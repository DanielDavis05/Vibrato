package dev.vibrato.human;

import baritone.api.utils.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pitch must reach and hold a look-down target while Baritone's aim calls drive the camera (nerd-pole pillaring). */
class PillarAimTest {

    private static final float HOLD_TOLERANCE = 3f;

    private static List<Float> pitchesHolding(float startPitch, float targetPitch, int ticks, int peeks) {
        AimSimulation sim = new AimSimulation(new HumanLook(9, HumanLookConfig.DEFAULT), 0, startPitch);
        List<Float> pitches = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            sim.tick(new Rotation(0, targetPitch), peeks);
            pitches.add(sim.pitch());
        }
        return pitches;
    }

    @Test
    void lookDownTargetIsReachedAndHeldFromAnyStartPitch() {
        for (float start : new float[] {0, 45, -30, 70}) {
            for (int peeks : new int[] {1, 2, 4}) {
                List<Float> pitches = pitchesHolding(start, 90, 80, peeks);
                int arrived = arrivalIndex(pitches, 90);
                assertTrue(arrived >= 0 && arrived <= 40, "start " + start + " peeks " + peeks + " never arrived: " + pitches);
                for (float pitch : pitches.subList(arrived, pitches.size())) {
                    assertTrue(Math.abs(90 - pitch) <= HOLD_TOLERANCE,
                            "start " + start + " peeks " + peeks + " drifted off target: " + pitch);
                }
            }
        }
    }

    /** The nudge dip after arriving is Baritone's, so the monotonic check stops at the first arrival. */
    @Test
    void pitchNeverReversesWhileClimbingToLookDown() {
        List<Float> pitches = pitchesHolding(-30, 90, 80, 3);
        int arrived = arrivalIndex(pitches, 90);
        float previous = -30;
        for (float pitch : pitches.subList(0, arrived + 1)) {
            assertTrue(pitch >= previous - 0.151f, "pitch went back from " + previous + " to " + pitch);
            previous = pitch;
        }
    }

    private static int arrivalIndex(List<Float> pitches, float target) {
        for (int i = 0; i < pitches.size(); i++) {
            if (Math.abs(target - pitches.get(i)) <= HOLD_TOLERANCE) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void alternatingTargetStaysWithinTheTargetsBand() {
        AimSimulation sim = new AimSimulation(new HumanLook(4, HumanLookConfig.DEFAULT), 0, 0);
        for (int t = 0; t < 60; t++) {
            float target = t % 2 == 0 ? 90 : 0;
            sim.tick(new Rotation(0, target), 2);
            assertTrue(sim.pitch() >= -5 && sim.pitch() <= 95, "pitch " + sim.pitch());
        }
    }

    @Test
    void targetJumpingUpAndBackDoesNotLeaveTheLookDownHold() {
        AimSimulation sim = new AimSimulation(new HumanLook(5, HumanLookConfig.DEFAULT), 0, 0);
        for (int t = 0; t < 30; t++) {
            sim.tick(new Rotation(0, 90), 2);
        }
        for (int t = 0; t < 10; t++) {
            sim.tick(new Rotation(0, -20), 2);
        }
        for (int t = 0; t < 30; t++) {
            sim.tick(new Rotation(0, 90), 2);
        }
        assertTrue(Math.abs(90 - sim.pitch()) <= HOLD_TOLERANCE, "pitch after round trip " + sim.pitch());
    }

    @Test
    void yawWrapTakesTheShortArc() {
        // From -170 to 170 the short way is 20 degrees downward through -180, not 340 degrees upward.
        AimSimulation sim = new AimSimulation(new HumanLook(6, HumanLookConfig.DEFAULT), -170, 0);
        sim.tick(new Rotation(170, 0), 2);
        assertTrue(sim.yaw() < -170, "first tick turned the long way: " + sim.yaw());
        for (int t = 0; t < 10; t++) {
            sim.tick(new Rotation(170, 0), 2);
        }
        assertTrue(Math.abs(sim.yaw() - -190) <= 1f, "settled at " + sim.yaw());
    }
}
