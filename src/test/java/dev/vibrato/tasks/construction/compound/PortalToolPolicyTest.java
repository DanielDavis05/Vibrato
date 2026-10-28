package dev.vibrato.tasks.construction.compound;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalToolPolicyTest {
    @Test void onlySuitableDurableToolAvoidsReplacement() {
        assertTrue(PortalToolPolicy.usable(true, true, 0, 131, 0));
        assertTrue(PortalToolPolicy.usable(true, true, 100, 250, 1));
        assertFalse(PortalToolPolicy.usable(false, true, 0, 59, 0));
        assertFalse(PortalToolPolicy.usable(false, false, 0, 0, 0));
        assertTrue(PortalToolPolicy.usable(true, true, 130, 131, 0));
        assertFalse(PortalToolPolicy.usable(true, true, 130, 131, 1));
    }
    @Test void finishedFrameDoesNotNeedAnotherToolForIgnition() {
        assertTrue(PortalToolPolicy.needsTool(false, false));
        assertFalse(PortalToolPolicy.needsTool(false, true));
        assertFalse(PortalToolPolicy.needsTool(true, false));
    }
}
