package dev.vibrato.tasks.build;

/** Role of one blueprint cell. Concrete items come from a {@link Palette}. */
public enum BlockKind {
    WALL, FLOOR, ROOF, PILLAR, DOOR, WINDOW, TORCH, BED, CHEST, CRAFTING_TABLE, FURNACE,
    FARMLAND, WATER, PATH, FENCE, GATE, LADDER, STAIR, OBSIDIAN;

    /** Kinds the executor cannot place as an item (tilled soil, fluids, shovel-flattened paths). */
    public boolean isToolPlaced() {
        return this == FARMLAND || this == WATER || this == PATH;
    }
}
