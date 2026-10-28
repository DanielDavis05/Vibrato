package dev.vibrato.tasks.construction.compound;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasks.construction.ClearLiquidTask;
import dev.vibrato.tasks.construction.DestroyBlockTask;
import dev.vibrato.tasks.construction.PlaceObsidianBucketTask;
import dev.vibrato.tasks.movement.PickupDroppedItemTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import dev.vibrato.util.time.TimerGame;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Build a nether portal by casting each piece with water + lava.
 *
 * Currently the most reliable portal building method.
 */
public class ConstructNetherPortalBucketTask extends Task {

    // Order here matters
    private static final Vec3i[] PORTAL_FRAME = new Vec3i[]{
            // Left side
            new Vec3i(0, 0, -1),
            new Vec3i(0, 1, -1),
            new Vec3i(0, 2, -1),
            // Right side
            new Vec3i(0, 0, 2),
            new Vec3i(0, 1, 2),
            new Vec3i(0, 2, 2),
            // Bottom
            new Vec3i(0, -1, 0),
            new Vec3i(0, -1, 1),
            // Top
            new Vec3i(0, 3, 0),
            new Vec3i(0, 3, 1)
    };

    private static final Vec3i[] PORTAL_INTERIOR = new Vec3i[]{
            new Vec3i(0, 0, 0),
            new Vec3i(0, 1, 0),
            new Vec3i(0, 2, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(0, 1, 1),
            new Vec3i(0, 2, 1)
    };

    // The "portalable" region includes the portal (1 x 6 x 4 structure) and an outer buffer for its construction and water bullshit.
    // The "portal origin relative to region" corresponds to the portal origin with respect to the "portalable" region (see _portalOrigin).
    // This can only really be explained visually, sorry!
    private static final Vec3i PORTALABLE_REGION_SIZE = new Vec3i(4, 6, 6);
    private static final Vec3i PORTAL_ORIGIN_RELATIVE_TO_REGION = new Vec3i(1, 0, 2);
    private final TimerGame _lavaSearchTimer = new TimerGame(5);
    private final TimerGame _initialLavaSearchGrace = new TimerGame(0.5);
    private final MovementProgressChecker _progressChecker = new MovementProgressChecker(5);
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(25);
    // Stored here to cache lava blacklist
    private final Task _collectLavaTask = TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
    private final TimerGame _refreshTimer = new TimerGame(11);
    private BlockPos _portalOrigin = null;
    private int _stonePickaxeGoal;

    //private BlockPos _currentLavaTarget = null;
    private BlockPos _currentDestroyTarget = null;
    // Persists across interruptions: do not restart the grace period on resume.
    private boolean _initialLavaSearchStarted = false;
    private boolean _initialLavaSearchFinished = false;

    @Override
    protected void onStart(Vibrato mod) {

        _currentDestroyTarget = null;

        mod.getBlockTracker().trackBlock(Blocks.LAVA);
        mod.getBehaviour().push();

        // Avoid breaking portal frame if we're obsidian.
        // Also avoid placing on the lava + water
        // Also avoid breaking the cast frame
        mod.getBehaviour().avoidBlockBreaking(block -> {
            if (_portalOrigin != null) {
                // Don't break frame
                for (Vec3i framePosRelative : PORTAL_FRAME) {
                    BlockPos framePos = _portalOrigin.add(framePosRelative);
                    if (block.equals(framePos)) {
                        return mod.getWorld().getBlockState(framePos).getBlock() == Blocks.OBSIDIAN;
                    }
                }
            }
            return false;
        });

        // Protect some used items
        mod.getBehaviour().addProtectedItems(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.FLINT_AND_STEEL, Items.FIRE_CHARGE, Items.IRON_INGOT);

        _progressChecker.reset();
    }

    @Override
    protected Task onTick(Vibrato mod) {

        if (_refreshTimer.elapsed()) {
            Debug.logInternal("Duct tape: Refreshing inventory again just in case");
            _refreshTimer.reset();
            mod.getSlotHandler().refreshInventory();
        }

        // Portal preparation can lead deep underground after earlier tools
        // break. Commit to a real replacement before starting another long
        // lava approach; keep stronger usable pickaxes instead of downgrading.
        int threshold = mod.getClientBaritoneSettings().itemSaver.value
                ? mod.getClientBaritoneSettings().itemSaverThreshold.value : 0;
        boolean hasUsablePickaxe = mod.getPlayer().getInventory().main.stream()
                .anyMatch(stack -> PortalToolPolicy.usable(stack, threshold));
        boolean frameComplete = _portalOrigin != null && Arrays.stream(PORTAL_FRAME)
                .allMatch(offset -> mod.getWorld().getBlockState(_portalOrigin.add(offset)).isOf(Blocks.OBSIDIAN));
        if (PortalToolPolicy.needsTool(hasUsablePickaxe, frameComplete)) {
            if (_stonePickaxeGoal == 0) {
                _stonePickaxeGoal = mod.getItemStorage().getItemCountInventoryOnly(Items.STONE_PICKAXE) + 1;
            }
            setDebugState("Preparing a usable stone pickaxe");
            _progressChecker.reset();
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, _stonePickaxeGoal);
        }
        _stonePickaxeGoal = 0;

        //If too far, reset.
        if (_portalOrigin != null && !_portalOrigin.isWithinDistance(mod.getPlayer().getPos(), 2000)) {
            _portalOrigin = null;
            _currentDestroyTarget = null;
        }

        if (_currentDestroyTarget != null) {
            if (!WorldHelper.isSolid(mod, _currentDestroyTarget)) {
                _currentDestroyTarget = null;
            } else {
                return new DestroyBlockTask(_currentDestroyTarget);
            }
        }


        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            setDebugState("Wandering before retrying...");
            _progressChecker.reset();
            return _wanderTask;
        }


