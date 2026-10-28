package dev.vibrato.tasks.construction;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Local cells from world28 saved NBT; wall is restored to observed S2C pre-grief state. */
class World28PlacementRouteTest {
    @Test void existingNativeScaffoldAllowsOneBlockRiseOntoSupportedTwoBlockColumn() {
        Set<BlockPos> terrain = new HashSet<>();
        // Sand foundation and the real earlier temporary scaffold (observed in the log and saved NBT).
        terrain.add(new BlockPos(-33, 65, -50));
        terrain.add(new BlockPos(-32, 65, -50));
        terrain.add(new BlockPos(-32, 66, -50));
        for (int x = -32; x <= -29; x++) for (int y = 66; y <= 68; y++)
            terrain.add(new BlockPos(x, y, -53));
        terrain.add(new BlockPos(-32, 69, -53));
        BlockPos from = new BlockPos(-32, 67, -50), stand = new BlockPos(-33, 68, -50);
        Set<BlockPos> overlay = new HashSet<>(terrain);
        overlay.add(new BlockPos(-33, 66, -50));
        overlay.add(new BlockPos(-33, 67, -50));
        assertFalse(PlacementRoute.reachable(from, stand, p -> !terrain.contains(p), terrain::contains));
        assertTrue(PlacementRoute.reachable(from, stand, p -> !overlay.contains(p), overlay::contains));
        // Sneaking eye to the supporting top-face centre: 4.251 blocks, inside 4.5 reach.
        double distance = Math.sqrt(3 * 3 + 0.27 * 0.27 + 3 * 3);
        assertTrue(distance < 4.5);
    }
}
