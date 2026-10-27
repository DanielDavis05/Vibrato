package dev.vibrato.nav;

/** When the bot should dig or walk out of the ground on its own. Times are game ticks. */
public final class EscapeTrigger {

    public static final long STALL_TICKS = 60 * 20;

    private EscapeTrigger() {}

    /**
     * An underground escape is appropriate only after a long stall, regardless of equipment.
     */
    public static boolean shouldEscape(boolean underground, boolean hasPickaxe, long ticksWithoutProgress) {
        return underground && ticksWithoutProgress >= STALL_TICKS;
    }
}
