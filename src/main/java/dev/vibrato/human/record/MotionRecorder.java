package dev.vibrato.human.record;

import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.input.Input;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.human.motion.Key;
import dev.vibrato.eventbus.Subscription;
import dev.vibrato.eventbus.events.ClientTickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.EmptyChunk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Records player motion to {@code <game run directory>/traces/<name>-<source>.jsonl} for fitting the human motion model. Samples the
 * player once per client tick, emits key transitions by diffing the keys, and forwards raw mouse deltas from
 * {@link dev.vibrato.mixins.MouseRecorderMixin}. Everything runs on the client thread and never throws into the game
 * loop: an I/O or unexpected failure is logged and recording stops.
 */
public final class MotionRecorder {

    private static final int TRACE_VERSION = 1;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String[] KEY_NAMES = {
            "forward", "back", "left", "right", "jump", "sprint", "sneak", "attack", "use"
    };

    private static MotionRecorder current;
    // Mirrors current != null without the lock, so the mouse mixin can skip work on every cursor event.
    private static volatile boolean recording;

    private final Vibrato mod;
    private final TraceWriter writer;
    private final Path file;
    private final boolean bot;
    private final boolean[] keyDown = new boolean[KEY_NAMES.length];
    private final KeyBinding attackKey;
    private final KeyBinding useKey;
    private final Subscription<ClientTickEvent> subscription;
    private long tickCount;

    private MotionRecorder(Vibrato mod, TraceWriter writer, Path file, boolean bot, GameOptions options) {
        this.mod = mod;
        this.writer = writer;
        this.file = file;
        this.bot = bot;
        this.attackKey = options.attackKey;
        this.useKey = options.useKey;
        this.subscription = EventBus.subscribe(ClientTickEvent.class, event -> onTick());
    }

