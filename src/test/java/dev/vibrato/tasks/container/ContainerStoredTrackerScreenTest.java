package dev.vibrato.tasks.container;

import net.minecraft.screen.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerStoredTrackerScreenTest {
    @Test void chestBarrelAndShulkerScreensAreStorageDeposits() {
        assertTrue(ContainerStoredTracker.isStorageHandler(GenericContainerScreenHandler.class));
        assertTrue(ContainerStoredTracker.isStorageHandler(ShulkerBoxScreenHandler.class));
    }
    @Test void acquisitionAndProcessingInputsNeverSatisfyStorageGoals() {
        for (Class<?> screen : new Class<?>[]{PlayerScreenHandler.class, CraftingScreenHandler.class,
                FurnaceScreenHandler.class, BlastFurnaceScreenHandler.class, SmokerScreenHandler.class,
                BrewingStandScreenHandler.class, SmithingScreenHandler.class, MerchantScreenHandler.class,
                EnchantmentScreenHandler.class, AnvilScreenHandler.class, GrindstoneScreenHandler.class}) {
            assertFalse(ContainerStoredTracker.isStorageHandler(screen), screen.getName());
        }
    }
}
