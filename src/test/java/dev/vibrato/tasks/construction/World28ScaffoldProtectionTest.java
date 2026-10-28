package dev.vibrato.tasks.construction;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** World28 run 11 local snapshot, with two sand blocks restored from recorded pre-dig evidence. */
class World28ScaffoldProtectionTest {
    @Test void earlierAcknowledgedScaffoldProvidesStepToSupportedCapColumn() {
        Set<BlockPos> solid = new HashSet<>();
        // Saved terrain, plus (-33,64,-69), identified as sand by the miner and dug later.
        for (int x = -35; x <= -32; x++) solid.add(new BlockPos(x, 64, -69));
        // S2C confirmed this previously placed scaffold before the collection dig removed it.
        BlockPos scaffold = new BlockPos(-34, 65, -69);
        solid.add(scaffold);
        for (int x = -35; x <= -32; x++) for (int y = 65; y <= 67; y++)
            solid.add(new BlockPos(x, y, -71));
        // Candidate actual supported two-block sand column; no other overlay is assumed.
        solid.add(new BlockPos(-35, 65, -69));
        solid.add(new BlockPos(-35, 66, -69));
        BlockPos actor = new BlockPos(-32, 65, -69), stand = new BlockPos(-35, 67, -69);
        assertTrue(PlacementRoute.reachable(actor, stand, p -> !solid.contains(p), solid::contains));
        // Center of top support face is two blocks ahead of the sneaking eye, well within reach.
        assertTrue(Math.sqrt(2 * 2 + .27 * .27) < 4.5);
        Set<BlockPos> withoutAcknowledgedStep = new HashSet<>(solid);
        withoutAcknowledgedStep.remove(scaffold);
        assertFalse(PlacementRoute.reachable(actor, stand,
                p -> !withoutAcknowledgedStep.contains(p), withoutAcknowledgedStep::contains));
    }
}
