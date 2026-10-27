package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulkGatherToolTest {
    @Test
    void bootstrapLogRequestsAndSmallJobsDoNotCraftTools() {
        // CollectPlanksTask asks for one log at a time, including while
        // gathering the materials needed to make the tool itself.
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(1, false));
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(1, true));
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(3, true));
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(8, false));
        assertTrue(MineAndCollectTask.worthCraftingGatherTool(8, true));
        assertFalse(MineAndCollectTask.worthCraftingGatherTool(15, false));
        assertTrue(MineAndCollectTask.worthCraftingGatherTool(16, false));
    }
}
