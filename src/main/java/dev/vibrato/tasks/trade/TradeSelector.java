package dev.vibrato.tasks.trade;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Picks villager offers from a plain snapshot of their trade list, so the choice can be unit tested without Minecraft.
 */
public final class TradeSelector {

    private TradeSelector() {}

    /** An item and count, as traded. */
    public record Stack<T>(T item, int count) {}

    /** One trade: the {@code inputs} are paid, the {@code output} is received. */
    public record Offer<T>(List<Stack<T>> inputs, Stack<T> output, int uses, int maxUses, boolean disabled) {

        public Offer {
            inputs = List.copyOf(inputs);
        }

        public boolean usable() {
            return !disabled && uses < maxUses;
        }

        public int inputCount(T item) {
            int total = 0;
            for (Stack<T> input : inputs) {
                if (input.item().equals(item)) {
                    total += input.count();
                }
            }
            return total;
        }

        public int totalInputCount() {
            int total = 0;
            for (Stack<T> input : inputs) {
                total += input.count();
            }
            return total;
        }
    }

    /** The usable offer that yields {@code wanted} for the fewest input items per item received. */
    public static <T> Optional<Offer<T>> cheapestBuying(List<Offer<T>> offers, T wanted) {
        return offers.stream()
                .filter(offer -> offer.usable() && offer.output().item().equals(wanted))
                .min(Comparator.comparingDouble(offer -> (double) offer.totalInputCount() / offer.output().count()));
    }

    /**
     * The usable offer that pays the most per {@code sold} item, limited to trades we can fill from {@code available}.
     */
    public static <T> Optional<Offer<T>> bestSelling(List<Offer<T>> offers, T sold, int available) {
        return offers.stream()
                .filter(Offer::usable)
                .filter(offer -> offer.inputCount(sold) > 0 && offer.inputCount(sold) <= available)
                .max(Comparator.comparingDouble(offer -> (double) offer.output().count() / offer.inputCount(sold)));
    }
}
