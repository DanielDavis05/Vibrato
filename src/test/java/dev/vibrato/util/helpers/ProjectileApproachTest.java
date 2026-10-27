package dev.vibrato.util.helpers;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileApproachTest {
    private static Vec3d approach(Vec3d origin, Vec3d velocity, double gravity, Vec3d player) {
        return ProjectileHelper.calculateArrowClosestApproach(origin, velocity, gravity, player);
    }

    @Test
    void approachIsFutureBoundedAndRetainsCloseShots() {
        Vec3d target = Vec3d.ZERO;
        assertEquals(0, approach(new Vec3d(-10, 0, 0), new Vec3d(2, 0, 0), 0, target).x, 1e-9);
        assertEquals(10, approach(new Vec3d(10, 0, 0), new Vec3d(2, 0, 0), 0, target).x, 1e-9);
        assertEquals(-60, approach(new Vec3d(-100, 0, 0), new Vec3d(1, 0, 0), 0, target).x, 1e-9);
        assertEquals(-0.5, approach(new Vec3d(-0.5, 0, 0), new Vec3d(-1, 0, 0), 0, target).x, 1e-9);
    }

    @Test
    void heightUsesVelocitySignAndGravity() {
        Vec3d hit = approach(new Vec3d(-10, 5, 0), new Vec3d(2, 1, 0), 0.05, Vec3d.ZERO);
        assertEquals(9.375, hit.y, 1e-9);
        assertEquals(0, hit.x, 1e-9);
    }

    @Test
    void verticalAndStationaryShotsRemainFinite() {
        Vec3d hit = approach(new Vec3d(0, 20, 0), new Vec3d(0, -1, 0), 0, Vec3d.ZERO);
        assertEquals(0, hit.y, 1e-9);
        assertEquals(new Vec3d(1, 2, 3), approach(new Vec3d(1, 2, 3), Vec3d.ZERO, 0, Vec3d.ZERO));
    }

    @Test
    void encounterDependsOnEachCandidate() {
        Vec3d origin = new Vec3d(-10, 0, 0);
        Vec3d velocity = new Vec3d(1, 0, 0);
        assertEquals(0, approach(origin, velocity, 0, Vec3d.ZERO).x, 1e-9);
        assertEquals(5, approach(origin, velocity, 0, new Vec3d(5, 0, 1)).x, 1e-9);
    }
}