    /**
     * Starts recording, replacing any recording already in progress. {@code source} is {@code "human"} or
     * {@code "bot"}.
     */
    public static synchronized void start(Vibrato mod, String name, String source) throws IOException {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("trace name must be letters, digits, '_' or '-': " + name);
        }
        if (!source.equals("human") && !source.equals("bot")) {
            throw new IllegalArgumentException("trace source must be human or bot: " + source);
        }
        stop();
        Path file = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("traces").resolve(name + "-" + source + ".jsonl");
        TraceWriter writer = TraceWriter.open(file);
        try {
            GameOptions options = MinecraftClient.getInstance().options;
            writer.write(new TraceEvent.Header(
                    TRACE_VERSION, source, SharedConstants.getGameVersion().getName(),
                    options.getGuiScale().getValue(), options.getMouseSensitivity().getValue(),
                    options.getMaxFps().getValue(), name));
            current = new MotionRecorder(mod, writer, file, source.equals("bot"), options);
            recording = true;
        } catch (IOException | RuntimeException e) {
            writer.close();
            throw e;
        }
        Debug.logMessage("Recording " + source + " trace to " + file.toAbsolutePath());
    }

    /**
     * Stops recording and closes the trace. Does nothing when not recording.
     */
    public static synchronized void stop() {
        MotionRecorder recorder = current;
        if (recorder == null) {
            return;
        }
        current = null;
        recording = false;
        EventBus.unsubscribe(recorder.subscription);
        try {
            recorder.writer.close();
            Debug.logMessage("Saved " + recorder.tickCount + " ticks to " + recorder.file.toAbsolutePath());
        } catch (IOException e) {
            Debug.logError("Failed to close motion trace " + recorder.file + ": " + e.getMessage());
        }
    }

    public static boolean isRecording() {
        return recording;
    }

    /**
     * Raw cursor movement in pixels since the last cursor event. Called from the mouse mixin, on the client thread.
     */
    public static synchronized void onMouse(double dx, double dy) {
        if (!recording || current == null) {
            return;
        }
        current.emit(new TraceEvent.Mouse(nowMillis(), dx, dy));
    }

    /** Record the pre- and post-shaping movement flags once after input processing. The next client tick
     * samples the movement produced by these flags. The masks are deliberately independent of enum ordinals. */
    public static synchronized void recordInputs(Set<Key> intended, Set<Key> shaped) {
        if (!recording || current == null || !current.bot) {
            return;
        }
        current.emit(new TraceEvent.IntendedKeys(current.tickCount + 1, nowMillis(),
                keyMask(intended), keyMask(shaped)));
    }

    private static int keyMask(Set<Key> keys) {
        int mask = 0;
        Key[] order = {Key.FORWARD, Key.BACK, Key.LEFT, Key.RIGHT, Key.JUMP, Key.SPRINT, Key.SNEAK};
        for (int i = 0; i < order.length; i++) {
            if (keys.contains(order[i])) mask |= 1 << i;
        }
        return mask;
    }

    /**
     * Monotonic milliseconds for trace timestamps. Shared by every event source so they line up.
     */
    public static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private void onTick() {
        if (!Vibrato.inGame()) {
            return;
        }
        try {
            synchronized (MotionRecorder.class) {
                if (current != this) {
                    return;
                }
                long ms = nowMillis();
                boolean[] down = pressedKeys(MinecraftClient.getInstance().player);
                for (int i = 0; i < down.length; i++) {
                    if (down[i] != keyDown[i]) {
                        keyDown[i] = down[i];
                        emit(new TraceEvent.Key(ms, KEY_NAMES[i], down[i]));
                    }
                }
                emit(sampleTick(ms));
            }
        } catch (RuntimeException e) {
            Debug.logError("Motion recording stopped after an error: " + e);
            stop();
        }
    }

    /**
     * Key state in {@link #KEY_NAMES} order, read at the tick event. The tick event runs at the head of
     * {@code MinecraftClient.tick()}, before the player's {@code tickMovement}, which is where {@code Input.tick} runs
     * and {@link dev.vibrato.mixins.KeyShapeMixin} shapes the keys. So this reads the input that the previous tick's
     * movement used, one tick behind the keys that will move the player next. The position and velocity in the tick
     * sample are read at the same point, so keys and motion stay aligned.
     * <p>
     * Movement keys come from the effective {@code player.input} axes, which is what moved the player. That covers
     * Baritone's {@code PlayerMovementInput} and the shaped keys, which a KeyBinding read would miss. Sprint comes
     * from {@code isSprinting()}, since Baritone sets it outside {@code Input}. Attack and use are the KeyBinding
     * state, plus Baritone's forced clicks for bot traces.
     */
    private boolean[] pressedKeys(ClientPlayerEntity player) {
        InputKeys.Pressed axes = InputKeys.fromAxes(player.input.movementForward, player.input.movementSideways,
                player.input.jumping, player.input.sneaking);
        var baritone = bot ? mod.getClientBaritone().getInputOverrideHandler() : null;
        boolean attack = attackKey.isPressed() || (baritone != null && baritone.isInputForcedDown(Input.CLICK_LEFT));
        boolean use = useKey.isPressed() || (baritone != null && baritone.isInputForcedDown(Input.CLICK_RIGHT));
        return new boolean[] {
                axes.forward(), axes.back(), axes.left(), axes.right(), axes.jump(),
                player.isSprinting(), axes.sneak(), attack, use
        };
    }

    private TraceEvent.Tick sampleTick(long ms) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Vec3d velocity = player.getVelocity();
        Double nextX = null;
        Double nextY = null;
        Double nextZ = null;
        var executor = mod.getClientBaritone().getPathingBehavior().getCurrent();
        if (executor != null) {
            List<BetterBlockPos> positions = executor.getPath().positions();
            int next = executor.getPosition() + 1;
            if (next >= 0 && next < positions.size()) {
                BetterBlockPos node = positions.get(next);
                nextX = node.getX() + 0.5;
                nextY = (double) node.getY(); // block centres in x and z, feet level in y
                nextZ = node.getZ() + 0.5;
            }
        }
        Double edge = edgeAhead(player);
        return new TraceEvent.Tick(
                ++tickCount, ms,
                player.getX(), player.getY(), player.getZ(),
                velocity.x, velocity.y, velocity.z,
                player.getYaw(), player.getPitch(),
                player.isOnGround(), player.isSprinting(),
                nextX, nextY, nextZ, edge);
    }

    /**
     * Distance to the first gap or drop ahead of the player, or null when the player is not on open ground (airborne,
     * in water or lava, riding, climbing), when there is none, or when no world is loaded. Measured against the feet
     * block of the last grounded tick, so a one-block step does not count as an edge.
     */
    private static Double edgeAhead(ClientPlayerEntity player) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !EdgeProbe.canProbe(player.isOnGround(), player.isTouchingWater(), player.isInLava(),
                player.hasVehicle(), player.isClimbing())) {
            return null;
        }
        // Grounded and eligible, so the feet block is the standing level; airborne ticks never reach this point.
        int standingFeetY = (int) Math.floor(player.getY());
        Vec3d velocity = player.getVelocity();
        double[] heading = EdgeProbe.heading(velocity.x, velocity.z, player.getYaw());
        return EdgeProbe.distanceToEdge(player.getX(), standingFeetY, player.getZ(), heading[0], heading[1],
                (x, z) -> highestSolidGround(world, x, z, standingFeetY));
    }

    /**
     * Highest solid block in the column from one block above the standing feet block down to two below it. Returns
     * {@link EdgeProbe#UNKNOWN} for an unloaded chunk and {@link EdgeProbe#NONE} when no solid block is in the window.
     */
    private static int highestSolidGround(ClientWorld world, int x, int z, int feetBlock) {
        if (world.getChunk(x >> 4, z >> 4) instanceof EmptyChunk) {
            return EdgeProbe.UNKNOWN;
        }
        BlockPos.Mutable pos = new BlockPos.Mutable(x, feetBlock, z);
        for (int y = feetBlock + 1; y >= feetBlock - 2; y--) {
            pos.setY(y);
            if (world.getBlockState(pos).isSolidBlock(world, pos)) {
                return y;
            }
        }
        return EdgeProbe.NONE;
    }

    private void emit(TraceEvent event) {
        if (current != this) {
            return;
        }
        try {
            writer.write(event);
        } catch (IOException e) {
            Debug.logError("Motion recording stopped, write failed: " + e.getMessage());
            stop();
        }
    }

}
