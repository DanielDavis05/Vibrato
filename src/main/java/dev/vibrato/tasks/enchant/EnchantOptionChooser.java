package dev.vibrato.tasks.enchant;

/**
 * Chooses which of the three enchanting table options to take. Option {@code i} costs {@code costs[i]} XP levels and
 * the same number of lapis lazuli, and shows an enchantment power of {@code powers[i]} (0 when the option is empty).
 */
public final class EnchantOptionChooser {

    private EnchantOptionChooser() {}

    /**
     * @return the index of the most expensive option that is affordable and at least {@code minPower}, or -1
     */
    public static int choose(int[] powers, int[] costs, int playerLevels, int lapis, int minPower) {
        for (int index = powers.length - 1; index >= 0; index--) {
            boolean affordable = playerLevels >= costs[index] && lapis >= costs[index];
            if (powers[index] > 0 && powers[index] >= minPower && affordable) {
                return index;
            }
        }
        return -1;
    }
}
