package dev.vibrato.human.motion;

import dev.vibrato.human.motion.Submovements.Step;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class SubmovementsTest {

    private static final double TOLERANCE = 1e-9;

    private static final HumanProfile TYPICAL = HumanProfile.typical(Population.DEFAULT);

    private static final HumanProfile OVERSHOOTY = new HumanProfile(5, 0, 0, 0, 0, 6, 0.3, 0, 0.03, 0.1, 3, 0.92, 2.5, 3, 1);

    private static double sum(List<Step> steps) {
        return steps.stream().mapToDouble(Step::amplitude).sum();
    }

    @Test
    void noErrorMeansNoMoves() {
        assertTrue(Submovements.plan(0, TYPICAL, new SplittableRandom(1)).isEmpty());
    }

    @Test
    void amplitudesSumToTheTotalErrorAndEndOnTarget() {
        double[] errors = {-170, -90, -12.5, -0.3, 0.3, 5, 45, 90, 179.9};
        for (long seed = 0; seed < 50; seed++) {
            for (double error : errors) {
                List<Step> steps = Submovements.plan(error, TYPICAL, new SplittableRandom(seed));
                assertEquals(error, sum(steps), TOLERANCE, "error " + error + " seed " + seed);
                assertTrue(steps.size() >= 1 && steps.size() <= 4, "steps " + steps.size());
                for (Step step : steps) {
                    assertTrue(step.ticks() >= 1, "ticks " + step.ticks());
                }
            }
        }
    }

    @Test
    void overshootPassesTheTargetAndCorrectsBack() {
        boolean sawOvershoot = false;
        for (long seed = 0; seed < 20; seed++) {
            List<Step> steps = Submovements.plan(90, OVERSHOOTY, new SplittableRandom(seed));
            assertEquals(90, sum(steps), TOLERANCE);
            if (steps.get(0).amplitude() > 90) {
                sawOvershoot = true;
                assertTrue(steps.size() >= 2);
                assertTrue(steps.get(1).amplitude() < 0, "correction must come back toward the target");
            }
        }
        assertTrue(sawOvershoot);
    }

    @Test
    void sameSeedGivesSameMoves() {
        assertEquals(
                Submovements.plan(73, TYPICAL, new SplittableRandom(6)),
                Submovements.plan(73, TYPICAL, new SplittableRandom(6)));
    }
}
