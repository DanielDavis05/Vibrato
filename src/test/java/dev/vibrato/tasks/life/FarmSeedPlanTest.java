package dev.vibrato.tasks.life;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmSeedPlanTest {
    @Test void onePlantedCropAndBareFarmlandStillNeedSeventeenSeeds() {
        var plan = new FarmSeedPlan();
        assertEquals(17, plan.seedGoal(18, 1, 0, false));
        assertTrue(plan.gathering(1));
        assertFalse(plan.gathering(17));
        for (int held = 1; held < 17; held++)
            assertEquals(17, plan.seedGoal(18, 1, held, false));
        assertEquals(0, plan.seedGoal(18, 1, 17, false));
    }
    @Test void matureCropTakesPriorityAndCollectionResumesAfterHarvest() {
        var plan = new FarmSeedPlan();
        assertEquals(17, plan.seedGoal(18, 1, 0, false));
        assertEquals(0, plan.seedGoal(18, 1, 4, true));
        assertEquals(17, plan.seedGoal(18, 0, 4, false));
        assertEquals(0, plan.seedGoal(18, 0, 17, false));
        assertEquals(0, plan.seedGoal(18, 0, 16, false)); // planting while block update catches up
        assertEquals(18, plan.seedGoal(18, 0, 0, false));
    }
    @Test void fullPlantedFieldWaitsWithoutGatheringSeeds() {
        assertEquals(0, new FarmSeedPlan().seedGoal(18, 18, 0, false));
    }
}
