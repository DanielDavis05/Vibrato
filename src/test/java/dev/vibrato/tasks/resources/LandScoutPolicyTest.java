package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LandScoutPolicyTest {
    private static LandScoutPolicy.Candidate land(int x, int z) {
        return new LandScoutPolicy.Candidate(x, 64, z, true, true, true, true);
    }

    @Test
    void rejectsWaterObstructionsBlacklistAndAlreadyVisitedOrNearbyChunks() {
        LandScoutPolicy policy = new LandScoutPolicy();
        policy.visited(2, 0);
        var water = new LandScoutPolicy.Candidate(64, 64, 0, false, true, false, true);
        var obstructed = new LandScoutPolicy.Candidate(80, 64, 0, true, false, true, true);
        var unreachable = new LandScoutPolicy.Candidate(96, 64, 0, true, true, true, false);
        var good = land(112, 0);
        assertSame(good, policy.choose(0, 0, false, false,
                List.of(land(5, 0), land(32, 0), water, obstructed, unreachable, good)));
    }

    @Test
    void retainsTargetWhileApproachingAndAvoidsReturningAfterArrival() {
        LandScoutPolicy policy = new LandScoutPolicy();
        var first = land(32, 0);
        var second = land(64, 0);
        assertSame(first, policy.choose(0, 0, false, false, List.of(first, second)));
        assertSame(first, policy.choose(50, 0, true, false, List.of(second)));
        assertSame(second, policy.choose(32, 0, true, true, List.of(first, second)));
        assertNull(policy.choose(64, 0, false, false, List.of(first, second)));
    }
}
