package dev.vibrato.tasks.resources;

import dev.vibrato.util.ItemTarget;
import net.minecraft.item.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectFoodAnimalTargetTest {
    @Test void eachAnimalTargetRequiresOneMoreThanAlreadyHeld() {
        var first = new ItemTarget(new Item[0], FoodAnimalTarget.nextCount(0));
        var next = new ItemTarget(new Item[0], FoodAnimalTarget.nextCount(4));
        assertEquals(1, first.getTargetCount());
        assertEquals(5, next.getTargetCount());
        assertNotEquals(first, next);
        assertEquals(next, new ItemTarget(new Item[0], FoodAnimalTarget.nextCount(4)));
    }

    @Test void targetCountDoesNotOverflow() {
        assertEquals(Integer.MAX_VALUE, FoodAnimalTarget.nextCount(Integer.MAX_VALUE));
    }
}
