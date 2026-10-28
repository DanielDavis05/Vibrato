package dev.vibrato.tasks.enchant;

import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AnvilRenameRequestTest {
    @Test void changedLocalNameSendsTheActualNativePacketAfterLocalStateUpdates() {
        AtomicReference<String> local = new AtomicReference<>("");
        List<RenameItemC2SPacket> sent = new ArrayList<>();
        assertTrue(AnvilRenameRequest.apply("Lucky Blade", name -> !local.getAndSet(name).equals(name), packet -> {
            assertEquals("Lucky Blade", local.get()); sent.add(packet);
        }));
        assertEquals(1, sent.size()); assertEquals("Lucky Blade", sent.getFirst().getName());
        assertFalse(AnvilRenameRequest.apply("Lucky Blade", name -> !local.getAndSet(name).equals(name), sent::add));
        assertEquals(1, sent.size());
    }
    @Test void unchangedOrRejectedNameNeverSpamsTheNetwork() {
        assertFalse(AnvilRenameRequest.apply("Rejected", name -> false, packet -> fail("unexpected packet")));
    }
}
