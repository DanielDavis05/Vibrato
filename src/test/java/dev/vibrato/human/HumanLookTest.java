package dev.vibrato.human;

import baritone.api.utils.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HumanLookTest {

    private static final HumanLookConfig NO_OVERSHOOT = new HumanLookConfig(40, 25, 0.35, 0.4, 0.0, 3.0, 0.0);
    private static final HumanLookConfig ALWAYS_OVERSHOOT = new HumanLookConfig(40, 25, 0.35, 0.4, 1.0, 3.0, 0.0);
    private static final HumanLookConfig WINDY = new HumanLookConfig(40, 25, 0.35, 0.4, 0.25, 3.0, 0.8);

    /** Drives the smoother like Baritone does: smooth from the last applied rotation, then advance one tick. */
    private static List<Float> yawTrack(HumanLook look, float start, float target, int ticks) {
        List<Float> track = new ArrayList<>();
        float current = start;
        for (int t = 0; t < ticks; t++) {
            current = look.smooth(new Rotation(current, 0), new Rotation(target, 0)).getYaw();
            look.onTick();
            track.add(current);
        }
        return track;
    }

    private static int ticksToArrive(List<Float> track, float target, float tolerance) {
        for (int i = 0; i < track.size(); i++) {
            if (Math.abs(target - track.get(i)) <= tolerance) {
                return i + 1;
            }
        }
        return -1;
    }

    @Test
    void neverExceedsMaxSpeedPerTick() {
        for (long seed = 0; seed < 20; seed++) {
            HumanLook look = new HumanLook(seed, WINDY);
            float previous = 0;
            for (float step : yawTrack(look, 0, 170, 60)) {
                assertTrue(Math.abs(step - previous) <= 40 + 1e-4, "step " + (step - previous));
                previous = step;
            }
        }
    }

    @Test
    void convergesWithin12TicksFor90Degrees() {
        List<Float> track = yawTrack(new HumanLook(1, NO_OVERSHOOT), 0, 90, 30);
        int ticks = ticksToArrive(track, 90, 0.4f);
        assertTrue(ticks > 0 && ticks <= 12, "ticks " + ticks);
    }

    @Test
    void convergesWithin25TicksFor180Degrees() {
        List<Float> track = yawTrack(new HumanLook(1, NO_OVERSHOOT), 0, 180, 40);
        int ticks = ticksToArrive(track, 180, 0.4f);
        assertTrue(ticks > 0 && ticks <= 25, "ticks " + ticks);
    }

    @Test
    void smallTurnFinishesWithin3Ticks() {
        List<Float> track = yawTrack(new HumanLook(1, NO_OVERSHOOT), 0, 5, 10);
        assertTrue(ticksToArrive(track, 5, 0.4f) <= 3);
    }

    @Test
    void bigTurnTakesAtLeast5Ticks() {
        List<Float> track = yawTrack(new HumanLook(1, NO_OVERSHOOT), 0, 180, 40);
        assertTrue(ticksToArrive(track, 180, 0.4f) >= 5);
    }

    @Test
    void idleLookStaysExactlyPut() {
        HumanLook look = new HumanLook(7, HumanLookConfig.DEFAULT);
        Rotation still = new Rotation(33.5f, -12.25f);
        for (int t = 0; t < 50; t++) {
            Rotation out = look.smooth(still, still);
            assertEquals(still.getYaw(), out.getYaw());
            assertEquals(still.getPitch(), out.getPitch());
            look.onTick();
        }
    }

    @Test
    void settledTurnHasNoJitter() {
        HumanLook look = new HumanLook(7, NO_OVERSHOOT);
        yawTrack(look, 0, 45, 30);
        Rotation settled = new Rotation(45, 0);
        assertEquals(45f, look.smooth(settled, new Rotation(45, 0)).getYaw());
    }

    @Test
    void sameSeedAndInputsGiveSameTrajectory() {
        assertEquals(yawTrack(new HumanLook(42, WINDY), 0, 150, 40), yawTrack(new HumanLook(42, WINDY), 0, 150, 40));
    }

    @Test
    void overshootIsBoundedAndSettles() {
        for (long seed = 0; seed < 10; seed++) {
            List<Float> track = yawTrack(new HumanLook(seed, ALWAYS_OVERSHOOT), 0, 90, 60);
            float peak = track.stream().max(Float::compare).orElseThrow();
            assertTrue(peak - 90 <= 3.0 + 1e-4, "overshoot " + (peak - 90));
            for (float yaw : track.subList(40, 60)) {
                assertTrue(Math.abs(90 - yaw) <= 0.4 + 1e-4, "not settled: " + yaw);
            }
        }
    }

    @Test
    void differentSeedsGiveDifferentValidTrajectories() {
        List<Float> first = yawTrack(new HumanLook(1, ALWAYS_OVERSHOOT), 0, 90, 30);
        List<Float> second = yawTrack(new HumanLook(2, ALWAYS_OVERSHOOT), 0, 90, 30);
        assertNotEquals(first, second);
        assertTrue(first.stream().max(Float::compare).orElseThrow() - 90 <= 3.0 + 1e-4);
        assertTrue(second.stream().max(Float::compare).orElseThrow() - 90 <= 3.0 + 1e-4);
    }

    @Test
    void pitchIsClampedToVerticalRange() {
        HumanLook look = new HumanLook(3, NO_OVERSHOOT);
        float pitch = 0;
        for (int t = 0; t < 40; t++) {
            pitch = look.smooth(new Rotation(0, pitch), new Rotation(0, 120)).getPitch();
            assertTrue(pitch <= 90 && pitch >= -90, "pitch " + pitch);
            look.onTick();
        }
        assertEquals(90f, pitch, 0.4f);

        pitch = 0;
        for (int t = 0; t < 40; t++) {
            pitch = look.smooth(new Rotation(0, pitch), new Rotation(0, -200)).getPitch();
            assertTrue(pitch >= -90, "pitch " + pitch);
            look.onTick();
        }
    }

    @Test
    void worksForNegativeAndLargeYaw() {
        List<Float> negative = yawTrack(new HumanLook(1, NO_OVERSHOOT), 0, -120, 30);
        assertTrue(ticksToArrive(negative, -120, 0.4f) > 0);

        List<Float> large = yawTrack(new HumanLook(1, NO_OVERSHOOT), 7200, 7290, 30);
        assertTrue(ticksToArrive(large, 7290, 0.4f) > 0);
    }

    @Test
    void smoothIsPureUntilTickAdvances() {
        HumanLook look = new HumanLook(5, NO_OVERSHOOT);
        Rotation first = look.smooth(new Rotation(0, 0), new Rotation(60, 0));
        Rotation again = look.smooth(new Rotation(0, 0), new Rotation(60, 0));
        assertEquals(first.getYaw(), again.getYaw());
    }

    @Test
    void reversingTargetsAcrossYawWrapNeverSnaps() {
        HumanLook look = new HumanLook(2, ALWAYS_OVERSHOOT);
        Rotation current = new Rotation(179, 0);
        for (int i = 0; i < 30; i++) {
            Rotation target = new Rotation(i < 8 ? -90 : 90, 0);
            Rotation next = look.smooth(current, target);
            assertTrue(Math.abs(next.getYaw() - current.getYaw()) <= 40.0001);
            look.onTick();
            current = next;
        }
        assertTrue(Math.abs(Rotation.normalizeYaw(90 - current.getYaw())) < 1);
    }

    @Test
    void idleTickResetsMomentumBeforeANewTurn() {
        HumanLook look = new HumanLook(1, NO_OVERSHOOT);
        Rotation current = look.smooth(new Rotation(0, 0), new Rotation(170, 0));
        look.onTick();
        look.onTick(); // no request
        Rotation afterIdle = look.smooth(current, new Rotation(-90, 0));
        assertTrue(afterIdle.getYaw() < current.getYaw());
    }

    @Test
    void forkCopiesPendingRequestAndMotionForDeterministicPrediction() {
        HumanLook live = new HumanLook(5, NO_OVERSHOOT);
        Rotation previous = new Rotation(0, 0);
        Rotation desired = new Rotation(150, 45);
        for (int i = 0; i < 3; i++) {
            previous = live.smooth(previous, desired);
            live.onTick();
        }
        // Fork between smooth and onTick: both must commit the same pending request.
        Rotation pending = live.smooth(previous, desired);
        HumanLook simulated = live.fork();
        live.onTick();
        simulated.onTick();
        Rotation actual = pending;
        Rotation predicted = pending;
        for (int i = 0; i < 8; i++) {
            actual = live.smooth(actual, desired);
            predicted = simulated.smooth(predicted, desired);
            assertEquals(actual.getYaw(), predicted.getYaw(), 1e-5);
            assertEquals(actual.getPitch(), predicted.getPitch(), 1e-5);
            live.onTick();
            simulated.onTick();
        }
    }

    @Test
    void speculativeForksDoNotChangeLiveRequestMotionOrRandomStream() {
        HumanLook live = new HumanLook(71, WINDY);
        HumanLook baseline = new HumanLook(71, WINDY);
        Rotation current = new Rotation(0, 0);
        Rotation expected = current;
        Rotation target = new Rotation(140, 35);
        for (int i = 0; i < 20; i++) {
            Rotation actualNext = live.smooth(current, target);
            Rotation expectedNext = baseline.smooth(expected, target);
            assertEquals(expectedNext.getYaw(), actualNext.getYaw(), 1e-5);
            assertEquals(expectedNext.getPitch(), actualNext.getPitch(), 1e-5);

            HumanLook simulated = live.fork();
            Rotation hypothetical = current;
            for (int j = 0; j < 5; j++) {
                hypothetical = simulated.smooth(hypothetical, new Rotation(-130, -50));
                simulated.onTick();
            }
            live.onTick();
            baseline.onTick();
            current = actualNext;
            expected = expectedNext;
        }
    }
}
