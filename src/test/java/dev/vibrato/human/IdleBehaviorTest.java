package dev.vibrato.human;

import dev.vibrato.human.IdleBehavior.Glance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IdleBehaviorTest {

    @Test
    void glancesAppearEvery80To240IdleTicks() {
        IdleBehavior idle = new IdleBehavior();
        Random rng = new Random(11);
        List<Long> starts = new ArrayList<>();
        for (long tick = 0; tick < 5_000; tick++) {
            Optional<Glance> glance = idle.poll(tick, true, rng);
            if (glance.isPresent()) {
                starts.add(tick);
            }
        }
        assertFalse(starts.isEmpty());
        assertTrue(starts.get(0) >= 80 && starts.get(0) <= 240, "first glance at " + starts.get(0));
        for (int i = 1; i < starts.size(); i++) {
            long gap = starts.get(i) - starts.get(i - 1);
            assertTrue(gap >= 80 && gap <= 240, "gap " + gap);
        }
    }

    @Test
    void glanceOffsetsAndDurationsAreBounded() {
        IdleBehavior idle = new IdleBehavior();
        Random rng = new Random(12);
        int count = 0;
        for (long tick = 0; tick < 50_000; tick++) {
            Optional<Glance> glance = idle.poll(tick, true, rng);
            if (glance.isPresent()) {
                count++;
                Glance g = glance.get();
                assertTrue(Math.abs(g.yawDeg()) <= 25, "yaw " + g.yawDeg());
                assertTrue(Math.abs(g.pitchDeg()) <= 25, "pitch " + g.pitchDeg());
                assertTrue(g.durationTicks() >= 10 && g.durationTicks() <= 30, "duration " + g.durationTicks());
            }
        }
        assertTrue(count > 100);
    }

    @Test
    void nothingHappensWhileBusy() {
        IdleBehavior idle = new IdleBehavior();
        Random rng = new Random(13);
        for (long tick = 0; tick < 5_000; tick++) {
            assertTrue(idle.poll(tick, false, rng).isEmpty());
        }
    }

    @Test
    void busyPeriodRestartsTheSchedule() {
        IdleBehavior idle = new IdleBehavior();
        Random rng = new Random(14);
        for (long tick = 0; tick < 1_000; tick++) {
            idle.poll(tick, true, rng);
        }
        idle.poll(1_000, false, rng);
        long firstIdle = 1_001;
        long firstGlance = -1;
        for (long tick = firstIdle; tick < firstIdle + 1_000 && firstGlance < 0; tick++) {
            if (idle.poll(tick, true, rng).isPresent()) {
                firstGlance = tick;
            }
        }
        // The first idle tick after a busy period only schedules, so the glance comes 80 to 240 ticks later.
        assertTrue(firstGlance - firstIdle >= 80 && firstGlance - firstIdle <= 240, "gap " + (firstGlance - firstIdle));
    }
}
