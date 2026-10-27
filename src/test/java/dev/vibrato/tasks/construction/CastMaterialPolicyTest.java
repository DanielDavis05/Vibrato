package dev.vibrato.tasks.construction;

import org.junit.jupiter.api.Test;
import net.minecraft.util.math.BlockPos;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CastMaterialPolicyTest {
    record Stack(String item, int amount, boolean block) { }

    @Test void countsOnlyAcceptedUnprotectedBlocksInMainInventory() {
        // The caller passes the 36 main slots, including non-hotbar slots; cursor
        // and offhand are separate inventories and must not satisfy placement.
        List<Stack> main = List.of(new Stack("dirt", 3, true), new Stack("cobble", 4, true),
                new Stack("torch", 12, false), new Stack("valuable", 20, true));
        Set<String> accepted = Set.of("dirt", "cobble", "torch");
        Set<String> protectedItems = Set.of("dirt");
        assertEquals(4, CastMaterialPolicy.countUsable(main,
                s -> s.block && accepted.contains(s.item) && !protectedItems.contains(s.item), Stack::amount));
        assertEquals(0, CastMaterialPolicy.countUsable(List.<Stack>of(), s -> true, Stack::amount));
    }

    @Test void countsOnlyMissingPositionsInTheSixBlockCast() {
        BlockPos origin = new BlockPos(42, -50, 7);
        Set<BlockPos> solid = Set.of(origin.down(), origin.north(), origin.east());
        assertEquals(3, PlaceObsidianBucketTask.missingFrameCount(origin, solid::contains));
        assertEquals(6, PlaceObsidianBucketTask.missingFrameCount(origin, pos -> false));
        assertEquals(0, PlaceObsidianBucketTask.missingFrameCount(origin, pos -> true));
    }

    @Test void requestsOnlyDeficitInAdditionToAllPreviouslyHeldCobblestone() {
        // Two of the six positions are already solid, and two usable blocks
        // exist. Existing cobble may include an offhand stack, so the resource
        // goal must be above the entire amount ResourceTask already counts.
        assertEquals(12, CastMaterialPolicy.cobblestoneTarget(10, 4, 2));
        assertEquals(6, CastMaterialPolicy.cobblestoneTarget(0, 6, 0));
        assertEquals(10, CastMaterialPolicy.cobblestoneTarget(10, 2, 5));
    }
}
