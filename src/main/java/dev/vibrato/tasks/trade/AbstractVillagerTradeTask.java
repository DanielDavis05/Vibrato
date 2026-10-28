package dev.vibrato.tasks.trade;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasks.slot.ClickSlotTask;
import dev.vibrato.tasks.slot.MoveItemToSlotFromInventoryTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasks.trade.TradeSelector.Offer;
import dev.vibrato.tasks.trade.TradeSelector.Stack;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.time.TimerGame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Walks to the nearest villager, opens its trade screen and performs trades until {@link #isFinished} reports that
 * the goal was reached or no usable trade remains (see {@link #failed()}).
 * <p>
 * Subclasses decide which offer to take and what a completed trade gains towards the goal.
 */
public abstract class AbstractVillagerTradeTask extends Task {

    private static final int INPUT_SLOT_A = 0;
    private static final int INPUT_SLOT_B = 1;
    private static final int OUTPUT_SLOT = 2;
    private static final double VILLAGER_REACH = 2;
    private static final int MAX_FAILURES = 4;
    private static final int MAX_INTERACT_ATTEMPTS = 8;
    // "At most once" per task: one trip to collect missing inputs.
    private static final int MAX_COLLECTIONS = 1;

    protected final Item _item;
    protected final int _count;

    private final TimeoutWanderTask _wander = new TimeoutWanderTask(10);
    private final TimerGame _interactTimer = new TimerGame(1);
    private final TimerGame _progressTimer = new TimerGame(8);
    private final Set<Offer<Item>> _blocked = new HashSet<>();
    private final VillagerRetryTracker _unavailableVillagers = new VillagerRetryTracker(120_000_000_000L);
    private UUID _villagerId;

    private int _gained = 0;
    private int _failures = 0;
    private int _collections = 0;
    private int _interactAttempts = 0;
    private boolean _exhausted = false;
    private boolean _outputCounted = false;
    private int _outputBefore;
    private ClickSlotTask _takeOutputTask;
    private long _offerWaitStarted;

    private Offer<Item> _current = null;
    private Task _collectTask = null;

    protected AbstractVillagerTradeTask(Item item, int count) {
        _item = item;
        _count = count;
    }

    /** The usable offer to take next, or empty if none is worth taking. */
    protected abstract Optional<Offer<Item>> chooseOffer(Vibrato mod, List<Offer<Item>> usable);

    /** How much of the goal a completed {@code trade} advances. */
    protected abstract int gainOf(Offer<Item> trade);

    /** Whether missing inputs may be collected with existing resource tasks. */
    protected abstract boolean mayCollectInputs();

    protected abstract boolean isSubEqual(AbstractVillagerTradeTask other);

    /** True when the task stopped without reaching its goal. */
    public boolean failed() {
        return _exhausted && _gained < _count;
    }

    public int gained() {
        return _gained;
    }

    @Override
    protected void onStart(Vibrato mod) {
        mod.getBehaviour().push();
        mod.getBehaviour().addProtectedItems(Items.EMERALD, _item);
        _wander.resetWander();
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_exhausted) {
            return null;
        }
        if (_failures >= MAX_FAILURES) {
            return rejectVillager("too many failed trades");
        }
        if (_collectTask != null && _collectTask.isActive() && !_collectTask.isFinished(mod)) {
            setDebugState("Collecting trade inputs");
            return _collectTask;
        }
        if (!(mod.getPlayer().currentScreenHandler instanceof MerchantScreenHandler merchant)) {
            return approachVillager(mod);
        }
        return trade(mod, merchant);
    }

    private Task trade(Vibrato mod, MerchantScreenHandler merchant) {
        _interactAttempts = 0;
        if (_outputCounted && _current != null) {
            if (_takeOutputTask != null && !_takeOutputTask.isFinished(mod)) return _takeOutputTask;
            int trades = TradeTransfer.completedTrades(_outputBefore,
                    playerInventoryCount(mod, _current.output().item()), _current.output().count());
            if (trades > 0) {
                _gained += trades * gainOf(_current);
                _outputCounted = false;
                _current = null;
                _progressTimer.reset();
            } else if (_progressTimer.elapsed()) {
                _outputCounted = false;
                stall();
            } else {
                setDebugState("Waiting for trade output transfer");
                return null;
            }
        }
        if (_gained >= _count) {
            setDebugState("Goal reached");
            return null;
        }

        if (_current == null) {
            List<Offer<Item>> offers = snapshot(merchant.getRecipes());
            if (offers.isEmpty()) {
                if (_offerWaitStarted == 0) _offerWaitStarted = System.nanoTime();
                if (System.nanoTime() - _offerWaitStarted < 8_000_000_000L) {
                    setDebugState("Waiting for merchant offers");
                    return null;
                }
                return rejectVillager("merchant offers did not arrive");
            }
            _offerWaitStarted = 0;
            List<Offer<Item>> usable = new ArrayList<>();
            for (Offer<Item> offer : offers) {
                if (!_blocked.contains(offer)) usable.add(offer);
            }
            Optional<Offer<Item>> chosen = chooseOffer(mod, usable);
            if (chosen.isEmpty()) return rejectVillager("no usable trade");
            _current = chosen.get();
            int index = offers.indexOf(_current);
            merchant.setRecipeIndex(index);
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
            _progressTimer.reset();
        }

        ItemStack output = StorageHelper.getItemStackInSlot(new PlayerSlot(OUTPUT_SLOT));
        if (!output.isEmpty() && output.isOf(_current.output().item())) {
            _outputBefore = playerInventoryCount(mod, _current.output().item());
            _outputCounted = true;
            _progressTimer.reset();
            setDebugState("Taking trade output");
            _takeOutputTask = new ClickSlotTask(new PlayerSlot(OUTPUT_SLOT), SlotActionType.QUICK_MOVE);
            return _takeOutputTask;
        }

        // Clear stale items left in the input slots by a previous trade.
        List<Stack<Item>> inputs = _current.inputs();
        for (int i = 0; i < 2; i++) {
            ItemStack inSlot = StorageHelper.getItemStackInSlot(new PlayerSlot(i));
            boolean wanted = i < inputs.size() && inSlot.isOf(inputs.get(i).item());
            if (!inSlot.isEmpty() && !wanted) {
                setDebugState("Clearing input slot " + i);
                return new ClickSlotTask(new PlayerSlot(i), SlotActionType.QUICK_MOVE);
            }
        }

        for (int i = 0; i < inputs.size(); i++) {
            Stack<Item> input = inputs.get(i);
            ItemStack inSlot = StorageHelper.getItemStackInSlot(new PlayerSlot(i));
            if (inSlot.isOf(input.item()) && inSlot.getCount() >= input.count()) {
                continue;
            }
            if (mod.getItemStorage().getItemCount(input.item()) < input.count()) {
                return missingInput(input);
            }
            setDebugState("Placing " + input.count() + " " + input.item().getTranslationKey());
            return new MoveItemToSlotFromInventoryTask(new ItemTarget(input.item(), input.count()), new PlayerSlot(i));
        }

        if (_progressTimer.elapsed()) {
            // The villager did not produce the output: give this offer up.
            stall();
            return null;
        }
        setDebugState("Waiting for trade output");
        return null;
    }

    private int playerInventoryCount(Vibrato mod, Item item) {
        int count = 0;
        for (int slot = 0; slot < mod.getPlayer().getInventory().size(); slot++) {
            ItemStack stack = mod.getPlayer().getInventory().getStack(slot);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    private Task missingInput(Stack<Item> input) {
        if (!mayCollectInputs() || _collections >= MAX_COLLECTIONS) {
            stall();
            setDebugState("Missing trade inputs");
            return null;
        }
        _collections++;
        Task collect = TaskCatalogue.getItemTask(input.item(), input.count());
        if (collect == null) {
            return exhaust("no task can collect " + input.item().getTranslationKey());
        }
        StorageHelper.closeScreen();
        _collectTask = collect;
        setDebugState("Collecting " + input.count() + " " + input.item().getTranslationKey() + " for trade");
        return _collectTask;
    }

    private Task approachVillager(Vibrato mod) {
        _current = null;
        _outputCounted = false;

        long now = System.nanoTime();
        Optional<Entity> villager = mod.getEntityTracker().getClosestEntity(entity ->
                entity instanceof VillagerEntity candidate && candidate.isAlive() && !candidate.isBaby()
                        && candidate.getVillagerData().getProfession() != VillagerProfession.NITWIT
                        && _unavailableVillagers.available(candidate.getUuid(), now)
                        && (_villagerId == null || _villagerId.equals(candidate.getUuid())), VillagerEntity.class);
        if (villager.isEmpty()) {
            // A selected villager can leave loaded range or die while approaching.
            if (_villagerId != null) return rejectVillager("villager no longer available");
            setDebugState("No usable villager nearby, wandering");
            return _wander;
        }
        Entity entity = villager.get();
        _villagerId = entity.getUuid();
        if (!mod.getPlayer().isInRange(entity, VILLAGER_REACH)) {
            setDebugState("Walking to villager");
            return new GetToEntityTask(entity, VILLAGER_REACH);
        }
        if (_interactTimer.elapsed()) {
            _interactTimer.reset();
            if (++_interactAttempts > MAX_INTERACT_ATTEMPTS) {
                return rejectVillager("villager would not open its trades");
            }
            mod.getController().interactEntity(mod.getPlayer(), entity, Hand.MAIN_HAND);
        }
        setDebugState("Opening trade screen");
        return null;
    }

    private Task rejectVillager(String reason) {
        if (_villagerId != null) _unavailableVillagers.reject(_villagerId, System.nanoTime());
        _villagerId = null;
        _interactAttempts = 0;
        _offerWaitStarted = 0;
        _failures = 0;
        _current = null;
        _outputCounted = false;
        _blocked.clear();
        StorageHelper.closeScreen();
        setDebugState("Trying another villager: " + reason);
        return null;
    }

    /** Gives up on the current offer and counts it towards the failure limit. */
    private void stall() {
        if (_current != null) {
            _blocked.add(_current);
        }
        _current = null;
        _failures++;
        _progressTimer.reset();
    }

    private Task exhaust(String reason) {
        _exhausted = true;
        setDebugState("Stopping: " + reason);
        return null;
    }

    static List<Offer<Item>> snapshot(List<TradeOffer> trades) {
        List<Offer<Item>> result = new ArrayList<>();
        for (TradeOffer trade : trades) {
            List<Stack<Item>> inputs = new ArrayList<>();
            inputs.add(stack(trade.getFirstBuyItem().item().value(), trade.getFirstBuyItem().count()));
            trade.getSecondBuyItem().ifPresent(second -> inputs.add(stack(second.item().value(), second.count())));
            ItemStack sell = trade.getSellItem();
            result.add(new Offer<>(inputs, stack(sell.getItem(), sell.getCount()), trade.getUses(), trade.getMaxUses(), trade.isDisabled()));
        }
        return result;
    }

    private static Stack<Item> stack(Item item, int count) {
        return new Stack<>(item, count);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        StorageHelper.closeScreen();
        mod.getBehaviour().pop();
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        if (_exhausted) {
            return true;
        }
        boolean outputPending = mod.getPlayer() != null
                && mod.getPlayer().currentScreenHandler instanceof MerchantScreenHandler
                && !StorageHelper.getItemStackInSlot(new PlayerSlot(OUTPUT_SLOT)).isEmpty();
        return _gained >= _count && !outputPending;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other != null && other.getClass() == getClass()) {
            return isSubEqual((AbstractVillagerTradeTask) other);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return getClass().getSimpleName() + " " + _count + " " + _item.getTranslationKey();
    }
}
