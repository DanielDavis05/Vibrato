package dev.vibrato.tasks.trade;

/** A merchant preview is not a trade; only received inventory output establishes transfer. */
final class TradeTransfer {
    static int completedTrades(int before, int after, int outputPerTrade) {
        return outputPerTrade <= 0 ? 0 : Math.max(0, after - before) / outputPerTrade;
    }
}
