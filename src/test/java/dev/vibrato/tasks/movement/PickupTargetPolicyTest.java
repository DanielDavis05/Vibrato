package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PickupTargetPolicyTest {
    @Test void keepsLiveReachableTargetEvenWhenAnotherBecomesNearest() {
        assertEquals(Optional.of("original"), PickupTargetPolicy.select("original", x -> true,
                () -> fail("should not recalculate nearest while pursuing a valid drop")));
    }

    @Test void switchesWhenCurrentDisappearsOrBecomesUnreachable() {
        assertEquals(Optional.of("replacement"), PickupTargetPolicy.select("original", x -> false,
                () -> Optional.of("replacement")));
        assertEquals(Optional.empty(), PickupTargetPolicy.select(null, x -> true, Optional::empty));
    }
}
