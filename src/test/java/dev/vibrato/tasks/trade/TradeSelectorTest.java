package dev.vibrato.tasks.trade;

import dev.vibrato.tasks.trade.TradeSelector.Offer;
import dev.vibrato.tasks.trade.TradeSelector.Stack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TradeSelectorTest {

    private static Offer<String> buy(int emeralds, String output, int outputCount) {
        return new Offer<>(List.of(new Stack<>("emerald", emeralds)), new Stack<>(output, outputCount), 0, 12, false);
    }

    @Test
    void picksCheapestPerItemReceived() {
        List<Offer<String>> offers = List.of(
                buy(1, "bread", 1),
                buy(2, "bread", 4),
                buy(3, "bread", 2));

        Optional<Offer<String>> chosen = TradeSelector.cheapestBuying(offers, "bread");

        assertEquals(offers.get(1), chosen.orElseThrow());
    }

    @Test
    void skipsDisabledAndExhaustedOffers() {
        Offer<String> disabled = new Offer<>(List.of(new Stack<>("emerald", 1)), new Stack<>("paper", 8), 0, 12, true);
        Offer<String> exhausted = new Offer<>(List.of(new Stack<>("emerald", 1)), new Stack<>("paper", 8), 12, 12, false);
        Offer<String> usable = buy(2, "paper", 1);

        Optional<Offer<String>> chosen = TradeSelector.cheapestBuying(List.of(disabled, exhausted, usable), "paper");

        assertEquals(usable, chosen.orElseThrow());
    }

    @Test
    void returnsEmptyWhenNothingYieldsWanted() {
        assertFalse(TradeSelector.cheapestBuying(List.of(buy(1, "bread", 1)), "book").isPresent());
    }

    @Test
    void costsTwoInputsTogether() {
        Offer<String> expensive = new Offer<>(List.of(new Stack<>("emerald", 5)), new Stack<>("book", 1), 0, 12, false);
        Offer<String> cheap = new Offer<>(
                List.of(new Stack<>("emerald", 1), new Stack<>("paper", 2)),
                new Stack<>("book", 1), 0, 12, false);

        Optional<Offer<String>> chosen = TradeSelector.cheapestBuying(List.of(expensive, cheap), "book");

        assertEquals(cheap, chosen.orElseThrow());
        assertEquals(3, cheap.totalInputCount());
    }

    @Test
    void sellPrefersBestPaymentPerSoldItem() {
        Offer<String> poor = new Offer<>(List.of(new Stack<>("wheat", 20)), new Stack<>("emerald", 1), 0, 12, false);
        Offer<String> good = new Offer<>(List.of(new Stack<>("wheat", 10)), new Stack<>("emerald", 1), 0, 12, false);

        Optional<Offer<String>> chosen = TradeSelector.bestSelling(List.of(poor, good), "wheat", 64);

        assertEquals(good, chosen.orElseThrow());
    }

    @Test
    void sellSkipsTradesWeCannotFill() {
        Offer<String> needsTwenty = new Offer<>(List.of(new Stack<>("wheat", 20)), new Stack<>("emerald", 1), 0, 12, false);

        assertFalse(TradeSelector.bestSelling(List.of(needsTwenty), "wheat", 5).isPresent());
        assertTrue(TradeSelector.bestSelling(List.of(needsTwenty), "wheat", 20).isPresent());
    }
}
