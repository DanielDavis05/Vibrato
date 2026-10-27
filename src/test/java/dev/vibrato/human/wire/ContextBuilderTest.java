package dev.vibrato.human.wire;

import dev.vibrato.human.motion.KeyShaper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextBuilderTest {

    private static PathView.Node node(double x, double y, double z) {
        return new PathView.Node(x, y, z);
    }

    private static PathView view(MovementKind kind, List<PathView.Node> nodes) {
        return new PathView(kind, nodes, 0.5, 64, 0.5, 0.1, 0, 0, true);
    }

    private static final List<PathView.Node> FLAT = List.of(
            node(0.5, 64, 0.5), node(1.5, 64, 0.5), node(2.5, 64, 0.5), node(3.5, 64, 0.5), node(4.5, 64, 0.5));

    @Test
    void flatPathHasNoGapAndNeverForecastsAJump() {
        KeyShaper.Context ctx = ContextBuilder.build(view(MovementKind.TRAVERSE, FLAT));
        assertEquals(Double.POSITIVE_INFINITY, ctx.gapAheadBlocks());
        assertEquals(KeyShaper.NO_JUMP, ctx.ticksUntilJump());
        assertEquals(0.1, ctx.speed(), 1e-9);
        assertTrue(ctx.onGround());
    }

    @Test
    void toleranceIsOneLateTickForAscendAndTraverseOnly() {
        for (MovementKind kind : MovementKind.values()) {
            KeyShaper.Context ctx = ContextBuilder.build(view(kind, FLAT));
            int late = kind == MovementKind.ASCEND || kind == MovementKind.TRAVERSE ? 1 : 0;
            assertEquals(0, ctx.earlyToleranceTicks(), kind.name());
            assertEquals(late, ctx.lateToleranceTicks(), kind.name());
        }
    }

    @Test
    void dropWithinThreeSegmentsIsAGapAtItsDistance() {
        List<PathView.Node> nodes = List.of(node(0.5, 64, 0.5), node(1.5, 64, 0.5), node(2.5, 64, 0.5),
                node(3.5, 62, 0.5), node(4.5, 62, 0.5));
        KeyShaper.Context ctx = ContextBuilder.build(view(MovementKind.TRAVERSE, nodes));
        assertEquals(2.0, ctx.gapAheadBlocks(), 1e-9);
    }

    @Test
    void oneBlockStepDownIsNotAGap() {
        List<PathView.Node> stairs = List.of(node(0.5, 64, 0.5), node(1.5, 63, 0.5), node(2.5, 62, 0.5),
                node(3.5, 61, 0.5), node(4.5, 60, 0.5));
        assertEquals(Double.POSITIVE_INFINITY, ContextBuilder.build(view(MovementKind.TRAVERSE, stairs)).gapAheadBlocks());
    }

    @Test
    void dropOfExactlyOneAndAHalfBlocksIsNotAGapButTwoBlocksIs() {
        List<PathView.Node> half = List.of(node(0.5, 64, 0.5), node(1.5, 62.5, 0.5), node(2.5, 62.5, 0.5));
        assertEquals(Double.POSITIVE_INFINITY, ContextBuilder.build(view(MovementKind.TRAVERSE, half)).gapAheadBlocks());
        List<PathView.Node> two = List.of(node(0.5, 64, 0.5), node(1.5, 62, 0.5), node(2.5, 62, 0.5));
        assertEquals(0.0, ContextBuilder.build(view(MovementKind.TRAVERSE, two)).gapAheadBlocks(), 1e-9);
    }

    @Test
    void horizontalGapIsDetectedButDiagonalIsNot() {
        List<PathView.Node> gap = List.of(node(0.5, 64, 0.5), node(1.5, 64, 0.5), node(3.5, 64, 0.5));
        assertEquals(1.0, ContextBuilder.build(view(MovementKind.TRAVERSE, gap)).gapAheadBlocks(), 1e-9);
        List<PathView.Node> diagonal = List.of(node(0.5, 64, 0.5), node(1.5, 64, 1.5), node(2.5, 64, 2.5));
        assertEquals(Double.POSITIVE_INFINITY,
                ContextBuilder.build(view(MovementKind.DIAGONAL, diagonal)).gapAheadBlocks());
    }

    @Test
    void dropBeyondLookaheadIsIgnored() {
        List<PathView.Node> nodes = List.of(node(0.5, 64, 0.5), node(1.5, 64, 0.5), node(2.5, 64, 0.5),
                node(3.5, 64, 0.5), node(4.5, 60, 0.5));
        assertEquals(Double.POSITIVE_INFINITY, ContextBuilder.build(view(MovementKind.TRAVERSE, nodes)).gapAheadBlocks());
    }

    @Test
    void ascentIsNotAGap() {
        List<PathView.Node> nodes = List.of(node(0.5, 64, 0.5), node(1.5, 65, 0.5), node(2.5, 66, 0.5));
        assertEquals(Double.POSITIVE_INFINITY, ContextBuilder.build(view(MovementKind.ASCEND, nodes)).gapAheadBlocks());
    }

    @Test
    void riskyOrUnknownMovementsReportAGapRightHere() {
        for (MovementKind kind : new MovementKind[]{MovementKind.DESCEND, MovementKind.FALL, MovementKind.PARKOUR}) {
            assertEquals(0, ContextBuilder.build(view(kind, FLAT)).gapAheadBlocks(), kind.name());
        }
        assertEquals(0, ContextBuilder.build(view(MovementKind.TRAVERSE, List.of())).gapAheadBlocks());
        assertEquals(0, ContextBuilder.build(view(MovementKind.TRAVERSE, List.of(node(0, 64, 0)))).gapAheadBlocks());
    }

    @Test
    void movementKindParsesBaritoneClassNames() {
        assertEquals(MovementKind.ASCEND, MovementKind.fromClassName("MovementAscend"));
        assertEquals(MovementKind.PARKOUR, MovementKind.fromClassName("MovementParkour"));
        assertEquals(MovementKind.OTHER, MovementKind.fromClassName("MovementSomethingNew"));
        assertEquals(MovementKind.OTHER, MovementKind.fromClassName(null));
    }
}
