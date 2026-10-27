package dev.vibrato.tasks.construction;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.InteractWithBlockTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.ItemTarget;
import baritone.api.utils.input.Input;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Given a block position with fire in it, extinguish the fire at that position
 */
public class PutOutFireTask extends Task {

    private final BlockPos _firePosition;

    public PutOutFireTask(BlockPos firePosition) {
        _firePosition = firePosition;
    }

    @Override
    protected void onStart(Vibrato mod) {

    }

    @Override
    protected Task onTick(Vibrato mod) {
        return new InteractWithBlockTask(ItemTarget.EMPTY, Direction.UP, _firePosition.down(), Input.CLICK_LEFT, false, false);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    public boolean isFinished(Vibrato mod) {
        BlockState s = mod.getWorld().getBlockState(_firePosition);
        return (s.getBlock() != Blocks.FIRE && s.getBlock() != Blocks.SOUL_FIRE);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof PutOutFireTask task) {
            return (task._firePosition.equals(_firePosition));
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Putting out fire at " + _firePosition;
    }
}
