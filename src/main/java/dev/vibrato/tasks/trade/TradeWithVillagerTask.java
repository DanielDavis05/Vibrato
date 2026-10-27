package dev.vibrato.tasks.trade;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.trade.TradeSelector.Offer;
import net.minecraft.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Buys {@code count} of an item from villagers, collecting the payment items with existing tasks if needed.
 */
public class TradeWithVillagerTask extends AbstractVillagerTradeTask {

    public TradeWithVillagerTask(Item wanted, int count) {
        super(wanted, count);
    }

    @Override
    protected Optional<Offer<Item>> chooseOffer(Vibrato mod, List<Offer<Item>> usable) {
        return TradeSelector.cheapestBuying(usable, _item);
    }

    @Override
    protected int gainOf(Offer<Item> trade) {
        return trade.output().count();
    }

    @Override
    protected boolean mayCollectInputs() {
        return true;
    }

    @Override
    protected boolean isSubEqual(AbstractVillagerTradeTask other) {
        return other instanceof TradeWithVillagerTask task && task._item == _item && task._count == _count;
    }

    @Override
    protected String toDebugString() {
        return "Trading for " + _count + " " + _item.getTranslationKey();
    }
}
