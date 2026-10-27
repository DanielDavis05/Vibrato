package dev.vibrato.tasks.resources;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BucketCollectionPolicyTest {
    record Stack(String item, int count) { }

    @Test void retainedTaskNeverFillsSecondBucketAfterOneIsInMainInventory() {
        List<Stack> main = List.of(new Stack("water", 1), new Stack("empty", 1));
        int water = BucketCollectionPolicy.countMain(main, s -> s.item.equals("water"), Stack::count);
        for (int forcedTick = 0; forcedTick < 10; forcedTick++) {
            assertTrue(BucketCollectionPolicy.goalMet(1, water));
            assertEquals(0, BucketCollectionPolicy.emptyBucketGoal(1, water));
        }
        assertEquals(1, BucketCollectionPolicy.countMain(main, s -> s.item.equals("empty"), Stack::count));
    }

    @Test void cursorAndOffhandAreNotMistakenForMainInventory() {
        List<Stack> main = List.of(new Stack("empty", 2));
        Stack cursor = new Stack("water", 1);
        Stack offhand = new Stack("water", 1);
        assertEquals(1, cursor.count);
        assertEquals(1, offhand.count);
        int water = BucketCollectionPolicy.countMain(main, s -> s.item.equals("water"), Stack::count);
        assertFalse(BucketCollectionPolicy.goalMet(1, water));
        assertEquals(1, BucketCollectionPolicy.emptyBucketGoal(1, water));
    }

    @Test void emptyBucketRequestIsATotalGoalRatherThanOnlyTheDeficit() {
        assertEquals(3, BucketCollectionPolicy.emptyBucketGoal(4, 1));
        assertEquals(0, BucketCollectionPolicy.emptyBucketGoal(1, 2));
        assertTrue(BucketCollectionPolicy.goalMet(2, 2));
    }
}
