package dev.vibrato.human.motion;

import java.util.EnumSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Turns Baritone's intended key set into the keys a person would hold. Call {@link #shape} once per tick, in order.
 * <p>
 * A planned jump is pressed a few ticks early or late, decided once per jump. The shift is clamped to the tolerance
 * window the caller passes in, so the bot never jumps earlier or later than the path planner allowed. A missed jump is
 * retried after a short wait, but a jump is never missed when a gap or ledge is within 2 blocks,
 * since it may be the jump that clears the gap. Hesitations release every key for a few ticks, only when nothing time-critical is happening. Strafe taps break a held strafe key for a
 * log-normal length. Before a close gap, W is released for one tick if the bot is still well clear of the edge.
 * <p>
 * Deterministic: the same seed and the same sequence of inputs give the same keys.
 */
public final class KeyShaper {

    /** {@link Context#ticksUntilJump} value when no jump is planned. */
    public static final int NO_JUMP = -1;

    private static final int HESITATION_MIN_TICKS = 2;
    private static final int HESITATION_MAX_TICKS = 6;
    private static final int MISS_RETRY_TICKS = 3;
    private static final double GAP_RELEASE_BLOCKS = 2;
    private static final double GAP_RELEASE_CHANCE = 0.5;
    private static final int MAX_TAP_BREAK_TICKS = 12;
    private static final Key[] STRAFE_KEYS = {Key.LEFT, Key.RIGHT};

    /**
     * What the caller knows this tick, besides the intended keys.
     *
     * @param onGround            whether the bot is on the ground
     * @param speed               horizontal speed in blocks per tick
     * @param gapAheadBlocks      distance to the next gap or ledge along the path, infinite when there is none
     * @param ticksUntilJump      ticks until the planned jump, 0 when it is now, {@link #NO_JUMP} when none is planned
     * @param earlyToleranceTicks how many ticks a jump may be pressed before its planned tick
     * @param lateToleranceTicks  how many ticks a jump may be pressed after its planned tick
     */
    public record Context(
            boolean onGround,
            double speed,
            double gapAheadBlocks,
            int ticksUntilJump,
            int earlyToleranceTicks,
            int lateToleranceTicks
    ) {

        public Context {
            if (earlyToleranceTicks < 0 || lateToleranceTicks < 0) {
                throw new IllegalArgumentException("jump tolerance must be >= 0");
            }
        }
    }

    private final RandomGenerator rng;
    private final HumanProfile profile;
    private long tick;
    private long hesitateUntil;
    private boolean jumpScheduled;
    private long jumpAt;
    private long jumpRetryAt;
    private long lastPlanned = Long.MIN_VALUE;
    private Key tapKey;
    private long tapUntil;

    public KeyShaper(long seed, HumanProfile profile) {
        this.rng = new SplittableRandom(seed);
        this.profile = profile;
    }

    /**
     * The keys to hold this tick, given the keys Baritone intends.
     */
    public EnumSet<Key> shape(Set<Key> intended, Context ctx) {
        long now = tick++;
        EnumSet<Key> out = EnumSet.noneOf(Key.class);
        if (now < hesitateUntil) {
            return out;
        }
        if (!intended.isEmpty() && isCalm(intended, ctx) && rng.nextDouble() < profile.hesitationRate()) {
            hesitateUntil = now + HESITATION_MIN_TICKS + rng.nextInt(HESITATION_MAX_TICKS - HESITATION_MIN_TICKS + 1);
            jumpScheduled = false;
            lastPlanned = Long.MIN_VALUE;
            return out;
        }

        scheduleJump(intended, ctx, now);
        if (jumpScheduled && now >= jumpAt) {
            out.add(Key.JUMP);
            jumpScheduled = false;
        }

        EnumSet<Key> move = EnumSet.noneOf(Key.class);
        for (Key key : intended) {
            if (key != Key.JUMP) {
                move.add(key);
            }
        }
        if (move.contains(Key.FORWARD) && isBeforeGap(intended, ctx) && rng.nextDouble() < GAP_RELEASE_CHANCE) {
            move.remove(Key.FORWARD);
        }
        if (tapKey != null && now >= tapUntil) {
            tapKey = null;
        }
        if (tapKey == null) {
            for (Key strafe : STRAFE_KEYS) {
                if (move.contains(strafe) && rng.nextDouble() < profile.strafeTapRate()) {
                    tapKey = strafe;
                    tapUntil = now + tapBreakTicks();
                    break;
                }
            }
        }
        if (tapKey != null) {
            move.remove(tapKey);
        }
        out.addAll(move);
        return out;
    }

    /**
     * Decides, once per jump, when the press happens. The window clamp is the guarantee the caller relies on.
     */
    private void scheduleJump(Set<Key> intended, Context ctx, long now) {
        int lead = ctx.ticksUntilJump();
        if (lead == NO_JUMP && intended.contains(Key.JUMP)) {
            lead = 0;
        }
        if (lead == NO_JUMP) {
            return;
        }
        long planned = now + lead;
        if (planned == lastPlanned || jumpScheduled || !ctx.onGround() || now < jumpRetryAt) {
            return;
        }
        lastPlanned = planned;
        boolean gapCritical = ctx.gapAheadBlocks() <= GAP_RELEASE_BLOCKS;
        if (!gapCritical && rng.nextDouble() < profile.jumpMissChance()) {
            lastPlanned = Long.MIN_VALUE;
            jumpRetryAt = now + MISS_RETRY_TICKS;
            return;
        }
        double sample = profile.jumpTimingBiasTicks() + profile.jumpTimingSigmaTicks() * rng.nextGaussian();
        int shift = (int) Math.round(sample);
        shift = Math.max(-ctx.earlyToleranceTicks(), Math.min(ctx.lateToleranceTicks(), shift));
        jumpAt = now + Math.max(0, lead + shift);
        jumpScheduled = true;
    }

    /**
     * True when there is a gap close ahead, but still more than one tick of travel away, so a one-tick W release
     * cannot carry the bot over the edge. Never when a jump is planned, since releasing W would cancel the sprint.
     */
    private boolean isBeforeGap(Set<Key> intended, Context ctx) {
        return ctx.onGround()
                && !jumpScheduled
                && ctx.ticksUntilJump() == NO_JUMP
                && !intended.contains(Key.JUMP)
                && ctx.gapAheadBlocks() < GAP_RELEASE_BLOCKS
                && ctx.gapAheadBlocks() > 2 * ctx.speed();
    }

    /**
     * True when nothing time-critical is happening, so every key can be released safely: on the ground, no jump
     * planned or scheduled, and no gap close ahead.
     */
    private boolean isCalm(Set<Key> intended, Context ctx) {
        return ctx.onGround()
                && !jumpScheduled
                && ctx.ticksUntilJump() == NO_JUMP
                && !intended.contains(Key.JUMP)
                && ctx.gapAheadBlocks() > GAP_RELEASE_BLOCKS;
    }

    /**
     * Log-normal break length around the profile's key hold median, clamped to 1..{@value #MAX_TAP_BREAK_TICKS}.
     */
    private int tapBreakTicks() {
        double sample = profile.keyHoldMedianTicks() * Math.exp(profile.keyHoldSigma() * rng.nextGaussian());
        return (int) Math.max(1, Math.min(MAX_TAP_BREAK_TICKS, Math.round(sample)));
    }
}
