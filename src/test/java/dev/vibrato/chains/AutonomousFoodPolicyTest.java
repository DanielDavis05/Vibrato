package dev.vibrato.chains;

import org.junit.jupiter.api.Test;
import dev.vibrato.director.DirectorTask;
import dev.vibrato.director.FocusedActivityTask;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasks.speedrun.BeatMinecraft2Task;
import dev.vibrato.tasks.resources.MineAndCollectTask;
import static org.junit.jupiter.api.Assertions.*;

class AutonomousFoodPolicyTest {
    @Test void longJourneyRootsOptInWhileShortManualResourceGoalsDoNot() {
        assertTrue(FoodChain.allowsAutonomousForage(DirectorTask.class));
        assertTrue(FoodChain.allowsAutonomousForage(DefaultGoToDimensionTask.class));
        assertTrue(FoodChain.allowsAutonomousForage(BeatMinecraft2Task.class));
        assertTrue(FoodChain.allowsAutonomousForage(FocusedActivityTask.class));
        assertFalse(FoodChain.allowsAutonomousForage(MineAndCollectTask.class));
        assertFalse(FoodChain.allowsAutonomousForage(null));
    }

    @Test void injuredHungryDimensionTravelerStartsBoundedForage() {
        assertTrue(FoodChain.allowsAutonomousForage(DefaultGoToDimensionTask.class));
        var policy = new AutonomousFoodPolicy();
        assertTrue(policy.shouldCollect(10, .25f, 0, false));
        for (int tick = 0; tick < 1200; tick++) policy.advance(true, false);
        assertFalse(policy.shouldCollect(10, .25f, 0, false));
        assertFalse(policy.shouldCollect(10, .25f, 0, false));
    }

    @Test void startsOnlyWhenUrgentAndNoUsableFood() {
        var policy = new AutonomousFoodPolicy();
        assertFalse(policy.shouldCollect(9, 1f, 0, false));
        assertFalse(policy.shouldCollect(8, 1f, 1, false));
        assertTrue(policy.shouldCollect(8, 1f, 0, false));
        policy.reset();
        assertFalse(policy.shouldCollect(18, 0.5f, 0, false));
        assertTrue(policy.shouldCollect(17, 0.5f, 0, false));
    }

    @Test void latchesUntilTargetOrTaskCompletes() {
        var policy = new AutonomousFoodPolicy();
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        assertTrue(policy.shouldCollect(12, 0.8f, 3, false));
        assertFalse(policy.shouldCollect(12, 0.8f, 12, false));
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        assertFalse(policy.shouldCollect(6, 0.2f, 0, true));
    }

    @Test void recoveredPlayerAndRespawnClearStaleForaging() {
        var policy = new AutonomousFoodPolicy();
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        assertFalse(policy.shouldCollect(20, 1f, 0, false));
        assertFalse(policy.shouldCollect(19, 1f, 0, false));
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        // A newly spawned player resets both an active request and any old backoff.
        policy.reset();
        assertFalse(policy.shouldCollect(20, 1f, 0, false));
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        // Ordinary interruptions, and hunger or injury that still needs food, keep the latch.
        policy.advance(false, false);
        assertTrue(policy.shouldCollect(17, 0.4f, 1, false));
        assertTrue(policy.shouldCollect(10, 1f, 1, false));
        assertFalse(policy.shouldCollect(18, 0.75f, 1, false));
    }

    @Test void timesOnlyActiveForagingAndBackoffOnlyNormalPlay() {
        var policy = new AutonomousFoodPolicy();
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        for (int i = 0; i < 2400; i++) policy.advance(false, false); // combat or other interruption
        for (int i = 0; i < 1199; i++) policy.advance(true, false);
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
        policy.advance(true, false);
        assertFalse(policy.shouldCollect(6, 0.2f, 0, false));
        for (int i = 0; i < 2400; i++) policy.advance(false, false);
        assertFalse(policy.shouldCollect(6, 0.2f, 0, false));
        for (int i = 0; i < 1200; i++) policy.advance(false, true);
        assertTrue(policy.shouldCollect(6, 0.2f, 0, false));
    }
}
