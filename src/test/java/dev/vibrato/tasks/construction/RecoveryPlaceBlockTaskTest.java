package dev.vibrato.tasks.construction;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryPlaceBlockTaskTest {
    @Test void retainsInterruptedProtectionAndHonorsNewProtection() {
        assertTrue(RecoveryMaterialSelector.isProtected(true, false));
        assertTrue(RecoveryMaterialSelector.isProtected(false, true));
        assertTrue(RecoveryMaterialSelector.isProtected(true, true));
        assertFalse(RecoveryMaterialSelector.isProtected(false, false));

        String[] cheap = {"dirt", "cobble", "andesite"};
        Set<String> interrupted = Set.of("dirt");
        Set<String> current = Set.of("cobble");
        assertEquals("andesite", RecoveryMaterialSelector.firstUsable(cheap, item -> true,
                item -> RecoveryMaterialSelector.isProtected(interrupted.contains(item), current.contains(item))));
    }

    @Test void choosesOnlyAvailableUnprotectedMaterial() {
        String[] cheap = {"dirt", "cobble", "andesite"};
        assertEquals("andesite", RecoveryMaterialSelector.firstUsable(cheap,
                Set.of("cobble", "andesite")::contains, Set.of("cobble")::contains));
        assertNull(RecoveryMaterialSelector.firstUsable(cheap,
                Set.of("cobble")::contains, Set.of("cobble")::contains));
        assertNull(RecoveryMaterialSelector.firstUsable(cheap,
                Set.<String>of()::contains, Set.<String>of()::contains));
    }
}
