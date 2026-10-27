package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

public class LocateDesertTempleTask extends Task {

    private BlockPos _finalPos;

    @Override
    protected void onStart(Vibrato mod) {
        // Track desert pyramid blocks
        mod.getBlockTracker().trackBlock(Blocks.STONE_PRESSURE_PLATE);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        BlockPos desertTemplePos = WorldHelper.getADesertTemple(mod);
        if (desertTemplePos != null) {
            _finalPos = desertTemplePos.up(14);
        }
        if (_finalPos != null) {
            setDebugState("Going to found desert temple");
            return new GetToBlockTask(_finalPos, false);
        }
        return new SearchWithinBiomeTask(BiomeKeys.DESERT);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(Blocks.STONE_PRESSURE_PLATE);
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof LocateDesertTempleTask;
    }

    @Override
    protected String toDebugString() {
        return "Searchin' for temples";
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return mod.getPlayer().getBlockPos().equals(_finalPos);
    }
}
