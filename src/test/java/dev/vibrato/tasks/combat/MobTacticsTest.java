package dev.vibrato.tasks.combat;

import dev.vibrato.tasks.combat.MobTactics.Engagement;
import dev.vibrato.tasks.combat.MobTactics.Snapshot;
import org.junit.jupiter.api.Test;

import static dev.vibrato.tasks.combat.MobTactics.Engagement.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MobTacticsTest {

    private static Snapshot snap(double distance) {
        return new Snapshot(distance, 20, false, false, 1.0, false, true, false);
    }

    private static Engagement decide(MobKind kind, Snapshot s) {
        return MobTactics.decide(kind, s);
    }

    @Test
    void zombieSlimeAndGenericWalkUp() {
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.ZOMBIE, snap(8)));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.GENERIC, snap(2)));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.SLIME, snap(3)));
    }

    @Test
    void mobsAttackingMidAirSwingAndBackOffButSwimmersJustMelee() {
        Snapshot falling = new Snapshot(3, 20, false, false, 1.0, false, false, false);
        assertEquals(HIT_AND_BACK_OFF, decide(MobKind.ZOMBIE, falling));
        Snapshot swimming = new Snapshot(3, 20, false, false, 1.0, false, false, true);
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.DROWNED, swimming));
    }

    @Test
    void spiderWitchPiglinAndDrownedMelee() {
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.SPIDER, snap(5)));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.WITCH, snap(12)));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.PIGLIN, snap(4)));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.DROWNED, snap(4)));
    }

    @Test
    void skeletonZigzagsInWhenWeHaveNoBowOrAreFar() {
        assertEquals(CLOSE_DISTANCE_ZIGZAG, decide(MobKind.SKELETON, snap(10)));
        assertEquals(CLOSE_DISTANCE_ZIGZAG, decide(MobKind.SKELETON, snap(30)));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.SKELETON, snap(2)));
    }

    @Test
    void skeletonWithBowKeepsDistanceInRange() {
        Snapshot armed = new Snapshot(10, 20, false, true, 1.0, false, true, false);
        assertEquals(KEEP_DISTANCE_AND_SHOOT, decide(MobKind.SKELETON, armed));
        Snapshot armedFar = new Snapshot(25, 20, false, true, 1.0, false, true, false);
        assertEquals(CLOSE_DISTANCE_ZIGZAG, decide(MobKind.SKELETON, armedFar));
        Snapshot armedClose = new Snapshot(2, 20, false, true, 1.0, false, true, false);
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.SKELETON, armedClose));
    }

    @Test
    void creeperSideStepsWhenFusingEvenWhenHurt() {
        Snapshot fusing = new Snapshot(1.5, 20, false, false, 1.0, true, true, false);
        assertEquals(SIDE_STEP, decide(MobKind.CREEPER, fusing));
        Snapshot fusingLow = new Snapshot(1.5, 4, false, false, 1.0, true, true, false);
        assertEquals(SIDE_STEP, decide(MobKind.CREEPER, fusingLow));
    }

    @Test
    void idleCreeperIsMeleedOrShotFromRange() {
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.CREEPER, snap(3)));
        Snapshot armed = new Snapshot(8, 20, false, true, 1.0, false, true, false);
        assertEquals(KEEP_DISTANCE_AND_SHOOT, decide(MobKind.CREEPER, armed));
    }

    @Test
    void endermanAlwaysAvoidsEyeContact() {
        assertEquals(AVOID_EYE_CONTACT, decide(MobKind.ENDERMAN, snap(4)));
        Snapshot water = new Snapshot(4, 20, false, false, 1.0, false, true, true);
        assertEquals(AVOID_EYE_CONTACT, decide(MobKind.ENDERMAN, water));
    }

    @Test
    void blazeShootsOnlyFromRange() {
        Snapshot armedFar = new Snapshot(9, 20, false, true, 1.0, false, true, false);
        assertEquals(KEEP_DISTANCE_AND_SHOOT, decide(MobKind.BLAZE, armedFar));
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.BLAZE, snap(9)));
    }

    @Test
    void hoglinShieldsUpOnlyWhenChargingWithAShield() {
        Snapshot chargingShield = new Snapshot(3, 20, true, false, 1.0, true, true, false);
        assertEquals(SHIELD_UP, decide(MobKind.HOGLIN, chargingShield));
        Snapshot chargingNoShield = new Snapshot(3, 20, false, false, 1.0, true, true, false);
        assertEquals(HIT_AND_BACK_OFF, decide(MobKind.HOGLIN, chargingNoShield));
        Snapshot idle = new Snapshot(3, 20, true, false, 1.0, false, true, false);
        assertEquals(HIT_AND_BACK_OFF, decide(MobKind.HOGLIN, idle));
    }

    @Test
    void phantomHitsAndBacksOff() {
        assertEquals(HIT_AND_BACK_OFF, decide(MobKind.PHANTOM, snap(6)));
    }

    @Test
    void lowHealthRetreatsUnlessMobIsNearlyDead() {
        Snapshot hurt = new Snapshot(3, 5, false, false, 1.0, false, true, false);
        assertEquals(RETREAT, decide(MobKind.ZOMBIE, hurt));
        assertEquals(RETREAT, decide(MobKind.SKELETON, hurt));
        Snapshot finishing = new Snapshot(3, 5, false, false, 0.2, false, true, false);
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.ZOMBIE, finishing));
    }

    @Test
    void retreatBoundaryIsInclusive() {
        Snapshot atLimit = new Snapshot(3, MobTactics.RETREAT_HEALTH, false, false, 1.0, false, true, false);
        assertEquals(RETREAT, decide(MobKind.ZOMBIE, atLimit));
        Snapshot above = new Snapshot(3, MobTactics.RETREAT_HEALTH + 0.5f, false, false, 1.0, false, true, false);
        assertEquals(APPROACH_AND_MELEE, decide(MobKind.ZOMBIE, above));
    }

    @Test
    void hurtCreeperIdleRetreats() {
        Snapshot hurtCreeper = new Snapshot(6, 4, false, false, 1.0, false, true, false);
        assertEquals(RETREAT, decide(MobKind.CREEPER, hurtCreeper));
    }
}
