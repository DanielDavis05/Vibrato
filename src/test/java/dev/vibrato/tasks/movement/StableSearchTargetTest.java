package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StableSearchTargetTest {
    @Test
    void retainsTargetEvenWhenAnotherBecomesCloser() {
        StableSearchTarget<String> target = new StableSearchTarget<>();
        assertEquals("west", target.select(List.of("west", "east"), () -> "west"));
        assertEquals("west", target.select(List.of("west", "east"), () -> "east"));
    }

    @Test
    void choosesAgainOnceTargetHasBeenSearched() {
        StableSearchTarget<String> target = new StableSearchTarget<>();
        assertEquals("west", target.select(List.of("west", "east"), () -> "west"));
        assertEquals("east", target.select(List.of("east"), () -> "east"));
        assertNull(target.select(List.of(), () -> null));
    }
}
