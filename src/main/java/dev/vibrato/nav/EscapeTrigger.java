package dev.vibrato.nav;

/** When the bot should dig or walk out of the ground on its own. Times are game ticks. */
public final class EscapeTrigger {

    public static final long STALL_TICKS = 60 * 20;

    private EscapeTrigger() {}

    /**
     * Underground without a pickaxe always warrants an escape. With a pickaxe, a long stall underground does.
     */
    public static boolean shouldEscape(boolean underground, boolean hasPickaxe, long ticksWithoutProgress) {
        return underground && (!hasPickaxe || ticksWithoutProgress >= STALL_TICKS);
    }
}
