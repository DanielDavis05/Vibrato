package dev.vibrato.util;

import dev.vibrato.util.time.TimerGame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * For crafting table/inventory recipe book crafting, we need to figure out identifiers given a recipe.
 */
public class JankCraftingRecipeMapping {
    private static final HashMap<Item, List<RecipeEntry<?>>> _recipeMapping = new HashMap<>();
    // The recipe manager our mapping was built from. Each world/server gets its own, so rebuild when it changes.
    private static RecipeManager _mappedRecipeManager = null;
    // Don't rebuild the whole mapping every tick when a recipe genuinely doesn't exist.
    private static final TimerGame _reloadOnMissTimer = new TimerGame(5);

    private static void reloadRecipeMapping() {
        _recipeMapping.clear();
        _mappedRecipeManager = null;
        if (MinecraftClient.getInstance().getNetworkHandler() != null) {
            RecipeManager recipes = MinecraftClient.getInstance().getNetworkHandler().getRecipeManager();
            _mappedRecipeManager = recipes;
            for (RecipeEntry<?> recipe : recipes.values()) {
                Item output = recipe.value().getResult(MinecraftClient.getInstance().getNetworkHandler().getRegistryManager()).getItem();
                if (!_recipeMapping.containsKey(output)) {
                    _recipeMapping.put(output, new ArrayList<>());
                }
                _recipeMapping.get(output).add(recipe);
            }
        }
    }

    public static Optional<RecipeEntry<?>> getMinecraftMappedRecipe(CraftingRecipe recipe, Item output) {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (_recipeMapping.isEmpty() || networkHandler == null || networkHandler.getRecipeManager() != _mappedRecipeManager) {
            reloadRecipeMapping();
        }
        Optional<RecipeEntry<?>> result = findMappedRecipe(recipe, output);
        // Recipes may have been (re)synced since we built our mapping, try once more with fresh data.
        if (result.isEmpty() && _reloadOnMissTimer.elapsed()) {
            _reloadOnMissTimer.reset();
            reloadRecipeMapping();
            result = findMappedRecipe(recipe, output);
        }
        return result;
    }


    private static Optional<RecipeEntry<?>> findMappedRecipe(CraftingRecipe recipe, Item output) {
        if (_recipeMapping.containsKey(output)) {
            for (RecipeEntry<?> checkRecipe : _recipeMapping.get(output)) {
                // Check for item count/satisfiability and not shape satisfiability (that would be annoying)
                // Assumes there are no 2 recipes with the same output and same inputs in a different order.
                List<ItemTarget> toSatisfy = Arrays.stream(recipe.getSlots()).filter(itemTarget -> itemTarget != null && !itemTarget.isEmpty()).collect(Collectors.toList());
                for (Object ingredientObj : checkRecipe.value().getIngredients()) {
                    Ingredient checkIngredient = (Ingredient) ingredientObj;
                    if (checkIngredient.isEmpty())
                        continue;
                    // Remove from "toSatisfy" if we find something that fits
                    outer:
                    for (int i = 0; i < toSatisfy.size(); ++i) {
                        ItemTarget check = toSatisfy.get(i);
                        for (ItemStack match : checkIngredient.getMatchingStacks()) {
                            if (check.matches(match.getItem())) {
                                toSatisfy.remove(i);
                                break outer;
                            }
                        }
                    }
                }
                /*int i = -1; // ++i first
                boolean found = true;
                for (Object ingredientObj : checkRecipe.getIngredients()) {
                    ++i;
                    // Out of range
                    if (i >= recipe.getSlotCount()) {
                        found = false;
                        break;
                    }
                    ItemTarget ourIngredient = recipe.getSlot(i);
                    Ingredient checkIngredient = (Ingredient) ingredientObj;
                    // If our ingredient is null, our check ingredient MUST be empty (Empty = null)
                    if (ourIngredient == null || ourIngredient.isEmpty()) {
                        if (checkIngredient.isEmpty())
                            continue;
                        found = false;
                        break;
                    }
                    // At least one item must satisfy to move on.
                    if (Arrays.stream(checkIngredient.getMatchingStacks()).noneMatch(itemStack -> ourIngredient.matches(itemStack.getItem()))) {
                        found = false;
                        break;
                    }
                }
                if (found)
                    return Optional.of(checkRecipe);
                 */
                // We satisfied every material, so assume it's the right recipe.
                if (toSatisfy.isEmpty()) {
                    return Optional.of(checkRecipe);
                }
            }
        }
        return Optional.empty();
    }
}
