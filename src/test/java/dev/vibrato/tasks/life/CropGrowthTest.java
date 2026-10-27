package dev.vibrato.tasks.life;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropGrowthTest {

    @Test
    void maxAgeMatchesVanillaCrops() {
        assertEquals(7, CropGrowth.WHEAT.maxAge());
        assertEquals(7, CropGrowth.CARROT.maxAge());
        assertEquals(7, CropGrowth.POTATO.maxAge());
        assertEquals(3, CropGrowth.BEETROOT.maxAge());
    }

    @Test
    void seedsAndBlocksAreTheVanillaOnes() {
        assertEquals("wheat_seeds", CropGrowth.WHEAT.seedId());
        assertEquals("carrot", CropGrowth.CARROT.seedId());
        assertEquals("potato", CropGrowth.POTATO.seedId());
        assertEquals("beetroot_seeds", CropGrowth.BEETROOT.seedId());
        assertEquals("carrots", CropGrowth.CARROT.blockId());
        assertEquals("beetroots", CropGrowth.BEETROOT.blockId());
    }

    @Test
    void matureOnlyAtMaxAge() {
        assertFalse(CropGrowth.WHEAT.isMature(6));
        assertTrue(CropGrowth.WHEAT.isMature(7));
        assertFalse(CropGrowth.BEETROOT.isMature(2));
        assertTrue(CropGrowth.BEETROOT.isMature(3));
    }

    @Test
    void looksUpByItemId() {
        assertEquals(Optional.of(CropGrowth.BEETROOT), CropGrowth.forItemId("beetroot"));
        assertEquals(Optional.empty(), CropGrowth.forItemId("melon_slice"));
    }
}
