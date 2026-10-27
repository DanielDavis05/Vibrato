package dev.vibrato.nav;

import baritone.api.utils.input.Input;
import dev.vibrato.Vibrato;
import dev.vibrato.eventbus.EventBus;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.eventbus.events.BlockBrokenEvent;
import dev.vibrato.eventbus.events.ClientTickEvent;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.util.baritone.GoalFollowEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.registry.Registries;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Opt-in console trace for diagnosing navigation and block-breaking decisions. */
final class NavigationTrace {
    private final Vibrato _mod;
    private int _tick;

    private NavigationTrace(Vibrato mod) {
        _mod = mod;
        EventBus.subscribe(ClientTickEvent.class, event -> onTick());
        EventBus.subscribe(BlockBrokenEvent.class, this::onBlockBroken);
    }

    static void install(Vibrato mod) {
        if (Boolean.getBoolean("vibrato.debugNavigation")) new NavigationTrace(mod);
    }

    private void onTick() {
        if (!Vibrato.inGame() || ++_tick % 5 != 0) return;
        ClientPlayerEntity player = _mod.getPlayer();
        HitResult hit = MinecraftClient.getInstance().crosshairTarget;
        Object crosshair = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos() : "none";
        var controller = _mod.getControllerExtras();
        var baritone = _mod.getClientBaritone();
        var goal = baritone.getPathingBehavior().getGoal();
        String goalDescription = goal == null ? "none" : goal instanceof GoalFollowEntity
                ? goal.toString() : goal.getClass().getSimpleName();
        String inputs = Arrays.stream(Input.values())
                .filter(input -> baritone.getInputOverrideHandler().isInputForcedDown(input))
                .map(Enum::name).collect(Collectors.joining(",", "[", "]"));
        var pathing = baritone.getPathingBehavior();
        var executor = pathing.getCurrent();
        String destination = executor == null ? "none" : executor.getPath().getDest().toString();
        String movement = "none";
        if (executor != null && executor.getPosition() >= 0 && executor.getPosition() < executor.getPath().movements().size()) {
            movement = executor.getPath().movements().get(executor.getPosition()).getClass().getSimpleName();
        }
        var held = player.getMainHandStack();
        var task = _mod.getUserTaskChain().getCurrentTask();
        String step = task instanceof DirectorTask director ? director.currentStepId() : "none";
        System.out.printf(Locale.ROOT,
                "[NavTrace] tick=%d pos=(%.3f,%.3f,%.3f) yaw=%.2f pitch=%.2f crosshair=%s breaking=%s progress=%.3f forced=%s goal=%s chain=%s step=%s water=%s submerged=%s air=%d pathing=%s calculating=%s dest=%s movement=%s hp=%.1f hunger=%d xp=%d held=%s durability=%d sprinting=%s grounded=%s velocity=(%.3f,%.3f,%.3f)%n",
                _tick, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(),
                crosshair, controller.getBreakingBlockPos(), controller.getBreakingBlockProgress(), inputs,
                goalDescription, chainDescription(), step, player.isTouchingWater(), player.isSubmergedInWater(),
                player.getAir(), pathing.isPathing(), pathing.getInProgress().isPresent(),
                destination, movement, player.getHealth(), player.getHungerManager().getFoodLevel(), player.experienceLevel,
                Registries.ITEM.getId(held.getItem()).getPath(), held.isDamageable() ? held.getMaxDamage() - held.getDamage() : 0,
                player.isSprinting(), player.isOnGround(), player.getVelocity().x, player.getVelocity().y, player.getVelocity().z);
    }

    private void onBlockBroken(BlockBrokenEvent event) {
        if (!Vibrato.inGame() || event.player != _mod.getPlayer()) return;
        ClientPlayerEntity player = _mod.getPlayer();
        System.out.printf(Locale.ROOT, "[NavTrace] broken=%s state=%s pos=(%.3f,%.3f,%.3f) chain=%s%n",
                event.blockPos, event.blockState, player.getX(), player.getY(), player.getZ(), chainDescription());
    }

    private String chainDescription() {
        TaskChain chain = _mod.getTaskRunner().getCurrentTaskChain();
        if (chain == null) return "none";
        return chain.getClass().getSimpleName() + chain.getTasks().stream()
                .map(task -> task.getClass().getSimpleName()).collect(Collectors.joining(",", "[", "]"));
    }
}
