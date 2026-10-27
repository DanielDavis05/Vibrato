package dev.vibrato.util.progresscheck;

import dev.vibrato.Vibrato;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MovementProgressChecker {

    private final IProgressChecker<Vec3d> _distanceChecker;
    private final IProgressChecker<Double> _mineChecker;

    private BlockPos _lastBreakingBlock = null;

    public MovementProgressChecker(double distanceTimeout, double minDistance, double mineTimeout, double minMineProgress, int attempts) {
        _distanceChecker = new MovementWindowProgressChecker(distanceTimeout, minDistance, attempts);
        _mineChecker = new LinearProgressChecker(mineTimeout, minMineProgress);
    }

    public MovementProgressChecker(double distanceTimeout, double minDistance, double mineTimeout, double minMineProgress) {
        this(distanceTimeout, minDistance, mineTimeout, minMineProgress, 1);
    }

    public MovementProgressChecker(int attempts) {
        this(6, 0.1, 0.5, 0.001, attempts);
    }

    public MovementProgressChecker() {
        this(1);
    }

    public boolean check(Vibrato mod) {

        // Path calculation can legitimately keep the player still before movement starts.
        var pathing = mod.getClientBaritone().getPathingBehavior();
        if (!pathing.isPathing() && pathing.getInProgress().isPresent()) {
            reset();
            return true;
        }

        // Allow pause on eat
        if (mod.getFoodChain().isTryingToEat()) {
            _distanceChecker.reset();
            _mineChecker.reset();
        }

        // Completion can turn isBreakingBlock false before the next check. Count the
        // finished block even in that transition, but do not reward failed attempts.
        if (_lastBreakingBlock != null && WorldHelper.isAir(mod, _lastBreakingBlock)) {
            _distanceChecker.reset();
            _mineChecker.reset();
            _lastBreakingBlock = null;
        }
        if (mod.getControllerExtras().isBreakingBlock()) {
            BlockPos breakBlock = mod.getControllerExtras().getBreakingBlockPos();
            _lastBreakingBlock = breakBlock;
            _mineChecker.setProgress(mod.getControllerExtras().getBreakingBlockProgress());
            return !_mineChecker.failed();
        } else {
            _mineChecker.reset();
            _distanceChecker.setProgress(mod.getPlayer().getPos());
            return !_distanceChecker.failed();
        }
    }

    public void reset() {
        _distanceChecker.reset();
        _mineChecker.reset();
        _lastBreakingBlock = null;
    }

}
