package dev.vibrato.validation;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CropGrowthProgressTest {
    @Test void onlyObservedAgeIncreasesAtSameCropCount() {
        CropGrowthProgress progress = new CropGrowthProgress();
        assertEquals(0, progress.observe(Map.of("a:wheat", 0)));
        assertEquals(2, progress.observe(Map.of("a:wheat", 2)));
        assertEquals(2, progress.observe(Map.of("a:wheat", 0, "b:wheat", 7)));
        assertEquals(3, progress.observe(Map.of("a:wheat", 1)));
        assertEquals(3, progress.observe(Map.of("a:carrot", 6)));
    }
}
