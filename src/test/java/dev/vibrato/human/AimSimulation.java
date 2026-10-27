package dev.vibrato.human;

import baritone.api.utils.Rotation;

/**
 * Replays Baritone's per-tick aim calls around a {@link HumanLook}: the LookBehavior tick (smoother onTick), then
 * several {@code peekRotation} calls that each nudge the pitch, smooth, quantize to mouse pixels and clamp, with the
 * last result written to the player. Mouse sensitivity is Minecraft's default 0.5, which is 0.15 degrees per pixel.
 */
final class AimSimulation {

    private static final double SENSITIVITY = 0.5;
    private static final float DEGREES_PER_PIXEL = 0.15f;

    private final HumanLook look;
    private float yaw;
    private float pitch;

    AimSimulation(HumanLook look, float yaw, float pitch) {
        this.look = look;
        // The camera snaps its own turns to the mouse grid, as the mouse does in game, so the simulation sets it too.
        look.setSensitivitySource(() -> SENSITIVITY);
        this.yaw = yaw;
        this.pitch = pitch;
    }

    float pitch() {
        return pitch;
    }

    float yaw() {
        return yaw;
    }

    /** One game tick aimed at {@code target}, with {@code peeks} peekRotation calls like Baritone's per-tick usage. */
    Rotation tick(Rotation target, int peeks) {
        look.onTick();
        Rotation actual = null;
        for (int i = 0; i < peeks; i++) {
            actual = peek(target);
        }
        yaw = actual.getYaw();
        pitch = actual.getPitch();
        return actual;
    }

    /** A task's direct look (LookHelper.lookAt on the human path): one smoothed step written straight to the player. */
    void taskLook(Rotation target) {
        Rotation smoothed = look.smooth(new Rotation(yaw, pitch), target);
        yaw = smoothed.getYaw();
        pitch = smoothed.getPitch();
    }

    private Rotation peek(Rotation target) {
        float desiredPitch = target.getPitch();
        if (desiredPitch == pitch) {
            desiredPitch = nudgeToLevel(desiredPitch);
        }
        Rotation smoothed = look.smooth(new Rotation(yaw, pitch), new Rotation(target.getYaw(), desiredPitch));
        return new Rotation(
                quantize(yaw, smoothed.getYaw()),
                quantize(pitch, smoothed.getPitch())
        ).clamp();
    }

    /** Baritone's AbstractAimProcessor.nudgeToLevel, copied so the simulation shows its effect. */
    private static float nudgeToLevel(float pitch) {
        if (pitch < -20) {
            return pitch + 1;
        } else if (pitch > 10) {
            return pitch - 1;
        }
        return pitch;
    }

    /** Baritone's calculateMouseMove: the angle moves in whole mouse pixels. */
    private static float quantize(float current, float target) {
        double pixels = Math.round((target - current) / DEGREES_PER_PIXEL);
        return current + (float) (pixels * DEGREES_PER_PIXEL);
    }
}
