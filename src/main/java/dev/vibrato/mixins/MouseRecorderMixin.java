package dev.vibrato.mixins;

import dev.vibrato.human.record.MotionRecorder;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports raw cursor movement to {@link MotionRecorder} while a trace is being recorded. Runs at the head of
 * {@code onCursorPos}, so the shadowed {@code x} and {@code y} still hold the previous position. The delta uses the
 * same rules the game uses to accumulate cursor deltas: the window must be focused and the resolution must not have
 * just changed.
 */
@Mixin(Mouse.class)
public abstract class MouseRecorderMixin {

    @Shadow private double x;
    @Shadow private double y;
    @Shadow private boolean hasResolutionChanged;

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void recordCursorDelta(long window, double newX, double newY, CallbackInfo ci) {
        if (!MotionRecorder.isRecording()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || hasResolutionChanged || !client.isWindowFocused()) {
            return;
        }
        double dx = newX - x;
        double dy = newY - y;
        if (dx != 0 || dy != 0) {
            MotionRecorder.onMouse(dx, dy);
        }
    }
}
