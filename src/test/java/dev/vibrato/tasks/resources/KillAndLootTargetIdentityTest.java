package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KillAndLootTargetIdentityTest {
    @Test void distinctSpecificTargetsDoNotRetainPreviousChild() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(KillAndLootTask.matchesTarget(first, first));
        assertFalse(KillAndLootTask.matchesTarget(first, second));
        assertTrue(KillAndLootTask.sameTarget(Object.class, first, Object.class, first));
        assertFalse(KillAndLootTask.sameTarget(Object.class, first, Object.class, second));
        assertFalse(KillAndLootTask.sameTarget(Object.class, first, Object.class, null));
    }
}
