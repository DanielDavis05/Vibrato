package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulkGatherToolTest {
    @Test
    void bootstrapLogRequestsAndSmallJobsDoNotCraftTools() {
        // CollectPlanksTask asks for one log at a time, including while
        // gathering the materials needed to make the tool itself.
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(1));
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(15));
        assertTrue(MineAndCollectTask.worthCraftingGatherTool(16));
    }
}
