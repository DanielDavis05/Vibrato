package dev.vibrato.tasks.container;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerAcquisitionTest {
    @Test void charcoalLogConsumedDuringFurnacePrerequisiteDoesNotInterruptIt() {
        var acquisition = new ContainerAcquisition();
        acquisition.select(); // furnace recipe begins, first crafting its table
        for (int tick = 0; tick < 100; tick++) {
            boolean smeltLogsAvailable = tick % 2 == 0; // log moves in and out of the grid
            boolean furnaceAvailable = false;
            boolean finishFurnace = acquisition.needsCompletion(furnaceAvailable);
            boolean gatherLogs = !finishFurnace && !smeltLogsAvailable;
            assertTrue(finishFurnace);
            assertFalse(gatherLogs);
        }
        assertFalse(acquisition.needsCompletion(true)); // furnace physically obtained
        assertFalse(acquisition.needsCompletion(false)); // later use cannot resurrect commitment
    }

    @Test void existingContainerCancelsAnObsoleteAcquisition() {
        var acquisition = new ContainerAcquisition();
        acquisition.select();
        acquisition.completedOrReplaced();
        assertFalse(acquisition.needsCompletion(false));
    }
}
