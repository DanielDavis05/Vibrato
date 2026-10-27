package dev.vibrato.tasks.resources;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoneCollectionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void cobblestoneMiningOwnsPickupAndKeepsFullTarget() {
        CollectCobblestoneTask task = new CollectCobblestoneTask(8);
        assertTrue(task.shouldAvoidPickingUp(null));
        MineAndCollectTask mining = assertInstanceOf(MineAndCollectTask.class, task.onResourceTick(null));
        assertEquals(8, mining.getItemTargets()[0].getTargetCount());
        assertEquals(mining, task.onResourceTick(null));
    }

    @Test
    void deepslateMiningOwnsPickupAndKeepsFullTarget() {
        CollectCobbledDeepslateTask task = new CollectCobbledDeepslateTask(12);
        assertTrue(task.shouldAvoidPickingUp(null));
        MineAndCollectTask mining = assertInstanceOf(MineAndCollectTask.class, task.onResourceTick(null));
        assertEquals(12, mining.getItemTargets()[0].getTargetCount());
        assertEquals(mining, task.onResourceTick(null));
    }
}
