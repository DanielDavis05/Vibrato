package dev.vibrato.tasks.trade;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.trade.TradeSelector.Offer;
import net.minecraft.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Sells {@code count} of a surplus item to villagers for whatever they pay. Never collects anything: it only sells
 * what the inventory already holds.
 */
public class SellToVillagerTask extends AbstractVillagerTradeTask {

    public SellToVillagerTask(Item sell, int count) {
        super(sell, count);
    }

    @Override
    protected Optional<Offer<Item>> chooseOffer(Vibrato mod, List<Offer<Item>> usable) {
        int available = mod.getItemStorage().getItemCount(_item);
        return TradeSelector.bestSelling(usable, _item, available);
    }

    @Override
    protected int gainOf(Offer<Item> trade) {
        return trade.inputCount(_item);
    }

    @Override
    protected boolean mayCollectInputs() {
        return false;
    }

    @Override
    protected boolean isSubEqual(AbstractVillagerTradeTask other) {
        return other instanceof SellToVillagerTask task && task._item == _item && task._count == _count;
    }

    @Override
    protected String toDebugString() {
        return "Selling " + _count + " " + _item.getTranslationKey();
    }
}
