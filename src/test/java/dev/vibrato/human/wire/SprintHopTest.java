package dev.vibrato.human.wire;

import dev.vibrato.human.motion.HumanProfile;
import dev.vibrato.human.motion.Population;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SprintHopTest {
    private static final List<PathView.Node> STRAIGHT = List.of(new PathView.Node(0, 64, 0),
            new PathView.Node(1, 64, 0), new PathView.Node(2, 64, 0), new PathView.Node(3, 64, 0),
            new PathView.Node(4, 64, 0), new PathView.Node(5, 64, 0));
    private static final List<MovementKind> TRAVERSE = List.of(MovementKind.TRAVERSE, MovementKind.TRAVERSE, MovementKind.TRAVERSE, MovementKind.TRAVERSE, MovementKind.TRAVERSE);
    private static PathView view(List<PathView.Node> nodes, boolean ground) {
        return new PathView(MovementKind.TRAVERSE, nodes, 0, 64, 0, .2, 0, 0, ground);
    }
    @Test void geometryRequiresFiveStraightFlatTraverses() {
        assertTrue(SprintHop.straightFlat(STRAIGHT, TRAVERSE));
        assertFalse(SprintHop.straightFlat(STRAIGHT.subList(0, 3), TRAVERSE));
        assertFalse(SprintHop.straightFlat(STRAIGHT, List.of(MovementKind.TRAVERSE, MovementKind.ASCEND, MovementKind.TRAVERSE)));
        assertFalse(SprintHop.straightFlat(List.of(STRAIGHT.get(0), STRAIGHT.get(1), new PathView.Node(2, 65, 0), STRAIGHT.get(3)), TRAVERSE));
        assertFalse(SprintHop.straightFlat(List.of(STRAIGHT.get(0), STRAIGHT.get(1), new PathView.Node(1, 64, 1), new PathView.Node(1, 64, 2)), TRAVERSE));
    }
    @Test void forecastRejectsGapOrTurnAfterTheThirdSegment() {
        assertFalse(SprintHop.straightFlat(List.of(STRAIGHT.get(0), STRAIGHT.get(1), STRAIGHT.get(2),
                STRAIGHT.get(3), new PathView.Node(5, 64, 0), new PathView.Node(6, 64, 0)), TRAVERSE));
        assertFalse(SprintHop.straightFlat(List.of(STRAIGHT.get(0), STRAIGHT.get(1), STRAIGHT.get(2),
                STRAIGHT.get(3), new PathView.Node(3, 64, 1), new PathView.Node(3, 64, 2)), TRAVERSE));
        assertFalse(SprintHop.straightFlat(STRAIGHT, List.of(MovementKind.TRAVERSE, MovementKind.TRAVERSE,
                MovementKind.TRAVERSE, MovementKind.PARKOUR, MovementKind.TRAVERSE)));
        assertFalse(SprintHop.straightFlat(STRAIGHT.subList(0, 5), TRAVERSE));
    }
    @Test void idleThenSafeGroundPrimesOneHopAndLaterRequiresLanding() {
        var keys = new KeyAdapter.Flags(true, false, false, false, false, true, false);
        var idle = new KeyAdapter.Flags(false, false, false, false, false, false, false);
        var hop = new SprintHop(1, HumanProfile.typical(Population.DEFAULT));
        for (int i = 0; i < 10; i++) assertFalse(hop.tick(idle, view(STRAIGHT, true), TRAVERSE, false, true));
        for (int i = 0; i < 5; i++) assertFalse(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
        assertTrue(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
        for (int i = 0; i < 20; i++) assertFalse(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
        assertFalse(hop.tick(keys, view(STRAIGHT, false), TRAVERSE, true, true));
        assertTrue(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
    }
    @Test void hazardsResetRunUpAndCooldownStillApplies() {
        var keys = new KeyAdapter.Flags(true, false, false, false, false, true, false);
        var hop = new SprintHop(1, HumanProfile.typical(Population.DEFAULT));
        for (int i = 0; i < 5; i++) assertFalse(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
        assertFalse(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, false, true));
        for (int i = 0; i < 5; i++) assertFalse(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
        assertTrue(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
        hop.tick(keys, view(STRAIGHT, false), TRAVERSE, true, true);
        assertFalse(hop.tick(keys, view(STRAIGHT, true), TRAVERSE, true, true));
    }
}
