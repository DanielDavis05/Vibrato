package dev.vibrato.tasks.speedrun;

import dev.vibrato.Vibrato;
import dev.vibrato.tasks.movement.CustomBaritoneGoalTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalRunAway;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;

public class DragonBreathTracker {
    private final HashSet<BlockPos> _breathBlocks = new HashSet<>();

    public void updateBreath(Vibrato mod) {
        _breathBlocks.clear();
        for (AreaEffectCloudEntity cloud : mod.getEntityTracker().getTrackedEntities(AreaEffectCloudEntity.class)) {
            for (BlockPos bad : WorldHelper.getBlocksTouchingBox(mod, cloud.getBoundingBox())) {
                _breathBlocks.add(bad);
            }
        }
    }

    public boolean isTouchingDragonBreath(BlockPos pos) {
        return _breathBlocks.contains(pos);
    }

    public Task getRunAwayTask() {
        return new RunAwayFromDragonsBreathTask();
    }

    private class RunAwayFromDragonsBreathTask extends CustomBaritoneGoalTask {

        @Override
        protected void onStart(Vibrato mod) {
            super.onStart(mod);
            mod.getBehaviour().push();
            // Encourage placing of all blocks!
            mod.getBehaviour().setBlockPlacePenalty(0);
            mod.getBehaviour().setBlockBreakAdditionalPenalty(10); // Normally 2

            // do NOT ever wander
            _checker = new MovementProgressChecker(999999);
        }

        @Override
        protected void onStop(Vibrato mod, Task interruptTask) {
            super.onStop(mod, interruptTask);
            mod.getBehaviour().pop();
        }

        @Override
        protected Goal newGoal(Vibrato mod) {
            return new GoalRunAway(2, _breathBlocks.toArray(BlockPos[]::new));
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof RunAwayFromDragonsBreathTask;
        }

        @Override
        protected String toDebugString() {
            return "ESCAPE Dragons Breath";
        }
    }
}
