package dev.vibrato.human.wire;

import dev.vibrato.human.motion.HumanProfile;
import dev.vibrato.human.motion.Population;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShapingLayerTest {

    private static final PathView.Node A = new PathView.Node(0.5, 64, 0.5);
    private static final PathView FLAT = new PathView(MovementKind.TRAVERSE,
            List.of(A, new PathView.Node(1.5, 64, 0.5), new PathView.Node(2.5, 64, 0.5)),
            0.5, 64, 0.5, 0.2, 0, 0, true);
    private static final KeyAdapter.Flags WALK = new KeyAdapter.Flags(true, false, true, false, false, true, false);

    private static HumanProfile busyProfile() {
        HumanProfile p = HumanProfile.typical(Population.DEFAULT);
        return new HumanProfile(p.reactionMedianTicks(), p.jumpTimingBiasTicks(), p.jumpTimingSigmaTicks(), 0,
                0.3, p.keyHoldMedianTicks(), p.keyHoldSigma(), 0.1, p.lateralSigma(), p.lateralReversion(),
                p.lookAheadBlocks(), p.primaryFraction(), p.correctionCountMean(), p.overshootScale(),
                p.turnPeakSpeedScale());
    }

    private static List<KeyAdapter.Flags> run(long seed, Bypass bypass, int ticks) {
        ShapingLayer layer = new ShapingLayer(seed, busyProfile());
        List<KeyAdapter.Flags> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            out.add(layer.apply(WALK, FLAT, bypass));
        }
        return out;
    }

    @Test
    void protectedMovementKeepsLateralSteeringAndJumpExactly() {
        ShapingLayer layer = new ShapingLayer(7, busyProfile());
        KeyAdapter.Flags precision = new KeyAdapter.Flags(true, false, true, false, true, true, false);
        for (MovementKind kind : MovementKind.values()) {
            if (kind == MovementKind.TRAVERSE || kind == MovementKind.DIAGONAL) continue;
            var view = new PathView(kind, FLAT.nodes(), .5, 64, .5, .2, 0, 0, true);
            for (int i = 0; i < 200; i++) assertEquals(precision, layer.apply(precision, view, Bypass.NONE));
        }
        var airborne = new PathView(MovementKind.TRAVERSE, FLAT.nodes(), .5, 65, .5, .2, .1, 0, false);
        for (int i = 0; i < 200; i++) assertEquals(precision, layer.apply(precision, airborne, Bypass.NONE));
        for (int i = 0; i < 200; i++) assertEquals(precision, layer.apply(precision, FLAT, Bypass.NONE));
    }

    @Test
    void shapingChangesSomeKeysAndIsDeterministicPerSeed() {
        List<KeyAdapter.Flags> first = run(7, Bypass.NONE, 400);
        assertEquals(first, run(7, Bypass.NONE, 400));
        assertTrue(first.stream().anyMatch(f -> !f.equals(WALK)), "expected some shaping");
    }

    @Test
    void everyBypassReasonPassesKeysThroughUnchanged() {
        Bypass[] all = {
                new Bypass(true, false, false, false, false, false, false, false, false),
                new Bypass(false, true, false, false, false, false, false, false, false),
                new Bypass(false, false, true, false, false, false, false, false, false),
                new Bypass(false, false, false, true, false, false, false, false, false),
                new Bypass(false, false, false, false, true, false, false, false, false),
                new Bypass(false, false, false, false, false, true, false, false, false),
                new Bypass(false, false, false, false, false, false, true, false, false),
                new Bypass(false, false, false, false, false, false, false, true, false),
                new Bypass(false, false, false, false, false, false, false, false, true),
        };
        for (Bypass bypass : all) {
            assertTrue(bypass.any());
            assertNotEquals("none", bypass.reason());
            for (KeyAdapter.Flags flags : run(7, bypass, 200)) {
                assertEquals(WALK, flags, bypass.reason());
            }
        }
        assertFalse(Bypass.NONE.any());
    }

    @Test
    void sneakAlwaysPassesThrough() {
        ShapingLayer layer = new ShapingLayer(3, busyProfile());
        KeyAdapter.Flags sneakWalk = new KeyAdapter.Flags(true, false, false, false, false, false, true);
        for (int i = 0; i < 300; i++) {
            assertTrue(layer.apply(sneakWalk, FLAT, Bypass.NONE).sneak());
        }
    }

    @Test
    void neverAddsKeysBaritoneDidNotPress() {
        ShapingLayer layer = new ShapingLayer(11, busyProfile());
        KeyAdapter.Flags intended = new KeyAdapter.Flags(true, false, false, false, false, false, false);
        for (int i = 0; i < 500; i++) {
            KeyAdapter.Flags out = layer.apply(intended, FLAT, Bypass.NONE);
            assertFalse(out.back() || out.left() || out.right() || out.jump() || out.sprint() || out.sneak());
        }
    }

    @Test
    void jumpIsNeverInventedAndNeverDelayedPastTolerance() {
        // Baritone presses jump for one tick on an ascend; the layer may delay it at most one tick, never drop it twice.
        ShapingLayer layer = new ShapingLayer(5, busyProfile());
        PathView ascend = new PathView(MovementKind.ASCEND, FLAT.nodes(), 0.5, 64, 0.5, 0.2, 0, 0, true);
        KeyAdapter.Flags idle = new KeyAdapter.Flags(true, false, false, false, false, false, false);
        KeyAdapter.Flags jump = new KeyAdapter.Flags(true, false, false, false, true, false, false);
        for (int i = 0; i < 200; i++) {
            assertFalse(layer.apply(idle, ascend, Bypass.NONE).jump(), "invented jump at " + i);
        }
    }

    @Test
    void bypassDropsPendingJumpState() {
        ShapingLayer layer = new ShapingLayer(5, busyProfile());
        KeyAdapter.Flags jump = new KeyAdapter.Flags(true, false, false, false, true, false, false);
        KeyAdapter.Flags idle = new KeyAdapter.Flags(true, false, false, false, false, false, false);
        Bypass swim = new Bypass(false, false, true, false, false, false, false, false, false);
        for (int i = 0; i < 100; i++) {
            layer.apply(jump, FLAT, Bypass.NONE);
            layer.apply(idle, FLAT, swim);
            assertEquals(idle, layer.apply(idle, FLAT, swim));
            assertFalse(layer.apply(idle, FLAT, Bypass.NONE).jump(), "stale jump after bypass at " + i);
            layer.apply(idle, FLAT, swim);
        }
    }
}
