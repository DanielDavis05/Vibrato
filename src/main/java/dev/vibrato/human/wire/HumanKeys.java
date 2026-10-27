package dev.vibrato.human.wire;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.input.Input;
import baritone.utils.PlayerMovementInput;
import baritone.vibrato.OptionalHopPolicy;
import baritone.vibrato.VibratoSettings;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.chains.MLGBucketFallChain;
import dev.vibrato.chains.MobDefenseChain;
import dev.vibrato.chains.SingleTaskChain;
import dev.vibrato.human.motion.KeyShaper;
import dev.vibrato.human.Human;
import dev.vibrato.human.HumanComponents;
import dev.vibrato.human.record.MotionRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import dev.vibrato.nav.NavSafetyChain;
import dev.vibrato.tasksystem.TaskChain;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs Baritone's movement keys through {@link ShapingLayer} before the player acts on them.
 * <p>
 * How Baritone feeds the player, from reading {@code ../Baritone} (not modified): {@code InputOverrideHandler.onTick}
 * replaces {@code player.input} with {@code PlayerMovementInput} while Baritone is in control. That class extends the
 * plain {@code Input}, not {@code KeyboardInput}, and its {@code tick} copies the forced inputs into
 * {@code pressingForward/Back/Left/Right}, {@code jumping}, {@code sneaking} and the two movement impulses. A mixin on
 * {@code KeyboardInput#tick} would therefore never see Baritone's keys. The hook is
 * {@link dev.vibrato.mixins.KeyShapeMixin}: an injection in {@code ClientPlayerEntity#tickMovement} right after the
 * {@code Input.tick} call. Baritone writes its keys inside that call, so running after it holds regardless of mixin
 * priorities, and Baritone has no injector at that point. Sprint is not part of {@code Input}; Baritone sets it
 * through {@code SprintStateEvent}, so it is left alone and ends when W is released.
 * <p>
 * The chains' {@code isActive()} is always true, so the layer asks the task runner which chain currently wins. Idle
 * chains win at priority 0 when nothing else runs, so the mob and MLG chains count only with a task set, and the nav
 * safety chain only while the user chain is active (its idle priority 0 loses to the user chain's 50).
 * <p>
 * TODO: lateral path offset ({@code motion.PathOffset}) is the next step. It needs a Baritone aim-target hook, since
 * the keys alone cannot move the bot off the centreline without Baritone steering it back.
 */
public final class HumanKeys {

    private static final int PATH_NODES = SprintHop.HORIZON + 1;
    private static final int DEBUG_INTERVAL_TICKS = 20;
    /** About 10 seconds at 20 ticks per second. */
    private static final int SUMMARY_INTERVAL_TICKS = 200;

    private static volatile ShapingLayer layer;
    private static boolean debug;
    private static long tick;
    private static long lastDebugTick = Long.MIN_VALUE;
    private static long lastSummaryTick = Long.MIN_VALUE;
    private static boolean failed;
    private static final OptionalHopPolicy optionalHop = new OptionalHopPolicy();

    private HumanKeys() {}

    public static void install(ShapingLayer shapingLayer, boolean debugKeys) {
        optionalHop.clear();
        VibratoSettings.getInstance().setOptionalHop(null, false, 0);
        layer = shapingLayer;
        debug = debugKeys;
    }

    /**
     * Called after the player's input has ticked. Never throws into the game loop: a failure logs once and turns the
     * layer off for the session.
     */
    public static void afterInputTick(ClientPlayerEntity player) {
        ShapingLayer active = layer;
        Vibrato mod = Debug.jankModInstance;
        if (mod == null || player != mod.getPlayer()) {
            clearOptionalHop();
            return;
        }
        if (active == null || failed) {
            clearOptionalHop();
            KeyAdapter.Flags flags = intended(player, BaritoneAPI.getProvider().getBaritoneForPlayer(player));
            MotionRecorder.recordInputs(KeyAdapter.toKeys(flags), KeyAdapter.toKeys(flags));
            if (Human.look() != null) Human.look().setTravelYawOffset(0);
            return;
        }
        try {
            shape(active, mod, player);
        } catch (RuntimeException e) {
            failed = true;
            clearOptionalHop();
            KeyAdapter.Flags flags = intended(player, BaritoneAPI.getProvider().getBaritoneForPlayer(player));
            MotionRecorder.recordInputs(KeyAdapter.toKeys(flags), KeyAdapter.toKeys(flags));
            if (Human.look() != null) Human.look().setTravelYawOffset(0);
            Debug.logWarning("Human key layer disabled after error: " + e);
        }
    }

    private static void shape(ShapingLayer active, Vibrato mod, ClientPlayerEntity player) {
        tick++;
        net.minecraft.client.input.Input input = player.input;
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(player);
        IPathExecutor executor = baritone == null ? null : baritone.getPathingBehavior().getCurrent();
        boolean controlled = input instanceof PlayerMovementInput && executor != null;
        var client = MinecraftClient.getInstance();
        boolean interacting = client.currentScreen != null || client.options.attackKey.isPressed()
                || client.options.useKey.isPressed() || baritone != null
                && (baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_RIGHT));
        Bypass bypass = new Bypass(
                false,
                !controlled,
                player.isTouchingWater() || player.isSubmergedInWater() || player.isInLava(),
                player.isClimbing(),
                player.isFallFlying() || player.getAbilities().flying,
                player.hasVehicle(),
                input.sneaking,
                interacting,
                safetyChainInCharge(mod));

        KeyAdapter.Flags intended = intended(player, baritone);
        PathView pathView = view(player, executor);
        List<MovementKind> movements = movements(executor);
        boolean clear = !bypass.any() && player.getHungerManager().getFoodLevel() > 6
                && SprintHop.straightFlat(pathView.nodes(), movements) && clearCorridor(mod, pathView);
        boolean hopsEnabled = HumanComponents.enabled("humanHops");
        optionalHop.update(clear && hopsEnabled, player.isOnGround());
        VibratoSettings.getInstance().setOptionalHop(player, optionalHop.active(), optionalHop.originY());
        KeyAdapter.Flags shaped = active.apply(intended, pathView, bypass);
        boolean shapedJump = shaped.jump();
        shaped = active.addHop(shaped, pathView, movements, clear, hopsEnabled);
        if (shaped.jump() && !shapedJump && !intended.jump() && clear && hopsEnabled && player.isOnGround()) {
            optionalHop.begin(player.getBlockY());
            VibratoSettings.getInstance().setOptionalHop(player, true, optionalHop.originY());
        }
        MotionRecorder.recordInputs(KeyAdapter.toKeys(intended), KeyAdapter.toKeys(shaped));
        if (Human.look() != null) Human.look().setTravelYawOffset(active.visualOffsetDegrees(clear));
        if (debug && tick - lastSummaryTick >= SUMMARY_INTERVAL_TICKS) {
            lastSummaryTick = tick;
            Debug.logMessage(summary(active.stats()));
        }
        if (shaped.equals(intended)) {
            return;
        }
        input.pressingForward = shaped.forward();
        input.pressingBack = shaped.back();
        input.pressingLeft = shaped.left();
        input.pressingRight = shaped.right();
        input.jumping = shaped.jump();
        input.movementForward = KeyAdapter.axis(shaped.forward(), shaped.back());
        input.movementSideways = KeyAdapter.axis(shaped.left(), shaped.right());
        if (debug && tick - lastDebugTick >= DEBUG_INTERVAL_TICKS) {
            lastDebugTick = tick;
            Debug.logMessage("Keys intended " + intended + " shaped " + shaped);
        }
    }

    private static void clearOptionalHop() {
        optionalHop.clear();
        VibratoSettings.getInstance().setOptionalHop(null, false, 0);
    }

    private static KeyAdapter.Flags intended(ClientPlayerEntity player, IBaritone baritone) {
        var input = player.input;
        boolean sprint = player.isSprinting() || baritone != null
                && baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT);
        return new KeyAdapter.Flags(input.pressingForward, input.pressingBack, input.pressingLeft,
                input.pressingRight, input.jumping, sprint, input.sneaking);
    }

    private static List<MovementKind> movements(IPathExecutor executor) {
        List<MovementKind> result = new ArrayList<>();
        if (executor == null) return result;
        var moves = executor.getPath().movements();
        int at = executor.getPosition();
        for (int i = Math.max(0, at); i < moves.size() && i < at + SprintHop.HORIZON; i++)
            result.add(MovementKind.fromClassName(moves.get(i).getClass().getSimpleName()));
        return result;
    }

    /** Full support and three clear blocks in a 3x3 corridor at each node through the complete hop horizon. */
    private static boolean clearCorridor(Vibrato mod, PathView view) {
        var world = MinecraftClient.getInstance().world;
        if (world == null || view.nodes().size() < SprintHop.HORIZON + 1) return false;
        for (int i = 0; i <= SprintHop.HORIZON; i++) {
            var node = view.nodes().get(i);
            int x = (int) Math.floor(node.x()), y = (int) Math.floor(node.y()), z = (int) Math.floor(node.z());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = new BlockPos(x + dx, y - 1, z + dz);
                if (!mod.getChunkTracker().isChunkLoaded(floor)
                        || !world.getBlockState(floor).isSolidBlock(world, floor)) return false;
                for (int h = 1; h <= 3; h++) if (!world.getBlockState(floor.up(h)).isAir()) return false;
            }
        }
        return true;
    }

    /** One line of shaper counters, so a run can attribute stutter to a component. */
    static String summary(KeyShaper.Stats s) {
        return "Keys stats ticks=" + s.ticks()
                + " changed=" + s.changedTicks()
                + " hesitation=" + s.hesitationTicks()
                + " gapRelease=" + s.gapReleaseTicks()
                + " strafeTap=" + s.strafeTapTicks()
                + " jumpShifted=" + s.jumpShifted()
                + " jumpMissed=" + s.jumpMissed();
    }

    private static boolean safetyChainInCharge(Vibrato mod) {
        TaskChain current = mod.getTaskRunner().getCurrentTaskChain();
        if (current instanceof NavSafetyChain) {
            return mod.getUserTaskChain().isActive();
        }
        if (current instanceof MobDefenseChain || current instanceof MLGBucketFallChain) {
            return ((SingleTaskChain) current).getCurrentTask() != null;
        }
        return false;
    }

    private static PathView view(ClientPlayerEntity player, IPathExecutor executor) {
        MovementKind kind = MovementKind.OTHER;
        List<PathView.Node> nodes = new ArrayList<>();
        if (executor != null) {
            IPath path = executor.getPath();
            List<BetterBlockPos> positions = path.positions();
            int at = executor.getPosition();
            if (at >= 0 && at < path.movements().size()) {
                IMovement movement = path.movements().get(at);
                kind = MovementKind.fromClassName(movement.getClass().getSimpleName());
            }
            for (int i = Math.max(at, 0); i < positions.size() && i < at + PATH_NODES; i++) {
                BetterBlockPos pos = positions.get(i);
                nodes.add(new PathView.Node(pos.x + 0.5, pos.y, pos.z + 0.5));
            }
        }
        Vec3d velocity = player.getVelocity();
        return new PathView(kind, nodes, player.getX(), player.getY(), player.getZ(),
                velocity.x, velocity.y, velocity.z, player.isOnGround());
    }
}
