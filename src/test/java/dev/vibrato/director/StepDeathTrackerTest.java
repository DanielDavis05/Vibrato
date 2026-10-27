package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import static dev.vibrato.director.StepDeathTracker.State.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StepDeathTrackerTest {
    private static class Player { boolean dead; }

    @Test
    void healthyPlayerReplacementDoesNotCountAsDeath() {
        StepDeathTracker<Player> tracker = new StepDeathTracker<>();
        Player player = new Player();
        assertEquals(ALIVE, tracker.observe(player, p -> p.dead));
        assertEquals(ALIVE, tracker.observe(player, p -> p.dead));
        assertEquals(ALIVE, tracker.observe(new Player(), p -> p.dead));
    }

    @Test
    void deathWhileInterruptedIsDetectedAfterRespawn() {
        StepDeathTracker<Player> tracker = new StepDeathTracker<>();
        Player previous = new Player();
        tracker.observe(previous, p -> p.dead);
        previous.dead = true;
        assertEquals(DEAD, tracker.observe(null, p -> p.dead));
        assertEquals(RESPAWNED, tracker.observe(new Player(), p -> p.dead));
        assertEquals(ALIVE, tracker.observe(new Player(), p -> p.dead));
    }

    @Test
    void deathObservedBeforeRespawnRemainsRecorded() {
        StepDeathTracker<Player> tracker = new StepDeathTracker<>();
        Player previous = new Player();
        tracker.observe(previous, p -> p.dead);
        previous.dead = true;
        assertEquals(DEAD, tracker.observe(previous, p -> p.dead));
        previous.dead = false; // The old object can be mutated after death.
        assertEquals(RESPAWNED, tracker.observe(new Player(), p -> p.dead));
    }
}
