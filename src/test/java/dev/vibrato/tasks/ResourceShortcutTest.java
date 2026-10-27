package dev.vibrato.tasks;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceShortcutTest {
    @Test void directMiningKeepsItsActiveToolPrerequisite() {
        assertFalse(ResourceTask.useMineIfPresent(true));
        assertTrue(ResourceTask.useMineIfPresent(false));
    }
}
