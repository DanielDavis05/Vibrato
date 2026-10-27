package dev.vibrato.tasks.build;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.Vibrato;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Builds the obsidian portal frame, then lights it with flint and steel from the bottom interior cell. */
public class BuildPortalFrameTask extends Task {

    private static final int MAX_LIGHT_ATTEMPTS = 4;

    private final BuildBlueprintTask _frame = new BuildBlueprintTask(Blueprints.portalFrame(), null, Palette.OAK);
    private int _lightAttempts = 0;
    private boolean _lit = false;
    private boolean _done = false;
    private Task _current;

    public boolean isLit() {
        return _lit;
    }

    @Override
    protected void onStart(Vibrato mod) {
        _lightAttempts = 0;
        _lit = false;
        _done = false;
        _current = null;
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (!_frame.isFinished(mod)) {
            setDebugState("Building the portal frame");
            return _frame;
        }
        if (!_frame.isReady() || _frame.origin().isEmpty()) {
            _done = true;
            return null;
        }
        BlockPos interior = _frame.origin().get().add(1, 2, 0);
        if (mod.getWorld().getBlockState(interior).getBlock() == Blocks.NETHER_PORTAL) {
            _lit = true;
            _done = true;
            return null;
        }
        if (_current != null && _current.isActive() && !_current.isFinished(mod)) {
            return _current;
        }
        if (_lightAttempts >= MAX_LIGHT_ATTEMPTS) {
            _done = true;
            return null;
        }
        if (!mod.getItemStorage().hasItem(Items.FLINT_AND_STEEL)) {
            setDebugState("Getting flint and steel");
            _current = TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
            return _current;
        }
        _lightAttempts++;
        setDebugState("Lighting the portal");
        _current = new InteractWithBlockTask(new ItemTarget(Items.FLINT_AND_STEEL, 1), Direction.UP, interior.down(), true);
        return _current;
    }

    @Override
    public boolean isFinished(Vibrato mod) {
        return _done;
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {}

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BuildPortalFrameTask;
    }

    @Override
    protected String toDebugString() {
        return "Building a portal frame";
    }
}
