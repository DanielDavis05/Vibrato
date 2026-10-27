package dev.vibrato.util.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public class GoalFollowEntity implements Goal {

    private final Supplier<Vec3d> _position;
    private final double _closeEnoughDistance;

    public GoalFollowEntity(Entity entity, double closeEnoughDistance) {
        this(entity::getPos, closeEnoughDistance);
    }

    GoalFollowEntity(Supplier<Vec3d> position, double closeEnoughDistance) {
        _position = position;
        _closeEnoughDistance = Math.max(0, closeEnoughDistance);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return isInGoal(x, y, z, _position.get());
    }

    private boolean isInGoal(int x, int y, int z, Vec3d target) {
        if (x == floor(target.x) && y == floor(target.y) && z == floor(target.z)) return true;
        double dx = x + 0.5 - target.x;
        double dy = y - target.y;
        double dz = z + 0.5 - target.z;
        return dx * dx + dy * dy + dz * dz <= _closeEnoughDistance * _closeEnoughDistance;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        Vec3d target = _position.get();
        if (isInGoal(x, y, z, target)) return 0;

        // The radius lies inside this axis-aligned box. Distance to the larger box is a
        // conservative bound, and the exact target cell remains an alternative goal.
        double dx = outsideRadius(x + 0.5 - target.x);
        double dy = outsideRadius(y - target.y);
        double dz = outsideRadius(z + 0.5 - target.z);
        double near = GoalBlock.calculate(dx, (int) dy, dz);
        double sameCell = GoalBlock.calculate(x - floor(target.x), y - floor(target.y), z - floor(target.z));
        return Math.min(near, sameCell);
    }

    @Override
    public String toString() {
        Vec3d target = _position.get();
        return String.format(java.util.Locale.ROOT, "GoalFollowEntity{target=(%.3f,%.3f,%.3f),radius=%.3f}",
                target.x, target.y, target.z, _closeEnoughDistance);
    }

    private double outsideRadius(double offset) {
        return Math.copySign(Math.max(0, Math.abs(offset) - _closeEnoughDistance), offset);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
