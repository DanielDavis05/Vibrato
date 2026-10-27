package dev.vibrato.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignDeathCounterTest {
    private static class Player { boolean dead; }

    @Test void deathIsRetainedAcrossRapidRespawnAndNotDoubleCounted() {
        var counter = new CampaignDeathCounter<Player>();
        var old = new Player();
        assertEquals(0, counter.observe(old, p -> p.dead));
        old.dead = true;
        assertEquals(1, counter.observe(old, p -> p.dead));
        assertEquals(1, counter.observe(null, p -> p.dead));
        assertEquals(1, counter.observe(new Player(), p -> p.dead));
    }

    @Test void deadOldPlayerIsDetectedOnReplacementButDimensionChangeIsNotDeath() {
        var counter = new CampaignDeathCounter<Player>();
        var first = new Player();
        counter.observe(first, p -> p.dead);
        var dimensionPlayer = new Player();
        assertEquals(0, counter.observe(dimensionPlayer, p -> p.dead));
        dimensionPlayer.dead = true;
        assertEquals(1, counter.observe(new Player(), p -> p.dead));
    }
}
