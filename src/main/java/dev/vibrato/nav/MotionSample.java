package dev.vibrato.nav;

/**
 * One tick of the bot's motion, as seen by {@link StuckDetector}. Plain data, no Minecraft types.
 * <p>
 * {@code productive} is true while the bot is busy with something that legitimately keeps it in place: breaking a
 * block, bridging, or waiting on a path that is still being calculated.
 */
public record MotionSample(long tick, double x, double y, double z, float yaw, float pitch,
                           boolean onGround, boolean inWater, boolean underWater,
                           boolean horizontalCollision, boolean wantsToMove, int airTicksLeft,
                           boolean productive) {

    public static final int FULL_AIR = 300;

    /** A sample of a bot that is not busy with anything that keeps it in place. */
    public MotionSample(long tick, double x, double y, double z, float yaw, float pitch,
                        boolean onGround, boolean inWater, boolean underWater,
                        boolean horizontalCollision, boolean wantsToMove, int airTicksLeft) {
        this(tick, x, y, z, yaw, pitch, onGround, inWater, underWater, horizontalCollision, wantsToMove, airTicksLeft, false);
    }
}
