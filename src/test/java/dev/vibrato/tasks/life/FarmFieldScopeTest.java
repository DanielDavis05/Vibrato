package dev.vibrato.tasks.life;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmFieldScopeTest {
    @Test void fieldRemainsAnchoredWhilePlayerGathersSeedsFarAway() {
        var field = new FarmFieldScope();
        var farm = new BlockPos(-1, 78, 40);
        var farAway = new BlockPos(80, 78, 40);
        field.establish(farm);
        field.establish(farAway); // ordinary interruption/reselection must not move it
        for (int x = -5; x <= 5; x++)
            assertTrue(field.contains(farm.add(x, 0, 0), farAway));
        assertFalse(field.contains(farAway, farAway));
        assertEquals(farm, field.anchor()); // seed collection must return to this field
        int planted = 0;
        for (int i = 0; i < 17; i++) {
            if (field.contains(farm.add(i % 5, 1, i / 5), farAway)) planted++;
        }
        assertEquals(17, planted);
        assertEquals(1, new FarmSeedPlan().seedGoal(18, planted, 0, false));
    }
}
