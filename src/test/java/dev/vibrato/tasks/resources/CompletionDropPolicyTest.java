package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletionDropPolicyTest {
    @Test
    void prefersPickupOnlyWhenNearbyDropsCoverRemainingTargets() {
        assertTrue(CompletionDropPolicy.canFinish(new int[]{7}, new int[]{8}, new int[]{1}));
        assertTrue(CompletionDropPolicy.canFinish(new int[]{5}, new int[]{8}, new int[]{3}));
        assertFalse(CompletionDropPolicy.canFinish(new int[]{5}, new int[]{8}, new int[]{2}));
        assertFalse(CompletionDropPolicy.canFinish(new int[]{8}, new int[]{8}, new int[]{1}));
    }

    @Test
    void allOutstandingTargetsMustBeCovered() {
        assertFalse(CompletionDropPolicy.canFinish(new int[]{1, 0}, new int[]{2, 2}, new int[]{1, 1}));
        assertTrue(CompletionDropPolicy.canFinish(new int[]{1, 0}, new int[]{2, 2}, new int[]{1, 2}));
    }
}
