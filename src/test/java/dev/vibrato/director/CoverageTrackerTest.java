package dev.vibrato.director;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageTrackerTest {

    @TempDir
    Path tempDir;

    private static ActivityRegistry everyAreaRegistry() {
        ActivityRegistry registry = new ActivityRegistry();
        for (FeatureArea area : FeatureArea.values()) {
            for (int i = 0; i < 3; i++) {
                registry.register(SyntheticActivity.of(area.name().toLowerCase() + "_" + i, area));
            }
        }
        return registry;
    }

    @Test
    void boostBalancesAreasOverManyPlans() {
        ActivityRegistry registry = everyAreaRegistry();
        PlanSampler sampler = new PlanSampler(registry);
        CoverageTracker coverage = new CoverageTracker();

        for (long seed = 1; seed <= 300; seed++) {
            Plan plan = sampler.sample(seed, 10, Personas.GENERALIST, Set.of(), coverage);
            plan.steps().forEach(step -> coverage.record(step.activity()));
        }

        List<Integer> counts = new ArrayList<>();
        for (FeatureArea area : FeatureArea.values()) {
            int uses = coverage.uses(area);
            assertTrue(uses > 0, area + " was never exercised");
            counts.add(uses);
        }
        int most = counts.stream().max(Integer::compare).orElseThrow();
        int least = counts.stream().min(Integer::compare).orElseThrow();
        assertTrue(most < 4 * least, "most " + most + " vs least " + least);
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        CoverageTracker original = new CoverageTracker();
        SyntheticActivity craft = SyntheticActivity.of("craft_pick", FeatureArea.CRAFT);
        original.record(craft);
        original.record(craft);
        original.record(SyntheticActivity.of("walk", FeatureArea.EXPLORE));
        original.recordFailure("craft_pick", 3);

        Path file = tempDir.resolve("nested").resolve("coverage.json");
        original.save(file);
        CoverageTracker loaded = CoverageTracker.load(file);

        assertEquals(original.snapshot(), loaded.snapshot());
        assertEquals(2, loaded.uses("craft_pick"));
        assertEquals(1, loaded.uses("walk"));
        assertTrue(loaded.isOnCooldown("craft_pick"));
        assertFalse(loaded.isOnCooldown("walk"));
    }

    @Test
    void missingFileGivesEmptyTracker() {
        CoverageTracker loaded = CoverageTracker.load(tempDir.resolve("absent.json"));

        assertTrue(loaded.snapshot().values().stream().allMatch(uses -> uses == 0));
        assertFalse(loaded.isOnCooldown("anything"));
    }

    @Test
    void corruptFileGivesEmptyTracker() throws IOException {
        Path file = tempDir.resolve("coverage.json");
        Files.writeString(file, "{ this is not json");

        CoverageTracker loaded = CoverageTracker.load(file);

        assertTrue(loaded.snapshot().values().stream().allMatch(uses -> uses == 0));
        assertEquals(0, loaded.uses("craft_pick"));
    }

    @Test
    void cooldownCountsDownPerPlan() {
        CoverageTracker coverage = new CoverageTracker();
        coverage.recordFailure("dig", 2);

        assertTrue(coverage.isOnCooldown("dig"));
        coverage.tickPlan();
        assertTrue(coverage.isOnCooldown("dig"));
        coverage.tickPlan();
        assertFalse(coverage.isOnCooldown("dig"));
    }

    @Test
    void snapshotListsEveryArea() {
        Map<FeatureArea, Integer> snapshot = new CoverageTracker().snapshot();

        assertEquals(Set.of(FeatureArea.values()), snapshot.keySet());
    }
}
