package dev.vibrato.tasks.resources;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.Debug;
import dev.vibrato.tasks.AbstractDoToClosestObjectTask;
import dev.vibrato.tasks.ResourceTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasks.slot.EnsureFreeInventorySlotTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.MiningRequirement;
import dev.vibrato.util.slots.PlayerSlot;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.helpers.ItemHelper;
import dev.vibrato.util.helpers.LookHelper;
import dev.vibrato.util.helpers.StorageHelper;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import dev.vibrato.util.slots.CursorSlot;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class MineAndCollectTask extends ResourceTask {

    private final Block[] _blocksToMine;

    private final MiningRequirement _requirement;

    private final TimerGame _cursorStackTimer = new TimerGame(3);

    private final MineOrCollectTask _subtask;
    private Item _acquiringGatherTool;
    private boolean _allowGatherTool = true;

    public MineAndCollectTask(ItemTarget[] itemTargets, Block[] blocksToMine, MiningRequirement requirement) {
        super(itemTargets);
        _requirement = requirement;
        _blocksToMine = blocksToMine;
        _subtask = new MineOrCollectTask(_blocksToMine, _itemTargets);
    }

    public MineAndCollectTask(ItemTarget[] blocksToMine, MiningRequirement requirement) {
        this(blocksToMine, itemTargetToBlockList(blocksToMine), requirement);
    }

    public MineAndCollectTask(ItemTarget target, Block[] blocksToMine, MiningRequirement requirement) {
        this(new ItemTarget[]{target}, blocksToMine, requirement);
    }

    public MineAndCollectTask(Item item, int count, Block[] blocksToMine, MiningRequirement requirement) {
        this(new ItemTarget(item, count), blocksToMine, requirement);
    }

    public static Block[] itemTargetToBlockList(ItemTarget[] targets) {
        List<Block> result = new ArrayList<>(targets.length);
        for (ItemTarget target : targets) {
            for (Item item : target.getMatches()) {
                Block block = Block.getBlockFromItem(item);
                if (block != null && !WorldHelper.isAir(block)) {
                    result.add(block);
                }
            }
        }
        return result.toArray(Block[]::new);
    }

    @Override
    protected void onResourceStart(Vibrato mod) {
        mod.getBehaviour().push();
        mod.getBlockTracker().trackBlock(_blocksToMine);

        // We're mining, so don't throw away pickaxes.
        mod.getBehaviour().addProtectedItems(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);

        _subtask.resetSearch();
    }

    @Override
    protected boolean shouldAvoidPickingUp(Vibrato mod) {
        // Picking up is controlled by a separate task here.
        return true;
    }

    @Override
    protected Task onResourceTick(Vibrato mod) {

        if (!StorageHelper.miningRequirementMet(mod, _requirement)) {
            return new SatisfyMiningRequirementTask(_requirement);
        }

        // Hand-harvestable blocks can still be much faster with a tool. Only
        // invest in one for a substantial request; small bootstrap collections
        // needed to craft the tool itself must remain possible by hand.
        if (_acquiringGatherTool != null && hasUsefulGatherTool(mod)) {
            _acquiringGatherTool = null;
        }
        if (_acquiringGatherTool == null) _acquiringGatherTool = bulkHandHarvestTool(mod);
        if (_acquiringGatherTool != null) {
            // Keep the commitment while its ingredients move into the crafting
            // grid; rechecking their inventory counts would cancel it mid-craft.
            return TaskCatalogue.getItemTask(_acquiringGatherTool, 1);
        }

        if (_subtask.isMining()) {
            makeSureToolIsEquipped(mod);
        }

        // Wrong dimension check.
        if (_subtask.wasWandering() && isInWrongDimension(mod)) {
            return getToCorrectDimensionTask(mod);
        }

        return _subtask;
    }

    @Override
    protected void onResourceStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(_blocksToMine);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof MineAndCollectTask task) {
            return Arrays.equals(task._blocksToMine, _blocksToMine)
                    && task._allowGatherTool == _allowGatherTool;
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Mine And Collect";
    }

    /** Avoid acquiring a tool when this collection supplies its recipe. */
    public MineAndCollectTask withoutGatherTool() {
        _allowGatherTool = false;
        return this;
    }

    private Item bulkHandHarvestTool(Vibrato mod) {
        if (!_allowGatherTool) return null;
        if (_requirement != MiningRequirement.HAND || _blocksToMine.length == 0) return null;
        int remaining = 0;
        for (ItemTarget target : _itemTargets) {
            remaining += Math.max(0, target.getTargetCount()
                    - mod.getItemStorage().getItemCountInventoryOnly(target.getMatches()));
        }
        if (remaining < 4) return null;

        // A tool is useful only if it speeds up every block in this request.
        // This avoids crafting an axe for a mixed set of unrelated blocks.
        if (hasUsefulGatherTool(mod)) return null;
        Item tool = speedsEveryBlock(new ItemStack(Items.WOODEN_AXE)) ? Items.WOODEN_AXE
                : speedsEveryBlock(new ItemStack(Items.WOODEN_SHOVEL)) ? Items.WOODEN_SHOVEL : null;
        if (tool == null) return null;
        boolean ready = remaining < 16 && hasReadyToolMaterials(mod, tool) && hasNearbyCraftingTable(mod);
        return worthCraftingGatherTool(remaining, ready) ? tool : null;
    }

    private boolean hasUsefulGatherTool(Vibrato mod) {
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (speedsEveryBlock(stack)) return true;
        }
        return false;
    }

    private boolean hasReadyToolMaterials(Vibrato mod, Item tool) {
        if (mod.getItemStorage().getItemCountInventoryOnly(Items.STICK) < 2) return false;
        int neededPlanks = tool == Items.WOODEN_AXE ? 3 : 1;
        for (Item plank : ItemHelper.PLANKS) {
            if (mod.getItemStorage().getItemCountInventoryOnly(plank) >= neededPlanks) return true;
        }
        return false;
    }

    private boolean hasNearbyCraftingTable(Vibrato mod) {
        if (StorageHelper.isBigCraftingOpen() || mod.getItemStorage().hasItemInventoryOnly(Items.CRAFTING_TABLE)) return true;
        BlockPos center = mod.getPlayer().getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(center, 4, 3, 4)) {
            if (mod.getWorld().getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) return true;
        }
        return false;
    }

    static boolean worthCraftingGatherTool(int remaining, boolean materialsAndTableReady) {
        return remaining >= 16 || (remaining >= 4 && materialsAndTableReady);
    }

    private boolean speedsEveryBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Block block : _blocksToMine) {
            if (stack.getMiningSpeedMultiplier(block.getDefaultState()) <= 1.0f) return false;
        }
        return true;
    }

    private void makeSureToolIsEquipped(Vibrato mod) {
        if (_cursorStackTimer.elapsed() && !mod.getFoodChain().isTryingToEat()) {
            assert MinecraftClient.getInstance().player != null;
            ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot();
            if (cursorStack != null && !cursorStack.isEmpty()) {
                // We have something in our cursor stack
                Item item = cursorStack.getItem();
                if (cursorStack.isSuitableFor(mod.getWorld().getBlockState(_subtask.miningPos()))) {
                    // Our cursor stack would help us mine our current block
                    Item currentlyEquipped = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot()).getItem();
                    if (item instanceof MiningToolItem) {
                        if (currentlyEquipped instanceof MiningToolItem currentPick) {
                            MiningToolItem swapPick = (MiningToolItem) item;
                            if (ItemHelper.getMiningLevel(swapPick.getMaterial()) > ItemHelper.getMiningLevel(currentPick.getMaterial())) {
                                // We can equip a better pickaxe.
                                mod.getSlotHandler().forceEquipSlot(CursorSlot.SLOT);
                            }
                        } else {
                            // We're not equipped with a pickaxe...
                            mod.getSlotHandler().forceEquipSlot(CursorSlot.SLOT);
                        }
                    }
                }
            }
            _cursorStackTimer.reset();
        }
    }

    private static class MineOrCollectTask extends AbstractDoToClosestObjectTask<Object> {

        private static final double COMPLETION_DROP_RADIUS_SQ = 36;
        private final Block[] _blocks;
        private final ItemTarget[] _targets;
        private final Set<BlockPos> _blacklist = new HashSet<>();
        private final MovementProgressChecker _progressChecker = new MovementProgressChecker(1);
        private final Task _pickupTask;
        private BlockPos _miningPos;
        private Vibrato _mod;

        public MineOrCollectTask(Block[] blocks, ItemTarget[] targets) {
            _blocks = blocks;
            _targets = targets;
            _pickupTask = new PickupDroppedItemTask(_targets, true);
        }

        @Override
        protected Vec3d getPos(Vibrato mod, Object obj) {
            if (obj instanceof BlockPos b) {
                return WorldHelper.toVec3d(b);
            }
            if (obj instanceof ItemEntity item) {
                return item.getPos();
            }
            throw new UnsupportedOperationException("Shouldn't try to get the position of object " + obj + " of type " + (obj != null ? obj.getClass().toString() : "(null object)"));
        }

        @Override
        protected Optional<Object> getClosestTo(Vibrato mod, Vec3d pos) {
            int[] held = new int[_targets.length];
            int[] required = new int[_targets.length];
            int[] nearby = new int[_targets.length];
            for (int i = 0; i < _targets.length; i++) {
                held[i] = mod.getItemStorage().getItemCountInventoryOnly(_targets[i].getMatches());
                required[i] = _targets[i].getTargetCount();
            }
            for (ItemEntity drop : mod.getEntityTracker().getTrackedEntities(ItemEntity.class)) {
                if (!drop.isAlive() || !mod.getEntityTracker().isEntityReachable(drop)
                        || drop.squaredDistanceTo(pos) > COMPLETION_DROP_RADIUS_SQ) continue;
                for (int i = 0; i < _targets.length; i++) {
                    if (_targets[i].matches(drop.getStack().getItem())) nearby[i] += drop.getStack().getCount();
                }
            }
            if (CompletionDropPolicy.canFinish(held, required, nearby)) {
                Optional<ItemEntity> completingDrop = mod.getEntityTracker().getClosestItemDrop(pos, drop -> {
                    if (drop.squaredDistanceTo(pos) > COMPLETION_DROP_RADIUS_SQ) return false;
                    for (int i = 0; i < _targets.length; i++) {
                        if (held[i] < required[i] && _targets[i].matches(drop.getStack().getItem())) return true;
                    }
                    return false;
                }, _targets);
                if (completingDrop.isPresent()) {
                    // Override the generic distance hysteresis only when pickup can finish this request.
                    if (_miningPos != null) resetSearch();
                    return completingDrop.map(Object.class::cast);
                }
            }
            // Prefer a nearby block we can already see and mine without removing our own footing.
            // This is only a preference: distant or buried ore remains eligible in the fallback.
            BlockPos feet = mod.getPlayer().getBlockPos();
            Optional<BlockPos> closestBlock = mod.getBlockTracker().getNearestWithinRange(pos, 4, check -> {
                if (check.getY() < feet.getY()) return false;
                if (check.getSquaredDistance(pos) > 16 || _blacklist.contains(check)) return false;
                return LookHelper.getReach(check).isPresent() && WorldHelper.canBreak(mod, check);
            }, _blocks);
            if (closestBlock.isEmpty()) closestBlock = mod.getBlockTracker().getNearestWithinRange(pos, 4, check -> {
                if (check.getX() == feet.getX() && check.getZ() == feet.getZ() && check.getY() < feet.getY()) return false;
                if (check.getSquaredDistance(pos) > 16 || _blacklist.contains(check)) return false;
                return LookHelper.getReach(check).isPresent() && WorldHelper.canBreak(mod, check);
            }, _blocks);
            if (closestBlock.isEmpty()) closestBlock = mod.getBlockTracker().getNearestTracking(pos, check -> {
                if (_blacklist.contains(check)) return false;
                return WorldHelper.canBreak(mod, check);
            }, _blocks);

            Optional<ItemEntity> closestDrop = Optional.empty();
            if (mod.getEntityTracker().itemDropped(_targets)) {
                closestDrop = mod.getEntityTracker().getClosestItemDrop(pos, _targets);
            }

            double blockSq = closestBlock.isEmpty() ? Double.POSITIVE_INFINITY : closestBlock.get().getSquaredDistance(pos);
            double dropSq = closestDrop.isEmpty() ? Double.POSITIVE_INFINITY : closestDrop.get().squaredDistanceTo(pos) + 5; // + 5 to make the bot stop mining a bit less

            // We can't mine right now.
            if (mod.getExtraBaritoneSettings().isInteractionPaused()) {
                return closestDrop.map(Object.class::cast);
            }

            if (dropSq <= blockSq) {
                return closestDrop.map(Object.class::cast);
            } else {
                return closestBlock.map(Object.class::cast);
            }
        }

        @Override
        protected Vec3d getOriginPos(Vibrato mod) {
            return mod.getPlayer().getPos();
        }

        @Override
        protected Task onTick(Vibrato mod) {
            _mod = mod;
            if (_miningPos != null && !_progressChecker.check(mod)) {
                Debug.logMessage("Failed to mine block. Suggesting it may be unreachable.");
                mod.getBlockTracker().requestBlockUnreachable(_miningPos, 2);
                _blacklist.add(_miningPos);
                _miningPos = null;
                _progressChecker.reset();
            }
            return super.onTick(mod);
        }

        @Override
        protected Task getGoalTask(Object obj) {
            if (obj instanceof BlockPos newPos) {
                if (_miningPos == null || !_miningPos.equals(newPos)) {
                    _progressChecker.reset();
                }
                _miningPos = newPos;
                return new DestroyBlockTask(_miningPos);
            }
            if (obj instanceof ItemEntity itemEntity) {
                _miningPos = null;

                if (_mod.getItemStorage().getSlotThatCanFitInPlayerInventory(itemEntity.getStack(), false).or(() -> StorageHelper.getGarbageSlot(_mod)).isEmpty()) {
                    return new EnsureFreeInventorySlotTask();
                }

                return _pickupTask;
            }
            throw new UnsupportedOperationException("Shouldn't try to get the goal from object " + obj + " of type " + (obj != null ? obj.getClass().toString() : "(null object)"));
        }

        @Override
        protected boolean isValid(Vibrato mod, Object obj) {
            if (obj instanceof BlockPos b) {
                // A failed progress check blacklists the active pursuit as well as future searches.
                // Otherwise AbstractDoToClosestObjectTask keeps returning that same block forever.
                if (_blacklist.contains(b)) return false;
                if (!mod.getBlockTracker().blockIsValid(b, _blocks)) return false;
                if (WorldHelper.canBreak(mod, b)) return true;
                // Blocks in unloaded chunks are assumed to be breakable, so we only know for sure once the chunk loads.
                // Remember the rejection, otherwise we go right back to it once the chunk unloads and loop forever.
                if (mod.getChunkTracker().isChunkLoaded(b)) {
                    _blacklist.add(b);
                }
                return false;
            }
            if (obj instanceof ItemEntity drop) {
                if (!drop.isAlive() || !mod.getEntityTracker().isEntityReachable(drop)) return false;
                Item item = drop.getStack().getItem();
                for (ItemTarget target : _targets) {
                    if (target.matches(item)) return true;
                }
                return false;
            }
            return false;
        }

        @Override
        protected void onStart(Vibrato mod) {
            _progressChecker.reset();
            _miningPos = null;
        }

        @Override
        protected void onStop(Vibrato mod, Task interruptTask) {

        }

        @Override
        protected boolean isEqual(Task other) {
            if (other instanceof MineOrCollectTask task) {
                return Arrays.equals(task._blocks, _blocks) && Arrays.equals(task._targets, _targets);
            }
            return false;
        }

        @Override
        protected String toDebugString() {
            return "Mining or Collecting";
        }

        public boolean isMining() {
            return _miningPos != null;
        }

        public BlockPos miningPos() {
            return _miningPos;
        }
    }

}
