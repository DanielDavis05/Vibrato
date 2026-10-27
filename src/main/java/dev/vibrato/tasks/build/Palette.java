package dev.vibrato.tasks.build;

/** Building material set. Maps each {@link BlockKind} to a registry item id; holds no Minecraft types. */
public enum Palette {
    OAK("oak_planks", "oak_stairs", "oak_fence", "oak_fence_gate", "oak_door"),
    COBBLESTONE("cobblestone", "cobblestone_stairs", "cobblestone_wall", "oak_fence_gate", "oak_door"),
    STONE_BRICKS("stone_bricks", "stone_brick_stairs", "stone_brick_wall", "oak_fence_gate", "oak_door"),
    SANDSTONE("sandstone", "sandstone_stairs", "sandstone_wall", "oak_fence_gate", "oak_door"),
    SPRUCE("spruce_planks", "spruce_stairs", "spruce_fence", "spruce_fence_gate", "spruce_door");

    private final String solid;
    private final String stairs;
    private final String fence;
    private final String gate;
    private final String door;

    Palette(String solid, String stairs, String fence, String gate, String door) {
        this.solid = solid;
        this.stairs = stairs;
        this.fence = fence;
        this.gate = gate;
        this.door = door;
    }

    public String itemId(BlockKind kind) {
        return switch (kind) {
            case WALL, FLOOR, ROOF, PILLAR -> solid;
            case STAIR -> stairs;
            case FENCE -> fence;
            case GATE -> gate;
            case DOOR -> door;
            case WINDOW -> "glass_pane";
            case TORCH -> "torch";
            case BED -> "white_bed";
            case CHEST -> "chest";
            case CRAFTING_TABLE -> "crafting_table";
            case FURNACE -> "furnace";
            case FARMLAND -> "farmland";
            case WATER -> "water_bucket";
            case PATH -> "dirt_path";
            case LADDER -> "ladder";
            case OBSIDIAN -> "obsidian";
        };
    }
}
