package dev.vibrato.human.wire;

import dev.vibrato.human.motion.KeyShaper;

import java.util.List;

/**
 * Builds the {@link KeyShaper.Context} from a {@link PathView}. Conservative: whenever the view does not prove a move
 * is safe, the gap is reported as close and no jump timing may shift. Baritone gives no jump forecast, so
 * {@code ticksUntilJump} is always {@link KeyShaper#NO_JUMP} and a jump is only ever the one Baritone is pressing now.
 */
public final class ContextBuilder {

    /** How many path segments ahead are checked for a drop or a gap. */
    static final int LOOKAHEAD_SEGMENTS = 3;
    /** A step longer than this between nodes is a horizontal gap; a diagonal step is about 1.41. */
    static final double GAP_STEP_BLOCKS = 1.5;
    private static final double DROP_BLOCKS = 0.5;

    private ContextBuilder() {}

    public static KeyShaper.Context build(PathView view) {
        boolean jumpWindow = view.kind() == MovementKind.ASCEND || view.kind() == MovementKind.TRAVERSE;
        return new KeyShaper.Context(
                view.onGround(),
                Math.hypot(view.vx(), view.vz()),
                gapAheadBlocks(view),
                KeyShaper.NO_JUMP,
                0,
                jumpWindow ? 1 : 0);
    }

    /**
     * Horizontal distance to the first node with a drop or gap after it within {@value #LOOKAHEAD_SEGMENTS}
     * segments, 0 when the current movement itself is one or the path is unknown, infinite when there is none.
     */
    static double gapAheadBlocks(PathView view) {
        MovementKind kind = view.kind();
        if (kind == MovementKind.DESCEND || kind == MovementKind.FALL || kind == MovementKind.PARKOUR) {
            return 0;
        }
        List<PathView.Node> nodes = view.nodes();
        if (nodes.size() < 2) {
            return 0;
        }
        int last = Math.min(LOOKAHEAD_SEGMENTS, nodes.size() - 1);
        for (int i = 0; i < last; i++) {
            PathView.Node from = nodes.get(i);
            PathView.Node to = nodes.get(i + 1);
            boolean drop = to.y() < from.y() - DROP_BLOCKS;
            boolean gap = Math.hypot(to.x() - from.x(), to.z() - from.z()) > GAP_STEP_BLOCKS;
            if (drop || gap) {
                return Math.hypot(from.x() - view.x(), from.z() - view.z());
            }
        }
        return Double.POSITIVE_INFINITY;
    }
}
