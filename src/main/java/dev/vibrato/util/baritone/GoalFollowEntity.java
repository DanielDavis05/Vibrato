package dev.vibrato.util.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class GoalFollowEntity implements Goal {

    // Ground collision coordinates can sit just below a whole-block surface.
    private static final double GROUND_EPSILON = 0.01;

    private final Supplier<Vec3d> _position;
    private final BooleanSupplier _groundedItem;
    private final double _closeEnoughDistance;

    public GoalFollowEntity(Entity entity, double closeEnoughDistance) {
        this(entity::getPos, closeEnoughDistance, () -> entity instanceof ItemEntity && entity.isOnGround());
    }

    GoalFollowEntity(Supplier<Vec3d> position, double closeEnoughDistance) {
        this(position, closeEnoughDistance, () -> false);
    }

    GoalFollowEntity(Supplier<Vec3d> position, double closeEnoughDistance, BooleanSupplier groundedItem) {
        _position = position;
        _groundedItem = groundedItem;
        _closeEnoughDistance = Math.max(0, closeEnoughDistance);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return isInGoal(x, y, z, _position.get(), _groundedItem.getAsBoolean());
    }

    private boolean isInGoal(int x, int y, int z, Vec3d target, boolean groundedItem) {
        int targetY = targetY(target, groundedItem);
        if (groundedItem && y < targetY) return false;
        if (x == floor(target.x) && y == targetY && z == floor(target.z)) return true;
        double dx = x + 0.5 - target.x;
        double dy = y - target.y;
        double dz = z + 0.5 - target.z;
        return dx * dx + dy * dy + dz * dz <= _closeEnoughDistance * _closeEnoughDistance;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        Vec3d target = _position.get();
        boolean groundedItem = _groundedItem.getAsBoolean();
        if (isInGoal(x, y, z, target, groundedItem)) return 0;

        // The radius lies inside this axis-aligned box. Distance to the larger box is a
        // conservative bound, and the exact target cell remains an alternative goal.
        double dx = outsideRadius(x + 0.5 - target.x);
        double dy = outsideRadius(y - target.y);
        int targetY = targetY(target, groundedItem);
        if (groundedItem && y < targetY) dy = Math.min(dy, y - targetY);
        double dz = outsideRadius(z + 0.5 - target.z);
        double near = GoalBlock.calculate(dx, (int) dy, dz);
        double sameCell = GoalBlock.calculate(x - floor(target.x), y - targetY, z - floor(target.z));
        return Math.min(near, sameCell);
    }

    private double outsideRadius(double offset) {
        return Math.copySign(Math.max(0, Math.abs(offset) - _closeEnoughDistance), offset);
    }

    private static int targetY(Vec3d target, boolean groundedItem) {
        return groundedItem ? floor(target.y + GROUND_EPSILON) : floor(target.y);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
