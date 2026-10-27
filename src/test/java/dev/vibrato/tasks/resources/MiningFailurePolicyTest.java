package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningFailurePolicyTest {
    @Test
    void suppressesOnlyAfterTwoConnectedFailuresAndExpires() {
        MiningFailurePolicy<Integer> policy = new MiningFailurePolicy<>();
        policy.record(63, 0, 0, 0, 0);
        assertFalse(policy.suppress(65, 0, 0, 0, 1, (a, b) -> Math.abs(a - b) < 10));
        policy.record(64, 0, 0, 0, 2);
        assertTrue(policy.suppress(65, 0, 0, 0, 3, (a, b) -> Math.abs(a - b) < 10));
        assertFalse(policy.suppress(90, 0, 0, 0, 3, (a, b) -> Math.abs(a - b) < 10));
        assertFalse(policy.suppress(65, 0, 0, 0, 30_000_000_002L, (a, b) -> true));
    }

    @Test
    void exhaustedRecoveryLadderRejectsOnlyTheConnectedApproachTemporarily() {
        MiningFailurePolicy<Integer> policy = new MiningFailurePolicy<>();
        policy.rejectApproach(63, 0, 0, 0, 0);
        assertTrue(policy.suppress(64, 0, 0, 0, 1, (a, b) -> Math.abs(a - b) < 10));
        assertFalse(policy.suppress(90, 0, 0, 0, 1, (a, b) -> Math.abs(a - b) < 10));
        assertFalse(policy.suppress(64, 0, 0, 0, 30_000_000_000L, (a, b) -> true));
    }

    @Test
    void newApproachOrSuccessfulMiningResetsFailures() {
        MiningFailurePolicy<Integer> policy = new MiningFailurePolicy<>();
        policy.record(1, 0, 0, 0, 0);
        policy.record(2, 0, 0, 0, 1);
        assertFalse(policy.suppress(3, 4, 0, 0, 2, (a, b) -> true));
        policy.record(1, 4, 0, 0, 3);
        policy.record(2, 4, 0, 0, 4);
        policy.clear();
        assertFalse(policy.suppress(3, 4, 0, 0, 5, (a, b) -> true));
    }
}
