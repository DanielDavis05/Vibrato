package dev.vibrato.human;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HumanInputTest {

    @Test
    void reactionTicksStayInRange() {
        Random rng = new Random(1);
        for (int i = 0; i < 10_000; i++) {
            int ticks = HumanInput.reactionTicks(rng);
            assertTrue(ticks >= 3 && ticks <= 9, "reaction " + ticks);
        }
    }

    @Test
    void reactionTicksHaveMedianNearFive() {
        Random rng = new Random(2);
        int[] samples = new int[10_000];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = HumanInput.reactionTicks(rng);
        }
        Arrays.sort(samples);
        int median = samples[samples.length / 2];
        assertTrue(median >= 4 && median <= 6, "median " + median);
    }

    @Test
    void keyHoldJitterIsWithinOneTick() {
        Random rng = new Random(3);
        boolean sawLow = false;
        boolean sawHigh = false;
        for (int i = 0; i < 10_000; i++) {
            int held = HumanInput.keyHoldJitterTicks(rng, 5);
            assertTrue(held >= 4 && held <= 6, "held " + held);
            sawLow |= held == 4;
            sawHigh |= held == 6;
        }
        assertTrue(sawLow && sawHigh);
    }

    @Test
    void keyHoldJitterNeverGoesNegative() {
        Random rng = new Random(4);
        for (int i = 0; i < 1_000; i++) {
            assertTrue(HumanInput.keyHoldJitterTicks(rng, 0) >= 0);
        }
    }
}
