package dev.vibrato.tasks.organize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.Set;

/**
 * Decides how to rearrange the player's 36 main inventory slots: best weapon and tools onto the hotbar, food and
 * torches next to them, building blocks on the hotbar tail, and junk thrown out. Works on item ids only.
 */
public final class InventoryPlan {

    public enum Role { SWORD, PICKAXE, AXE, SHOVEL, HOE, FOOD, TORCH, BUCKET, BLOCK, DIRT_LIKE, JUNK, VALUABLE, OTHER }

    /** A non-empty slot. Slots 0-8 are the hotbar, 9-35 the rest of the main inventory. */
    public record Stack(int slot, String id, int count) {}

    /** Swap the contents of {@code from} with hotbar slot {@code hotbar}. */
    public record Swap(int from, int hotbar) {}

    public record Plan(List<Swap> swaps, List<Integer> dump) {}

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 36;
    /** Two full stacks of dirt-like blocks are kept; any surplus beyond this is dumped. */
    static final int DIRT_LIKE_KEEP = 128;

    /** Acceptable roles per hotbar slot, in preference order. */
    private static final List<List<Role>> HOTBAR_ROLES = List.of(
            List.of(Role.SWORD), List.of(Role.PICKAXE), List.of(Role.AXE), List.of(Role.SHOVEL),
            List.of(Role.FOOD), List.of(Role.TORCH), List.of(Role.BUCKET, Role.HOE),
            List.of(Role.BLOCK, Role.DIRT_LIKE), List.of(Role.BLOCK, Role.DIRT_LIKE));

    private static final Set<String> FOOD = Set.of(
            "apple", "bread", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_cod",
            "cooked_salmon", "baked_potato", "carrot", "melon_slice", "sweet_berries", "beetroot", "cookie",
            "pumpkin_pie", "mushroom_stew", "rabbit_stew", "beef", "porkchop", "chicken", "mutton", "cod", "salmon",
            "potato", "honey_bottle", "dried_kelp");
    private static final Set<String> DIRT_LIKE = Set.of("dirt", "cobblestone", "gravel", "netherrack", "grass_block");
    private static final Set<String> JUNK = Set.of("rotten_flesh", "poisonous_potato", "spider_eye", "pufferfish");
    private static final Set<String> VALUABLE = Set.of(
            "diamond", "emerald", "iron_ingot", "gold_ingot", "raw_iron", "raw_gold", "lapis_lazuli", "redstone",
            "enchanted_book", "golden_apple", "enchanted_golden_apple", "netherite_ingot", "ancient_debris");
    private static final Set<String> BLOCK = Set.of(
            "stone", "sandstone", "andesite", "diorite", "granite", "tuff", "deepslate");

    private InventoryPlan() {}

    public static Role classify(String id) {
        if (id.endsWith("_sword")) return Role.SWORD;
        if (id.endsWith("_pickaxe")) return Role.PICKAXE;
        if (id.endsWith("_axe")) return Role.AXE;
        if (id.endsWith("_shovel")) return Role.SHOVEL;
        if (id.endsWith("_hoe")) return Role.HOE;
        if (FOOD.contains(id)) return Role.FOOD;
        if (id.equals("torch")) return Role.TORCH;
        if (id.equals("bucket") || id.equals("water_bucket")) return Role.BUCKET;
        if (DIRT_LIKE.contains(id)) return Role.DIRT_LIKE;
        if (JUNK.contains(id)) return Role.JUNK;
        if (VALUABLE.contains(id)) return Role.VALUABLE;
        if (id.endsWith("_planks") || id.endsWith("_bricks") || BLOCK.contains(id)) return Role.BLOCK;
        return Role.OTHER;
    }

    /** Material tier of a tool or weapon id: netherite 5 down to wooden 0, -1 for anything else. */
    public static int tier(String id) {
        if (id.startsWith("netherite_")) return 5;
        if (id.startsWith("diamond_")) return 4;
        if (id.startsWith("iron_")) return 3;
        if (id.startsWith("golden_")) return 2;
        if (id.startsWith("stone_")) return 1;
        if (id.startsWith("wooden_")) return 0;
        return -1;
    }

    /** Whether loot of this id is worth moving into a storage chest rather than carrying. */
    public static boolean isStashable(String id) {
        Role role = classify(id);
        return role == Role.VALUABLE || role == Role.OTHER;
    }

    public static Plan plan(List<Stack> inventory) {
        List<Stack> stacks = new ArrayList<>(inventory);
        int[] contents = new int[MAIN_SIZE];
        Arrays.fill(contents, -1);
        for (int i = 0; i < stacks.size(); i++) {
            contents[stacks.get(i).slot()] = i;
        }

        int[] desired = new int[HOTBAR_SIZE];
        Arrays.fill(desired, -1);
        Set<Integer> chosen = new HashSet<>();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            for (Role role : HOTBAR_ROLES.get(slot)) {
                int pick = best(role, stacks, chosen);
                if (pick >= 0) {
                    desired[slot] = pick;
                    chosen.add(pick);
                    break;
                }
            }
        }

        List<Swap> swaps = new ArrayList<>();
        for (int hotbar = 0; hotbar < HOTBAR_SIZE; hotbar++) {
            if (desired[hotbar] < 0) {
                continue;
            }
            int from = indexOf(contents, desired[hotbar]);
            if (from != hotbar) {
                int moved = contents[from];
                contents[from] = contents[hotbar];
                contents[hotbar] = moved;
                swaps.add(new Swap(from, hotbar));
            }
        }

        int dirtTotal = 0;
        for (Stack stack : stacks) {
            if (classify(stack.id()) == Role.DIRT_LIKE) {
                dirtTotal += stack.count();
            }
        }
        List<Integer> dump = new ArrayList<>();
        for (int slot = HOTBAR_SIZE; slot < MAIN_SIZE; slot++) {
            int index = contents[slot];
            if (index < 0 || chosen.contains(index)) {
                continue;
            }
            Role role = classify(stacks.get(index).id());
            if (role == Role.JUNK || (role == Role.DIRT_LIKE && dirtTotal > DIRT_LIKE_KEEP)) {
                dump.add(slot);
            }
        }
        return new Plan(swaps, dump);
    }

    /** Index into {@code stacks} of the best stack for a role not already chosen, or -1. */
    private static int best(Role role, List<Stack> stacks, Set<Integer> chosen) {
        int pick = -1;
        int pickScore = Integer.MIN_VALUE;
        for (int i = 0; i < stacks.size(); i++) {
            if (chosen.contains(i)) {
                continue;
            }
            Stack stack = stacks.get(i);
            Role stackRole = classify(stack.id());
            boolean accepts = stackRole == role || (role == Role.BLOCK && stackRole == Role.DIRT_LIKE);
            if (!accepts) {
                continue;
            }
            int score = isTool(role) ? tier(stack.id()) : stack.count();
            if (score > pickScore) {
                pickScore = score;
                pick = i;
            }
        }
        return pick;
    }

    private static boolean isTool(Role role) {
        return role == Role.SWORD || role == Role.PICKAXE || role == Role.AXE || role == Role.SHOVEL || role == Role.HOE;
    }

    private static int indexOf(int[] contents, int value) {
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] == value) {
                return slot;
            }
        }
        throw new IllegalStateException("stack " + value + " vanished from inventory");
    }
}