        // Get bucket if we don't have one.
        int bucketCount = mod.getItemStorage().getItemCount(Items.BUCKET, Items.LAVA_BUCKET, Items.WATER_BUCKET);
        if (bucketCount < 2) {
            setDebugState("Getting buckets");
            _progressChecker.reset();
            // Recover an existing bucket before deciding how much iron to gather.
            if (mod.getEntityTracker().itemDropped(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
                return new PickupDroppedItemTask(new ItemTarget(new Item[]{Items.WATER_BUCKET, Items.LAVA_BUCKET}, 1), true);
            }
            boolean hasIgniter = mod.getItemStorage().hasItem(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
            int ironGoal = PortalIronPlan.ingotGoal(bucketCount, hasIgniter);
            if (mod.getItemStorage().getItemCount(Items.IRON_INGOT) < ironGoal) {
                setDebugState("Collecting " + ironGoal + " iron ingots for buckets and ignition");
                return TaskCatalogue.getItemTask(Items.IRON_INGOT, ironGoal);
            }
            // If we have lava/water, get the inverse; otherwise craft the buckets.
            if (mod.getItemStorage().hasItem(Items.LAVA_BUCKET)) {
                return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
            } else if (mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                return TaskCatalogue.getItemTask(Items.LAVA_BUCKET, 1);
            }
            return TaskCatalogue.getItemTask(Items.BUCKET, 2);
        }

        // Get flint & steel if we don't have one
        if (!mod.getItemStorage().hasItem(Items.FLINT_AND_STEEL) && !mod.getItemStorage().hasItem(Items.FIRE_CHARGE)) {
            setDebugState("Getting flint & steel");
            _progressChecker.reset();
            return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
        }

        boolean needsToLookForPortal = _portalOrigin == null;
        if (needsToLookForPortal) {
            _progressChecker.reset();
            // Get water before searching, just for convenience.
            if (!mod.getItemStorage().hasItem(Items.WATER_BUCKET)) {
                setDebugState("Getting water");
                _progressChecker.reset();
                return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
            }

            boolean foundSpot = false;
            boolean firstLookup = !_initialLavaSearchStarted;
            if (firstLookup) {
                _initialLavaSearchStarted = true;
                _initialLavaSearchGrace.reset();
            }
            boolean initialSearch = !_initialLavaSearchFinished;
            boolean graceExpired = initialSearch && _initialLavaSearchGrace.elapsed();

            if (initialSearch || _lavaSearchTimer.elapsed()) {
                if (firstLookup || !initialSearch || graceExpired) {
                    Debug.logMessage("(Searching for lava lake with portalable spot nearby...)");
                }
                if (!initialSearch || graceExpired) {
                    _initialLavaSearchFinished = true;
                    _lavaSearchTimer.reset();
                }
                BlockPos lavaPos = findLavaLake(mod, mod.getPlayer().getBlockPos());
                if (lavaPos != null) {
                    // We have a lava lake, set our portal origin!
                    BlockPos foundPortalRegion = getPortalableRegion(mod, lavaPos, mod.getPlayer().getBlockPos(), new Vec3i(-1, 0, 0), PORTALABLE_REGION_SIZE, 20);
                    if (foundPortalRegion == null) {
                        if (!initialSearch || graceExpired) {
                            Debug.logWarning("Failed to find portalable region nearby. Consider increasing the search timeout range");
                        }
                    } else {
                        _portalOrigin = foundPortalRegion.add(PORTAL_ORIGIN_RELATIVE_TO_REGION);
                        _initialLavaSearchFinished = true;
                        _lavaSearchTimer.reset();
                        foundSpot = true;
                    }
                } else if (!initialSearch || graceExpired) {
                    Debug.logMessage("(lava lake not found)");
                }
            }

            if (!foundSpot) {
                if (initialSearch && !graceExpired) {
                    setDebugState("Waiting briefly for the initial lava scan");
                    return null;
                }
                setDebugState("(timeout: Looking for lava lake)");
                return new TimeoutWanderTask(100);
            }
        }

        // We have a portal, now build it.
        for (Vec3i framePosRelative : PORTAL_FRAME) {
            BlockPos framePos = _portalOrigin.add(framePosRelative);
            Block frameBlock = mod.getWorld().getBlockState(framePos).getBlock();
            if (frameBlock == Blocks.OBSIDIAN) {
                // Already satisfied, clear water above if need be.
                BlockPos waterCheck = framePos.up();
                if (mod.getWorld().getBlockState(waterCheck).getBlock() == Blocks.WATER && WorldHelper.isSourceBlock(mod, waterCheck, true)) {
                    setDebugState("Clearing water from cast");
                    return new ClearLiquidTask(waterCheck);
                }
                continue;
            }

            // Get lava early so placing it is faster
            if (!mod.getItemStorage().hasItem(Items.LAVA_BUCKET) && frameBlock != Blocks.LAVA) {
                setDebugState("Collecting lava");
                _progressChecker.reset();
                return _collectLavaTask;
            }

            // We need to place obsidian here.
            return new PlaceObsidianBucketTask(framePos);

        }

        // Now, clear the inside.
        for (Vec3i offs : PORTAL_INTERIOR) {
            BlockPos p = _portalOrigin.add(offs);
            assert MinecraftClient.getInstance().world != null;
            if (!MinecraftClient.getInstance().world.getBlockState(p).isAir()) {
                setDebugState("Clearing inside of portal");
                _currentDestroyTarget = p;
                return null;
                //return new DestroyBlockTask(p);
            }
        }

        setDebugState("Flinting and Steeling");

        // Flint and steel it baby
        return new InteractWithBlockTask(new ItemTarget(new Item[]{Items.FLINT_AND_STEEL, Items.FIRE_CHARGE}, 1), Direction.UP, _portalOrigin.down(), true);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.LAVA);
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ConstructNetherPortalBucketTask;
    }

