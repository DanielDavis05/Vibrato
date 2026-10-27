package dev.vibrato.tasks.movement;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.baritone.GoalChunk;
import dev.vibrato.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.Goal;
import net.minecraft.util.math.ChunkPos;

public class GetToChunkTask extends CustomBaritoneGoalTask {

    private final ChunkPos _pos;

    public GetToChunkTask(ChunkPos pos) {
        // Override checker to be more lenient, as we are traversing entire chunks here.
        _checker = new MovementProgressChecker(20, 0.5, 5, 0.001, 3);
        _pos = pos;
    }

    @Override
    protected Goal newGoal(Vibrato mod) {
        return new GoalChunk(_pos);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof GetToChunkTask task) {
            return task._pos.equals(_pos);
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "Get to chunk: " + _pos.toString();
    }
}
