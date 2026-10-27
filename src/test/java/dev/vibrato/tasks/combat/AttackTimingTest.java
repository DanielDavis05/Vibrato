package dev.vibrato.tasks.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackTimingTest {

    @Test
    void ordinaryMobsSwingAtNinetyPercent() {
        assertFalse(AttackTiming.shouldSwing(0.5f, MobKind.ZOMBIE));
        assertFalse(AttackTiming.shouldSwing(0.89f, MobKind.SPIDER));
        assertTrue(AttackTiming.shouldSwing(0.9f, MobKind.SKELETON));
        assertTrue(AttackTiming.shouldSwing(1.0f, MobKind.HOGLIN));
    }

    @Test
    void creeperAndEndermanNeedAFullCooldown() {
        assertFalse(AttackTiming.shouldSwing(0.95f, MobKind.CREEPER));
        assertTrue(AttackTiming.shouldSwing(1.0f, MobKind.CREEPER));
        assertFalse(AttackTiming.shouldSwing(0.95f, MobKind.ENDERMAN));
        assertTrue(AttackTiming.shouldSwing(1.0f, MobKind.ENDERMAN));
    }

    @Test
    void zeroCooldownNeverSwings() {
        for (MobKind kind : MobKind.values()) {
            assertFalse(AttackTiming.shouldSwing(0f, kind), kind.name());
        }
    }
}