    @Override
    protected String toDebugString() {
        return "Construct Nether Portal";
    }

    private BlockPos findLavaLake(Vibrato mod, BlockPos playerPos) {
        HashSet<BlockPos> alreadyExplored = new HashSet<>();

        double nearestSqDistance = Double.POSITIVE_INFINITY;
        BlockPos nearestLake = null;
        // The global tracker cache is capped and may omit a visible surface lake.
        // Seed it with the nearest loaded, non-blacklisted local source before flood checking.
        List<BlockPos> candidates = new ArrayList<>();
        mod.getBlockTracker().getNearestWithinRange(Vec3d.of(playerPos), 16,
                pos -> WorldHelper.isSourceBlock(mod, pos, true), Blocks.LAVA).ifPresent(candidates::add);
        candidates.addAll(mod.getBlockTracker().getKnownLocations(Blocks.LAVA));
        for (BlockPos pos : candidates) {
            if (alreadyExplored.contains(pos)) continue;
            double sqDist = playerPos.getSquaredDistance(pos);
            if (sqDist < nearestSqDistance) {
                int depth = getNumberOfBlocksAdjacent(alreadyExplored, pos);
                if (depth != 0) {
                    Debug.logMessage("Found with depth " + depth);
                    if (depth >= 12) {
                        nearestSqDistance = sqDist;
                        nearestLake = pos;
                    }
                }
            }
        }

        return nearestLake;
    }

