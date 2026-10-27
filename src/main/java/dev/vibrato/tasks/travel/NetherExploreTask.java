package dev.vibrato.tasks.travel;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.explore.DiscoveryLog;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.tasks.movement.EscapeFromLavaTask;
import dev.vibrato.tasks.movement.GetToBlockTask;
import dev.vibrato.tasks.movement.GetToYTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.Dimension;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Searches the nether for a fortress or bastion, or mines ancient debris down at its layer. Structures are found by their
 * blocks in the block tracker while the bot explores; ancient debris is mined through the existing catalogue task after
 * walking down to the requested height.
 * <p>
 * Limits: structure search wanders with the explore process and relies on Baritone's own pathing, so it prefers the
 * paths Baritone plans rather than hand-picked safe routes. Lava is handled only by the existing escape task. The task
 * is bounded by the activity budget, and a structure is only logged when it is actually seen.
 */
public class NetherExploreTask extends Task {

    private static final double FOUND_RANGE = 12;
    private static final int DEBRIS_Y_TOLERANCE = 4;

    public enum NetherTarget {
        FORTRESS("fortress", Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_FENCE),
        BASTION("bastion", Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE),
        ANCIENT_DEBRIS("ancient_debris");

        private final String id;
        private final Block[] blocks;

        NetherTarget(String id, Block... blocks) {
            this.id = id;
            this.blocks = blocks;
        }

        public String id() {
            return id;
        }

        public static Optional<NetherTarget> fromId(String id) {
            for (NetherTarget target : values()) {
                if (target.id.equals(id)) {
                    return Optional.of(target);
                }
            }
            return Optional.empty();
        }
    }

    private final NetherTarget _target;
    private final int _debrisY;
    private BlockPos _found;

    /** @param debrisY the layer to mine ancient debris at; ignored for structures */
    public NetherExploreTask(NetherTarget target, int debrisY) {
        _target = target;
        _debrisY = debrisY;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _found = null;
        if (_target.blocks.length > 0) {
            mod.getBlockTracker().trackBlock(_target.blocks);
        }
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (WorldHelper.getCurrentDimension() != Dimension.NETHER) {
            setDebugState("Going to the nether");
            return new DefaultGoToDimensionTask(Dimension.NETHER);
        }
        if (mod.getPlayer().isInLava() || mod.getPlayer().isOnFire()) {
            setDebugState("Escaping lava");
            return new EscapeFromLavaTask();
        }
        if (_target == NetherTarget.ANCIENT_DEBRIS) {
            return mineDebris(mod);
        }
        return searchStructure(mod);
    }

    private Task mineDebris(Vibrato mod) {
        if (mod.getItemStorage().hasItem(Items.ANCIENT_DEBRIS)) {
            setDebugState("Have ancient debris");
            return null;
        }
        if (Math.abs(mod.getPlayer().getY() - _debrisY) > DEBRIS_Y_TOLERANCE) {
            setDebugState("Going down to y=" + _debrisY);
            return new GetToYTask(_debrisY, Dimension.NETHER);
        }
        setDebugState("Mining ancient debris");
        return TaskCatalogue.getItemTask("ancient_debris", 1);
    }

    private Task searchStructure(Vibrato mod) {
        if (_found == null) {
            _found = mod.getBlockTracker().getNearestTracking(_target.blocks).orElse(null);
            if (_found != null && DiscoveryLog.add("structure:nether_" + _target.id(), _found)) {
                setDebugState("Spotted a " + _target.id());
            }
        }
        if (_found != null) {
            setDebugState("Going to the " + _target.id());
            return new GetToBlockTask(_found);
        }
        setDebugState("Exploring the nether for a " + _target.id());
        return new TimeoutWanderTask(true);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        if (_target.blocks.length > 0) {
            mod.getBlockTracker().stopTracking(_target.blocks);
        }
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        if (_target == NetherTarget.ANCIENT_DEBRIS) {
            return mod.getItemStorage().hasItem(Items.ANCIENT_DEBRIS);
        }
        return _found != null && WorldHelper.inRangeXZ(mod.getPlayer(), _found, FOUND_RANGE);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof NetherExploreTask task && task._target == _target && task._debrisY == _debrisY;
    }

    @Override
    protected String toDebugString() {
        return "Nether search for " + _target.id();
    }
}
