package dev.vibrato.tasks.combat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDropTableTest {

    @Test
    void mapsEachDropToItsMob() {
        assertEquals(Optional.of("skeleton"), MobDropTable.mobFor("bone"));
        assertEquals(Optional.of("skeleton"), MobDropTable.mobFor("arrow"));
        assertEquals(Optional.of("spider"), MobDropTable.mobFor("string"));
        assertEquals(Optional.of("spider"), MobDropTable.mobFor("spider_eye"));
        assertEquals(Optional.of("creeper"), MobDropTable.mobFor("gunpowder"));
        assertEquals(Optional.of("enderman"), MobDropTable.mobFor("ender_pearl"));
        assertEquals(Optional.of("slime"), MobDropTable.mobFor("slime_ball"));
        assertEquals(Optional.of("cow"), MobDropTable.mobFor("leather"));
        assertEquals(Optional.of("cow"), MobDropTable.mobFor("beef"));
        assertEquals(Optional.of("chicken"), MobDropTable.mobFor("feather"));
        assertEquals(Optional.of("squid"), MobDropTable.mobFor("ink_sac"));
        assertEquals(Optional.of("blaze"), MobDropTable.mobFor("blaze_rod"));
    }

    @Test
    void ghastTearsAreSkipped() {
        assertEquals(Optional.empty(), MobDropTable.mobFor("ghast_tear"));
    }

    @Test
    void unknownDropsAreUnmapped() {
        assertEquals(Optional.empty(), MobDropTable.mobFor("diamond"));
        assertEquals(Optional.empty(), MobDropTable.mobFor(""));
    }

    @Test
    void supportedDropsListsEveryMappedItem() {
        assertTrue(MobDropTable.supportedDrops().contains("blaze_rod"));
        assertFalse(MobDropTable.supportedDrops().contains("ghast_tear"));
    }
}
