package dev.vibrato.tasks.trade;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TradeTransferTest {
    @Test void previewOrPartialTransferCannotCountAsCompletedTrade() {
        assertEquals(0, TradeTransfer.completedTrades(3, 3, 2));
        assertEquals(0, TradeTransfer.completedTrades(3, 4, 2));
        assertEquals(1, TradeTransfer.completedTrades(3, 5, 2));
        assertEquals(2, TradeTransfer.completedTrades(3, 7, 2));
        assertEquals(0, TradeTransfer.completedTrades(3, 1, 2));
    }
}
