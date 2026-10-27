package dev.vibrato.tasks.explore;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.LocateStrongholdCoordinatesTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Finds a generated structure and logs it as {@code structure:<id>}.
 * <ul>
 *   <li>desert_temple: the existing pressure-plate and sandstone signature.</li>
 *   <li>stronghold: the existing eye-of-ender triangulation.</li>
 *   <li>jungle_temple, shipwreck, ruined_portal, mineshaft: a heuristic, the first of their signature blocks seen
 *   (mossy cobblestone, barrel, crying obsidian, rail).</li>
 * </ul>
 * Limits: the heuristics can match other structures or natural blocks, so a logged spot is a lead, not a confirmed
 * structure. Exploration wanders within a fixed radius per attempt and the activity budget caps the total.
 */
public class FindStructureTask extends Task {

    private static final float WANDER_DISTANCE = 200;

    private final String _structure;
    private LocateStrongholdCoordinatesTask _stronghold;
    private Block[] _signature;
    private BlockPos _found;
    private boolean _unknown;

    public FindStructureTask(String structure) {
        _structure = structure;
        _unknown = !ExplorableStructures.isSupported(structure);
    }

    @Override
    protected void onStart(Vibrato mod) {
        _found = null;
        _signature = signatureFor(_structure);
        if (_signature.length > 0) {
            mod.getBlockTracker().trackBlock(_signature);
        }
        if ("stronghold".equals(_structure)) {
            _stronghold = new LocateStrongholdCoordinatesTask(12);
        }
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_unknown) {
            setDebugState("Unknown structure " + _structure);
            return null;
        }
        if (_found == null) {
            _found = locate(mod);
            if (_found != null && DiscoveryLog.add("structure:" + _structure, _found)) {
                setDebugState("Logged " + _structure);
            }
        }
        if (_found != null) {
            return null;
        }
        if (_stronghold != null) {
            setDebugState("Triangulating the stronghold");
            return _stronghold;
        }
        setDebugState("Exploring for " + _structure);
        return new TimeoutWanderTask(WANDER_DISTANCE, true);
    }

    private BlockPos locate(Vibrato mod) {
        if ("desert_temple".equals(_structure)) {
            return WorldHelper.getADesertTemple(mod);
        }
        if (_stronghold != null) {
            return _stronghold.getStrongholdCoordinates().orElse(null);
        }
        return mod.getBlockTracker().getNearestTracking(_signature).orElse(null);
    }

    private static Block[] signatureFor(String structure) {
        return switch (structure) {
            case "jungle_temple" -> new Block[]{Blocks.MOSSY_COBBLESTONE};
            case "shipwreck" -> new Block[]{Blocks.BARREL};
            case "ruined_portal" -> new Block[]{Blocks.CRYING_OBSIDIAN};
            case "mineshaft" -> new Block[]{Blocks.RAIL};
            default -> new Block[0];
        };
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        if (_signature != null && _signature.length > 0) {
            mod.getBlockTracker().stopTracking(_signature);
        }
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _unknown || _found != null;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof FindStructureTask task && task._structure.equals(_structure);
    }

    @Override
    protected String toDebugString() {
        return "Finding structure " + _structure;
    }
}
