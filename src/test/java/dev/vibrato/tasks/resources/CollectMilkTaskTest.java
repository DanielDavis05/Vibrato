package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectMilkTaskTest {
    @Test void preparesOnlyBucketsNeededForRemainingMilk() {
        assertEquals(3, CollectMilkTask.requiredEmptyBuckets(3, 0));
        assertEquals(2, CollectMilkTask.requiredEmptyBuckets(3, 1));
        assertEquals(1, CollectMilkTask.requiredEmptyBuckets(3, 2));
        assertEquals(0, CollectMilkTask.requiredEmptyBuckets(3, 3));
        assertEquals(0, CollectMilkTask.requiredEmptyBuckets(3, 4));
    }
}
