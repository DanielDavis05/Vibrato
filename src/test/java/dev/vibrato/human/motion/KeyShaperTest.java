package dev.vibrato.human.motion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeyShaperTest {

    private static final double INF = Double.POSITIVE_INFINITY;

    /** No misses, no hesitation, no taps, no timing shift: keys pass through unchanged. */
    private static final HumanProfile QUIET = new HumanProfile(5, 0, 0, 0, 0, 6, 0.3, 0, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);

    /** Jump timing jitter large enough that the window clamp is what decides the press tick. */
    private static final HumanProfile JITTERY = new HumanProfile(5, 0, 10, 0, 0, 6, 0.3, 0, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);

    private static KeyShaper.Context ctx(boolean onGround, int ticksUntilJump, int early, int late) {
        return new KeyShaper.Context(onGround, 0.2, INF, ticksUntilJump, early, late);
    }

    @Test
    void sameSeedAndInputsGiveSameKeys() {
        HumanProfile profile = HumanProfile.sample(new java.util.SplittableRandom(4), Population.DEFAULT);
        assertEquals(run(new KeyShaper(9, profile)), run(new KeyShaper(9, profile)));
    }

    private static List<EnumSet<Key>> run(KeyShaper shaper) {
        List<EnumSet<Key>> out = new ArrayList<>();
        for (int t = 0; t < 200; t++) {
            EnumSet<Key> intended = t % 20 < 10 ? EnumSet.of(Key.FORWARD, Key.LEFT) : EnumSet.of(Key.FORWARD);
            int lead = t % 25 == 0 ? 4 : KeyShaper.NO_JUMP;
            out.add(shaper.shape(intended, ctx(true, lead, 2, 3)));
        }
        return out;
    }

    @Test
    void jumpPressStaysInsideToleranceWindow() {
        int early = 2;
        int late = 3;
        int planned = 5;
        boolean sawShift = false;
        int previousFire = -1;
        for (long seed = 0; seed < 60; seed++) {
            KeyShaper shaper = new KeyShaper(seed, JITTERY);
            int fired = -1;
            int jumps = 0;
            for (int t = 0; t < 30; t++) {
                int lead = t <= planned ? planned - t : KeyShaper.NO_JUMP;
                if (shaper.shape(EnumSet.noneOf(Key.class), ctx(true, lead, early, late)).contains(Key.JUMP)) {
                    jumps++;
                    fired = t;
                }
            }
            assertEquals(1, jumps, "seed " + seed);
            assertTrue(fired >= Math.max(0, planned - early) && fired <= planned + late, "seed " + seed + " fired at " + fired);
            if (previousFire >= 0 && fired != previousFire) {
                sawShift = true;
            }
            previousFire = fired;
        }
        assertTrue(sawShift, "jump timing never varied across seeds");
    }

    @Test
    void zeroToleranceNeverMovesTheJump() {
        for (long seed = 0; seed < 30; seed++) {
            KeyShaper shaper = new KeyShaper(seed, JITTERY);
            int fired = -1;
            for (int t = 0; t < 20; t++) {
                int lead = t <= 4 ? 4 - t : KeyShaper.NO_JUMP;
                if (shaper.shape(EnumSet.noneOf(Key.class), ctx(true, lead, 0, 0)).contains(Key.JUMP)) {
                    fired = t;
                }
            }
            assertEquals(4, fired, "seed " + seed);
        }
    }

    @Test
    void noJumpIsPressedWhileAirborne() {
        KeyShaper shaper = new KeyShaper(1, QUIET);
        for (int t = 0; t < 10; t++) {
            assertFalse(shaper.shape(EnumSet.of(Key.JUMP), ctx(false, 0, 2, 3)).contains(Key.JUMP));
        }
    }

    @Test
    void quietProfilePassesMovementThroughAndFiresUnplannedJumpNow() {
        KeyShaper shaper = new KeyShaper(1, QUIET);
        EnumSet<Key> keys = shaper.shape(EnumSet.of(Key.FORWARD, Key.LEFT, Key.JUMP), ctx(true, KeyShaper.NO_JUMP, 0, 0));
        assertEquals(EnumSet.of(Key.FORWARD, Key.LEFT, Key.JUMP), keys);
        assertEquals(EnumSet.of(Key.BACK), new KeyShaper(1, QUIET).shape(EnumSet.of(Key.BACK), ctx(true, KeyShaper.NO_JUMP, 0, 0)));
    }

    @Test
    void certainHesitationReleasesEveryKey() {
        HumanProfile always = new HumanProfile(5, 0, 0, 0, 0, 6, 0.3, 1, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);
        KeyShaper shaper = new KeyShaper(1, always);
        for (int t = 0; t < 2; t++) {
            assertTrue(shaper.shape(EnumSet.of(Key.FORWARD, Key.LEFT), ctx(true, KeyShaper.NO_JUMP, 0, 0)).isEmpty());
        }
    }

    @Test
    void hesitationNeverHappensWhenAJumpIsPlannedOrInTheAir() {
        HumanProfile always = new HumanProfile(5, 0, 0, 0, 0, 6, 0.3, 1, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);
        KeyShaper planned = new KeyShaper(1, always);
        assertTrue(planned.shape(EnumSet.of(Key.FORWARD), ctx(true, 0, 0, 0)).contains(Key.JUMP));
        assertEquals(EnumSet.of(Key.FORWARD), new KeyShaper(1, always).shape(EnumSet.of(Key.FORWARD), ctx(false, KeyShaper.NO_JUMP, 0, 0)));
        KeyShaper.Context nearGap = new KeyShaper.Context(true, 0.2, 1.0, KeyShaper.NO_JUMP, 0, 0);
        assertEquals(EnumSet.of(Key.FORWARD), new KeyShaper(1, always).shape(EnumSet.of(Key.FORWARD), nearGap));
    }

    @Test
    void closeGapNeverReleasesForwardWhenAJumpIsPlanned() {
        KeyShaper shaper = new KeyShaper(1, QUIET);
        KeyShaper.Context jumpGap = new KeyShaper.Context(true, 0.2, 1.5, 6, 0, 0);
        for (int t = 0; t < 50; t++) {
            assertTrue(shaper.shape(EnumSet.of(Key.FORWARD, Key.SPRINT), jumpGap).contains(Key.FORWARD));
        }
    }

    @Test
    void certainStrafeTapBreaksTheStrafeKey() {
        HumanProfile taps = new HumanProfile(5, 0, 0, 0, 1, 6, 0.3, 0, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);
        KeyShaper shaper = new KeyShaper(1, taps);
        EnumSet<Key> keys = shaper.shape(EnumSet.of(Key.FORWARD, Key.LEFT), ctx(true, KeyShaper.NO_JUMP, 0, 0));
        assertEquals(EnumSet.of(Key.FORWARD), keys);
    }

    /** Every jump is missed unless it is gap-critical. No hesitation, taps, or timing shift. */
    private static final HumanProfile ALWAYS_MISS = new HumanProfile(5, 0, 0, 1, 0, 6, 0.3, 0, 0.03, 0.1, 3, 0.92, 1.2, 1, 1);

    @Test
    void gapCriticalJumpIsNeverMissed() {
        for (double gap : new double[] {0, 1.5}) {
            KeyShaper shaper = new KeyShaper(1, ALWAYS_MISS);
            KeyShaper.Context ctx = new KeyShaper.Context(true, 0.2, gap, KeyShaper.NO_JUMP, 0, 0);
            assertTrue(shaper.shape(EnumSet.of(Key.FORWARD, Key.JUMP), ctx).contains(Key.JUMP), "gap " + gap);
        }
    }

    @Test
    void jumpWithDistantOrNoGapCanBeMissed() {
        KeyShaper shaper = new KeyShaper(1, ALWAYS_MISS);
        KeyShaper.Context ctx = new KeyShaper.Context(true, 0.2, INF, KeyShaper.NO_JUMP, 0, 0);
        assertFalse(shaper.shape(EnumSet.of(Key.FORWARD, Key.JUMP), ctx).contains(Key.JUMP));
    }

    @Test
    void emptyIntentPressesNothing() {
        KeyShaper shaper = new KeyShaper(5, QUIET);
        for (int t = 0; t < 20; t++) {
            assertTrue(shaper.shape(EnumSet.noneOf(Key.class), ctx(true, KeyShaper.NO_JUMP, 2, 3)).isEmpty());
        }
    }
}
