package dev.vibrato.nav;

/**
 * One tick of the bot's motion, as seen by {@link StuckDetector}. Plain data, no Minecraft types.
 */
public record MotionSample(long tick, double x, double y, double z, float yaw, float pitch,
                           boolean onGround, boolean inWater, boolean underWater,
                           boolean horizontalCollision, boolean wantsToMove, int airTicksLeft) {

    public static final int FULL_AIR = 300;
}