    // Used to flood-scan for blocks of lava.
    private int getNumberOfBlocksAdjacent(HashSet<BlockPos> alreadyExplored, BlockPos origin) {
        // Base case: We already explored this one
        if (alreadyExplored.contains(origin)) return 0;
        alreadyExplored.add(origin);

        // Base case: We hit a non-full lava block.
        assert MinecraftClient.getInstance().world != null;
        BlockState s = MinecraftClient.getInstance().world.getBlockState(origin);
        if (s.getBlock() != Blocks.LAVA) {
            return 0;
        } else {
            // We may not be a full lava block
            if (!s.getFluidState().isStill()) return 0;
            int level = s.getFluidState().getLevel();
            //Debug.logMessage("TEST LEVEL: " + level + ", " + height);
            // Only accept FULL SOURCE BLOCKS
            if (level != 8) return 0;
        }

        BlockPos[] toCheck = new BlockPos[]{origin.north(), origin.south(), origin.east(), origin.west(), origin.up(), origin.down()};

        int bonus = 0;
        for (BlockPos check : toCheck) {
            // This block is new! Explore out from it.
            bonus += getNumberOfBlocksAdjacent(alreadyExplored, check);
        }

        return bonus + 1;
    }

    // Get a region that a portal can fit into
    private BlockPos getPortalableRegion(Vibrato mod, BlockPos lava, BlockPos playerPos, Vec3i sizeOffset, Vec3i sizeAllocation, int timeoutRange) {
        Vec3i[] directions = new Vec3i[]{new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(0, 0, -1)};

        double minDistanceToPlayer = Double.POSITIVE_INFINITY;
        BlockPos bestPos = null;

        for (Vec3i direction : directions) {

            // Inch along
            for (int offs = 1; offs < timeoutRange; ++offs) {

                Vec3i offset = new Vec3i(direction.getX() * offs, direction.getY() * offs, direction.getZ() * offs);

                boolean found = true;
                boolean solidFound = false;
                // check for collision with lava in box
                // We have an extra buffer to make sure we never break a block NEXT to lava.
                moveAlongLine:
                for (int dx = -1; dx < sizeAllocation.getX() + 1; ++dx) {
                    for (int dz = -1; dz < sizeAllocation.getZ() + 1; ++dz) {
                        for (int dy = -1; dy < sizeAllocation.getY(); ++dy) {
                            BlockPos toCheck = lava.add(offset).add(sizeOffset).add(dx, dy, dz);
                            assert MinecraftClient.getInstance().world != null;
                            BlockState state = MinecraftClient.getInstance().world.getBlockState(toCheck);
                            if (state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.BEDROCK) {
                                found = false;
                                break moveAlongLine;
                            }
                            // Also check for at least 1 solid block for us to place on...
                            if (dy <= 1 && !solidFound && WorldHelper.isSolid(mod, toCheck)) {
                                solidFound = true;
                            }
                        }
                    }
                }
                // Check for solid ground at least somewhere
                if (!solidFound) {
                    break;
                }

                if (found) {
                    BlockPos foundBoxCorner = lava.add(offset).add(sizeOffset);
                    double sqDistance = foundBoxCorner.getSquaredDistance(playerPos);
                    if (sqDistance < minDistanceToPlayer) {
                        minDistanceToPlayer = sqDistance;
                        bestPos = foundBoxCorner;
                    }
                    break;
                }
            }

        }

        return bestPos;
    }
}
