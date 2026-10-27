package dev.vibrato.human;

import baritone.api.utils.Rotation;
import dev.vibrato.human.motion.HumanProfile;
import dev.vibrato.human.motion.Population;
import dev.vibrato.human.motion.SensitivityGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HumanLookTest {

    private static final HumanLookConfig QUIET = new HumanLookConfig(40, 25, 0.35, 0.4, 0.0);
    private static final HumanLookConfig WINDY = new HumanLookConfig(40, 25, 0.35, 0.4, 0.8);

    /** One primary move straight at the target, never overshooting: the old NO_OVERSHOOT behaviour. */
    private static final HumanProfile STRAIGHT = profile(0, 0);
    /** Overshoot at the largest chance, with corrections always planned. */
    private static final HumanProfile OVERSHOOTY = profile(3, 1.2);

    /** A profile with the given overshoot scale and mean correction count, everything else at the population means. */
    private static HumanProfile profile(double overshootScale, double correctionCount) {
        String json = "{\"traits\":{\"overshootScale\":{\"mean\":" + overshootScale
                + "},\"correctionCountMean\":{\"mean\":" + correctionCount + "}}}";
        return HumanProfile.typical(Population.parse(json));
    }

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

    private static List<Float> pitchTrack(HumanLook look, float start, float target, int ticks) {
        List<Float> track = new ArrayList<>();
        float current = start;
        for (int t = 0; t < ticks; t++) {
            current = look.smooth(new Rotation(0, current), new Rotation(0, target)).getPitch();
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

    private static double maxStep(List<Float> track, float start) {
        double max = 0;
        float previous = start;
        for (float value : track) {
            max = Math.max(max, Math.abs(value - previous));
            previous = value;
        }
        return max;
    }

    @Test
    void neverExceedsMaxSpeedPerTick() {
        for (long seed = 0; seed < 20; seed++) {
            HumanLook look = new HumanLook(seed, WINDY, OVERSHOOTY);
            assertTrue(maxStep(yawTrack(look, 0, 170, 60), 0) <= 40 + 1e-4);
        }
    }

    @Test
    void pitchNeverExceedsMaxSpeedPerTick() {
        for (long seed = 0; seed < 20; seed++) {
            HumanLook look = new HumanLook(seed, WINDY, OVERSHOOTY);
            assertTrue(maxStep(pitchTrack(look, -60, 60, 60), -60) <= 25 + 1e-4);
        }
    }

    @Test
    void convergesWithin12TicksFor90Degrees() {
        List<Float> track = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 0, 90, 30);
        int ticks = ticksToArrive(track, 90, 0.4f);
        assertTrue(ticks > 0 && ticks <= 12, "ticks " + ticks);
    }

    @Test
    void convergesWithin25TicksFor180Degrees() {
        List<Float> track = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 0, 180, 40);
        int ticks = ticksToArrive(track, 180, 0.4f);
        assertTrue(ticks > 0 && ticks <= 25, "ticks " + ticks);
    }

    @Test
    void smallTurnFinishesWithin3Ticks() {
        List<Float> track = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 0, 5, 10);
        assertTrue(ticksToArrive(track, 5, 0.4f) <= 3);
    }

    @Test
    void bigTurnTakesAtLeast5Ticks() {
        List<Float> track = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 0, 180, 40);
        assertTrue(ticksToArrive(track, 180, 0.4f) >= 5);
    }

    @Test
    void turnSpeedRisesThenFalls() {
        List<Float> track = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 0, 90, 30);
        List<Double> speeds = new ArrayList<>();
        float previous = 0;
        for (float yaw : track) {
            speeds.add((double) Math.abs(yaw - previous));
            previous = yaw;
        }
        int peak = speeds.indexOf(speeds.stream().max(Double::compare).orElseThrow());
        assertTrue(peak > 0 && peak < speeds.size() - 1, "peak at " + peak);
        assertTrue(speeds.get(0) < speeds.get(peak));
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
        HumanLook look = new HumanLook(7, QUIET, STRAIGHT);
        yawTrack(look, 0, 45, 30);
        Rotation settled = new Rotation(45, 0);
        assertEquals(45f, look.smooth(settled, new Rotation(45, 0)).getYaw());
    }

    @Test
    void sameSeedAndInputsGiveSameTrajectory() {
        assertEquals(yawTrack(new HumanLook(42, WINDY), 0, 150, 40), yawTrack(new HumanLook(42, WINDY), 0, 150, 40));
    }

    @Test
    void legacyConstructorIsTheTypicalProfile() {
        HumanProfile typical = HumanProfile.typical(Population.DEFAULT);
        assertEquals(
                yawTrack(new HumanLook(42, WINDY, typical), 0, 150, 40),
                yawTrack(new HumanLook(42, WINDY), 0, 150, 40));
    }

    @Test
    void overshootIsBoundedAndSettles() {
        // The primary overshoots by 2 to 8 percent of the turn, so 90 degrees peaks at most about 7.2 degrees past.
        for (long seed = 0; seed < 10; seed++) {
            List<Float> track = yawTrack(new HumanLook(seed, QUIET, OVERSHOOTY), 0, 90, 60);
            float peak = track.stream().max(Float::compare).orElseThrow();
            assertTrue(peak - 90 <= 7.2 + 1e-4, "overshoot " + (peak - 90));
            for (float yaw : track.subList(40, 60)) {
                assertTrue(Math.abs(90 - yaw) <= 0.4 + 1e-4, "not settled: " + yaw);
            }
        }
    }

    @Test
    void twentyDegreeTurnsOvershootSometimes() {
        int overshoots = 0;
        int seeds = 200;
        for (long seed = 0; seed < seeds; seed++) {
            List<Float> track = yawTrack(new HumanLook(seed, QUIET), 0, 20, 40);
            float peak = track.stream().max(Float::compare).orElseThrow();
            assertTrue(peak - 20 <= 1.6 + 1e-4, "overshoot " + (peak - 20));
            if (peak > 20 + 0.05) {
                overshoots++;
            }
        }
        assertTrue(overshoots > 0, "no 20 degree turn overshot in " + seeds + " seeds");
        assertTrue(overshoots < seeds, "every 20 degree turn overshot");
    }

    @Test
    void differentSeedsGiveDifferentValidTrajectories() {
        List<Float> first = yawTrack(new HumanLook(1, QUIET, OVERSHOOTY), 0, 90, 30);
        List<Float> second = yawTrack(new HumanLook(2, QUIET, OVERSHOOTY), 0, 90, 30);
        assertNotEquals(first, second);
        assertTrue(first.stream().max(Float::compare).orElseThrow() - 90 <= 7.2 + 1e-4);
        assertTrue(second.stream().max(Float::compare).orElseThrow() - 90 <= 7.2 + 1e-4);
    }

    @Test
    void endsWithinArrivalToleranceAcrossSeedsAndTargets() {
        for (long seed = 0; seed < 30; seed++) {
            for (float target : new float[] {20, -75, 150}) {
                List<Float> track = yawTrack(new HumanLook(seed, HumanLookConfig.DEFAULT), 0, target, 120);
                assertTrue(Math.abs(target - track.get(track.size() - 1)) <= 0.4 + 1e-4,
                        "seed " + seed + " target " + target + " ended at " + track.get(track.size() - 1));
            }
        }
    }

    /**
     * Wind is the only thing that moves the camera off the plan, so its effect is the gap to the same turn with wind
     * off. Averaged over seeds, that gap stays under 0.3 degrees RMS while the camera tracks a stationary target.
     */
    @Test
    void windJitterStaysUnderThreeTenthsDegreeWhileTracking() {
        HumanLookConfig windy = HumanLookConfig.DEFAULT;
        HumanLookConfig quiet = new HumanLookConfig(
                windy.maxYawDegPerTick(), windy.maxPitchDegPerTick(), windy.gain(), windy.arrivalToleranceDeg(), 0);
        assertTrue(windy.windStrength() > 0, "wind must be on by default");
        double sumSquares = 0;
        double gapSquares = 0;
        int count = 0;
        for (long seed = 0; seed < 20; seed++) {
            List<Float> withWind = yawTrack(new HumanLook(seed, windy), 0, 30, 300);
            List<Float> without = yawTrack(new HumanLook(seed, quiet), 0, 30, 300);
            for (int t = 0; t < withWind.size(); t++) {
                gapSquares += Math.pow(withWind.get(t) - without.get(t), 2);
                count++;
            }
            for (float yaw : withWind.subList(100, withWind.size())) {
                sumSquares += (yaw - 30) * (yaw - 30);
            }
        }
        double gapRms = Math.sqrt(gapSquares / count);
        assertTrue(gapRms > 0.01, "wind changed almost nothing: " + gapRms);
        assertTrue(gapRms <= 0.3, "wind rms " + gapRms);
        assertTrue(Math.sqrt(sumSquares / (20 * 200)) <= 0.3, "tracking jitter " + Math.sqrt(sumSquares / (20 * 200)));
    }

    @Test
    void pitchIsClampedToVerticalRange() {
        HumanLook look = new HumanLook(3, QUIET, STRAIGHT);
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
        List<Float> negative = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 0, -120, 30);
        assertTrue(ticksToArrive(negative, -120, 0.4f) > 0);

        List<Float> large = yawTrack(new HumanLook(1, QUIET, STRAIGHT), 7200, 7290, 30);
        assertTrue(ticksToArrive(large, 7290, 0.4f) > 0);
    }

    @Test
    void smoothIsPureUntilTickAdvances() {
        HumanLook look = new HumanLook(5, QUIET);
        Rotation first = look.smooth(new Rotation(0, 0), new Rotation(60, 0));
        Rotation again = look.smooth(new Rotation(0, 0), new Rotation(60, 0));
        assertEquals(first.getYaw(), again.getYaw());
    }

    @Test
    void reversingTargetsAcrossYawWrapNeverSnaps() {
        HumanLook look = new HumanLook(2, QUIET, OVERSHOOTY);
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
        HumanLook look = new HumanLook(1, QUIET, STRAIGHT);
        Rotation current = look.smooth(new Rotation(0, 0), new Rotation(170, 0));
        look.onTick();
        look.onTick(); // no request
        Rotation afterIdle = look.smooth(current, new Rotation(-90, 0));
        assertTrue(afterIdle.getYaw() < current.getYaw());
    }

    @Test
    void forkCopiesPendingRequestAndMotionForDeterministicPrediction() {
        assertForkPredictsLive(null);
    }

    @Test
    void forkPredictsQuantizedMotion() {
        assertForkPredictsLive(() -> 0.5);
    }

    /** Fork between smooth and onTick: the fork must commit the same pending request as the live camera. */
    private static void assertForkPredictsLive(java.util.function.DoubleSupplier sensitivity) {
        HumanLook live = new HumanLook(5, QUIET, STRAIGHT);
        live.setSensitivitySource(sensitivity);
        Rotation previous = new Rotation(0, 0);
        Rotation desired = new Rotation(150, 45);
        for (int i = 0; i < 3; i++) {
            previous = live.smooth(previous, desired);
            live.onTick();
        }
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
        assertSpeculativeForksLeaveLiveUnchanged(null);
    }

    @Test
    void speculativeForksDoNotChangeQuantizedMotion() {
        assertSpeculativeForksLeaveLiveUnchanged(() -> 0.5);
    }

    private static void assertSpeculativeForksLeaveLiveUnchanged(java.util.function.DoubleSupplier sensitivity) {
        HumanLook live = new HumanLook(71, WINDY);
        HumanLook baseline = new HumanLook(71, WINDY);
        live.setSensitivitySource(sensitivity);
        baseline.setSensitivitySource(sensitivity);
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

    @Test
    void quantizedStepsAreWholeMouseSteps() {
        double step = SensitivityGrid.degreesPerPixel(0.5);
        HumanLook look = new HumanLook(9, HumanLookConfig.DEFAULT);
        look.setSensitivitySource(() -> 0.5);
        List<Float> track = yawTrack(look, 0, 170, 60);
        float previous = 0;
        for (float yaw : track) {
            double ratio = (yaw - previous) / step;
            assertEquals(Math.round(ratio), ratio, 1e-2, "delta " + (yaw - previous) + " is not a whole step");
            assertTrue(Math.abs(yaw - previous) <= 40 + 1e-4);
            previous = yaw;
        }
        assertTrue(Math.abs(170 - track.get(track.size() - 1)) <= 0.4 + 1e-4);
    }

    @Test
    void quantizedPitchStepsAreWholeMouseSteps() {
        double step = SensitivityGrid.degreesPerPixel(0.5);
        HumanLook look = new HumanLook(9, HumanLookConfig.DEFAULT);
        look.setSensitivitySource(() -> 0.5);
        float previous = -60;
        for (float pitch : pitchTrack(look, -60, 60, 60)) {
            double ratio = (pitch - previous) / step;
            assertEquals(Math.round(ratio), ratio, 1e-2, "delta " + (pitch - previous) + " is not a whole step");
            previous = pitch;
        }
    }

    @Test
    void quantizedTurnsStillArrive() {
        for (double sensitivity : new double[] {0.2, 0.5, 0.8}) {
            HumanLook look = new HumanLook(13, HumanLookConfig.DEFAULT);
            look.setSensitivitySource(() -> sensitivity);
            List<Float> track = yawTrack(look, 0, 75, 120);
            double step = SensitivityGrid.degreesPerPixel(sensitivity);
            assertTrue(Math.abs(75 - track.get(track.size() - 1)) <= Math.max(0.4, step) + 1e-4,
                    "sens " + sensitivity + " ended at " + track.get(track.size() - 1));
        }
    }

    @Test
    void nullSensitivitySourceLeavesMotionUnquantized() {
        HumanLook plain = new HumanLook(21, HumanLookConfig.DEFAULT);
        HumanLook cleared = new HumanLook(21, HumanLookConfig.DEFAULT);
        cleared.setSensitivitySource(() -> 0.5);
        cleared.setSensitivitySource(null);
        assertEquals(yawTrack(plain, 0, 50, 30), yawTrack(cleared, 0, 50, 30));
    }
}
