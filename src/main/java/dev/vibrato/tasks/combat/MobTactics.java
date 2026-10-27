package dev.vibrato.tasks.combat;

/**
 * Pure decision table: given a mob family and a snapshot of the fight, which engagement to run.
 * Holds no game state, so every rule here is unit-tested.
 */
public final class MobTactics {

    /** At or below this many health points we disengage, unless the mob is nearly dead. */
    static final float RETREAT_HEALTH = 6;
    /** A mob at or below this fraction of its max health is worth finishing even when we are hurt. */
    static final double FINISH_OFF_FRACTION = 0.25;
    /** Closer than this a bow is useless, so we melee. */
    static final double BOW_MIN_RANGE = 4;
    /** Further than this a skeleton's arrows and our bow are both inaccurate, so we close in. */
    static final double BOW_MAX_RANGE = 20;

    private MobTactics() {}

    public enum Engagement {
        APPROACH_AND_MELEE,
        HIT_AND_BACK_OFF,
        KEEP_DISTANCE_AND_SHOOT,
        SHIELD_UP,
        RETREAT,
        AVOID_EYE_CONTACT,
        CLOSE_DISTANCE_ZIGZAG,
        SIDE_STEP
    }

    /**
     * @param distance          blocks between us and the mob
     * @param ownHealth         our health points
     * @param hasShield         a shield is in our off hand
     * @param hasBow            we can shoot (bow and arrows)
     * @param mobHealthFraction the mob's health over its max, in [0, 1]
     * @param mobCharging       a creeper is fusing, or a hoglin is about to charge
     * @param onGround          we are standing on the ground
     * @param inWater           we are in water
     */
    public record Snapshot(double distance, float ownHealth, boolean hasShield, boolean hasBow,
                           double mobHealthFraction, boolean mobCharging, boolean onGround, boolean inWater) {}

    public static Engagement decide(MobKind kind, Snapshot s) {
        // A hissing creeper is never worth standing beside, whatever our health.
        if (kind == MobKind.CREEPER && s.mobCharging()) {
            return Engagement.SIDE_STEP;
        }
        if (s.ownHealth() <= RETREAT_HEALTH && s.mobHealthFraction() > FINISH_OFF_FRACTION) {
            return Engagement.RETREAT;
        }
        return switch (kind) {
            case SKELETON -> skeleton(s);
            case CREEPER -> s.hasBow() && s.distance() >= BOW_MIN_RANGE
                    ? Engagement.KEEP_DISTANCE_AND_SHOOT : Engagement.APPROACH_AND_MELEE;
            case ENDERMAN -> Engagement.AVOID_EYE_CONTACT;
            case BLAZE -> s.hasBow() && s.distance() >= BOW_MIN_RANGE
                    ? Engagement.KEEP_DISTANCE_AND_SHOOT : Engagement.APPROACH_AND_MELEE;
            case HOGLIN -> hoglin(s);
            case PHANTOM -> Engagement.HIT_AND_BACK_OFF;
            default -> meleeOrSwoop(s);
        };
    }

    private static Engagement skeleton(Snapshot s) {
        if (s.hasBow() && s.distance() >= BOW_MIN_RANGE && s.distance() <= BOW_MAX_RANGE) {
            return Engagement.KEEP_DISTANCE_AND_SHOOT;
        }
        if (s.distance() < BOW_MIN_RANGE) {
            return Engagement.APPROACH_AND_MELEE;
        }
        return Engagement.CLOSE_DISTANCE_ZIGZAG;
    }

    private static Engagement hoglin(Snapshot s) {
        if (s.mobCharging() && s.hasShield()) {
            return Engagement.SHIELD_UP;
        }
        return Engagement.HIT_AND_BACK_OFF;
    }

    /** Ground mobs we just walk up to. Mid-air we swing on the fall for the crit, then step back. */
    private static Engagement meleeOrSwoop(Snapshot s) {
        if (!s.onGround() && !s.inWater()) {
            return Engagement.HIT_AND_BACK_OFF;
        }
        return Engagement.APPROACH_AND_MELEE;
    }
}
