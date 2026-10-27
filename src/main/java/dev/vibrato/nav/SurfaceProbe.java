package dev.vibrato.nav;

import java.util.List;

/**
 * Decides how to get to open sky from a column of blocks above the bot's head. Pure: the caller classifies blocks into
 * {@link Cell}s. The column runs upwards, index 0 being the block directly above the head.
 */
public final class SurfaceProbe {

    public enum Cell {
        AIR, SOFT, STONE, HARD, FALLING, LIQUID
    }

    public enum Verdict {
        /** Nothing solid above: the bot is already at the surface. */
        OPEN_SKY,
        /** The next block can be mined by hand. */
        DIG_SOFT,
        /** The next block needs a pickaxe, and the bot has one. */
        DIG_STONE,
        /** The next block needs a pickaxe, and the bot has none. */
        NEEDS_PICKAXE,
        /** Liquid or a stack of falling blocks sits above: digging would drop it on the bot. */
        HAZARD,
        /** Obsidian or other unbreakable-for-us blocks. */
        BLOCKED
    }

    private SurfaceProbe() {}

    public static Verdict decide(List<Cell> column, boolean hasPickaxe) {
        int next = -1;
        for (int i = 0; i < column.size(); i++) {
            Cell cell = column.get(i);
            if (cell == Cell.LIQUID) {
                return Verdict.HAZARD;
            }
            if (cell != Cell.AIR && next < 0) {
                next = i;
            }
        }
        if (next < 0) {
            return Verdict.OPEN_SKY;
        }
        Cell target = column.get(next);
        if (target == Cell.FALLING && next + 1 < column.size() && column.get(next + 1) == Cell.FALLING) {
            return Verdict.HAZARD;
        }
        return switch (target) {
            case HARD -> Verdict.BLOCKED;
            case STONE -> hasPickaxe ? Verdict.DIG_STONE : Verdict.NEEDS_PICKAXE;
            case SOFT, FALLING -> Verdict.DIG_SOFT;
            default -> Verdict.HAZARD;
        };
    }
}
