package dev.vibrato.tasks.slot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StackTransferPolicyTest {
    @Test void craftingAndFuelBatchesUseHalfStacksWhenEnough() {
        assertEquals(1, StackTransferPolicy.pickupButton(64, 32));
        assertEquals(1, StackTransferPolicy.pickupButton(5, 3));
        assertEquals(1, StackTransferPolicy.pickupButton(4, 2));
    }

    @Test void neverTakeTooFewForTheRequestedTransfer() {
        for (int available = 1; available <= 64; available++) {
            for (int needed = 1; needed <= available; needed++) {
                int button = StackTransferPolicy.pickupButton(available, needed);
                int picked = button == 1 ? (available + 1) / 2 : available;
                assertTrue(picked >= needed);
                if (needed == available) assertEquals(0, button);
            }
        }
    }
}
