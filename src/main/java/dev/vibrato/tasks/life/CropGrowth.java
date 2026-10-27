package dev.vibrato.tasks.life;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Growth facts for the crops a farm can plant. Pure data so it can be unit tested without a game bootstrap.
 */
public enum CropGrowth {
    WHEAT("wheat", "wheat", 7, "wheat_seeds"),
    CARROT("carrot", "carrots", 7, "carrot"),
    POTATO("potato", "potatoes", 7, "potato"),
    BEETROOT("beetroot", "beetroots", 3, "beetroot_seeds");

    private final String _itemId;
    private final String _blockId;
    private final int _maxAge;
    private final String _seedId;

    CropGrowth(String itemId, String blockId, int maxAge, String seedId) {
        _itemId = itemId;
        _blockId = blockId;
        _maxAge = maxAge;
        _seedId = seedId;
    }

    /** Item id of the harvested crop, without namespace. */
    public String itemId() {
        return _itemId;
    }

    /** Block id of the growing crop, without namespace. */
    public String blockId() {
        return _blockId;
    }

    /** Age at which the crop is fully grown and can be harvested. */
    public int maxAge() {
        return _maxAge;
    }

    /** Item id that plants this crop, without namespace. */
    public String seedId() {
        return _seedId;
    }

    public boolean isMature(int age) {
        return age >= _maxAge;
    }

    public static List<String> itemIds() {
        return Arrays.stream(values()).map(CropGrowth::itemId).toList();
    }

    public static Optional<CropGrowth> forItemId(String itemId) {
        return Arrays.stream(values()).filter(growth -> growth._itemId.equals(itemId)).findFirst();
    }
}
