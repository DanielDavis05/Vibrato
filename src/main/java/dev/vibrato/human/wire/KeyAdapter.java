package dev.vibrato.human.wire;

import dev.vibrato.human.motion.Key;

import java.util.EnumSet;
import java.util.Set;

/**
 * Converts between movement flags, as the player's input object holds them, and the {@link Key} sets the motion
 * models work on. Pure, so the Minecraft side only copies fields.
 */
public final class KeyAdapter {

    private KeyAdapter() {}

    /**
     * Movement flags of one tick.
     */
    public record Flags(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sprint,
                        boolean sneak) {
    }

    public static EnumSet<Key> toKeys(Flags flags) {
        EnumSet<Key> keys = EnumSet.noneOf(Key.class);
        add(keys, Key.FORWARD, flags.forward());
        add(keys, Key.BACK, flags.back());
        add(keys, Key.LEFT, flags.left());
        add(keys, Key.RIGHT, flags.right());
        add(keys, Key.JUMP, flags.jump());
        add(keys, Key.SPRINT, flags.sprint());
        add(keys, Key.SNEAK, flags.sneak());
        return keys;
    }

    public static Flags toFlags(Set<Key> keys) {
        return new Flags(
                keys.contains(Key.FORWARD), keys.contains(Key.BACK), keys.contains(Key.LEFT),
                keys.contains(Key.RIGHT), keys.contains(Key.JUMP), keys.contains(Key.SPRINT),
                keys.contains(Key.SNEAK));
    }

    /**
     * Movement impulse for an opposing key pair: +1, -1, or 0 when both or neither are held.
     */
    public static float axis(boolean positive, boolean negative) {
        return (positive ? 1f : 0f) - (negative ? 1f : 0f);
    }

    private static void add(EnumSet<Key> keys, Key key, boolean down) {
        if (down) {
            keys.add(key);
        }
    }
}
