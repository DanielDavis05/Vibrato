package dev.vibrato.tasks.life;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Crops that HarvestAndReplantTask can take. Ids are item ids without namespace; the block id is what grows in the
 * world. Pure data so the activity's offered crops can be checked in unit tests.
 */
public enum HarvestableCrops {
    SUGAR_CANE("sugar_cane", "sugar_cane"),
    PUMPKIN("pumpkin", "pumpkin"),
    MELON_SLICE("melon_slice", "melon");

    private final String _itemId;
    private final String _blockId;

    HarvestableCrops(String itemId, String blockId) {
        _itemId = itemId;
        _blockId = blockId;
    }

    public String itemId() {
        return _itemId;
    }

    public String blockId() {
        return _blockId;
    }

    public static List<String> itemIds() {
        return Arrays.stream(values()).map(HarvestableCrops::itemId).toList();
    }

    public static Optional<HarvestableCrops> forItemId(String itemId) {
        return Arrays.stream(values()).filter(crop -> crop._itemId.equals(itemId)).findFirst();
    }
}
