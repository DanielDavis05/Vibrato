package dev.vibrato.human.wire;

import dev.vibrato.human.motion.HumanProfile;
import dev.vibrato.human.motion.Key;
import dev.vibrato.human.motion.KeyShaper;

import java.util.EnumSet;

/**
 * One bot's key layer: Baritone's intended flags in, the flags to press out. Call {@link #apply} once per tick.
 * <p>
 * When shaping is bypassed the flags pass through unchanged and the shaper is dropped, so a jump scheduled before the
 * bypass can never fire after it. The next shaped tick starts a fresh shaper with a seed derived from the base seed
 * and the number of restarts, which keeps the whole run deterministic. {@link #stats} keeps counting across restarts.
 */
public final class ShapingLayer {

    private final long seed;
    private final HumanProfile profile;
    private KeyShaper shaper;
    private long generation;
    private KeyShaper.Stats retired = KeyShaper.Stats.ZERO;

    public ShapingLayer(long seed, HumanProfile profile) {
        this.seed = seed;
        this.profile = profile;
    }

    public KeyAdapter.Flags apply(KeyAdapter.Flags intended, PathView view, Bypass bypass) {
        if (bypass.any()) {
            retireShaper();
            return intended;
        }
        if (shaper == null) {
            shaper = new KeyShaper(seed + 0x9E3779B97F4A7C15L * generation++, profile);
        }
        EnumSet<Key> shaped = shaper.shape(KeyAdapter.toKeys(intended), ContextBuilder.build(view));
        // Sneak is never shaped. Sprint can only be dropped, never added.
        shaped.remove(Key.SNEAK);
        if (intended.sneak()) {
            shaped.add(Key.SNEAK);
        }
        if (!intended.sprint()) {
            shaped.remove(Key.SPRINT);
        }
        return KeyAdapter.toFlags(shaped);
    }

    /** Counters for every shaper this layer has run, including the current one. */
    public KeyShaper.Stats stats() {
        return shaper == null ? retired : retired.plus(shaper.stats());
    }

    private void retireShaper() {
        if (shaper != null) {
            retired = retired.plus(shaper.stats());
            shaper = null;
        }
    }
}
