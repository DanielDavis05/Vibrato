package dev.vibrato.protocol.scenarios;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasks.movement.GetWithinRangeOfBlockTask;
import dev.vibrato.tasks.movement.GetToEntityTask;
import dev.vibrato.util.helpers.LookHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Opens the vanilla fixture UI, waiting for server synchronization between interactions. */
final class ProtocolScenarioPreparation {
    private int nextInteraction;
    private int tick;
    private Entity targetEntity;
    private BlockPos targetBlock;
    private Task navigation;

    Task navigationTask() { return navigation; }
    Entity targetEntity() { return targetEntity; }
    BlockPos targetBlock() { return targetBlock; }

    boolean prepare(String action, Vibrato mod) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.interactionManager == null) return false;
        tick++;
        navigation = null;
        return switch (action) {
            case "book_edit", "book_sign", "use_item" -> prepareBook(client);
            case "command_block" -> prepareBlock(client, mod, Blocks.COMMAND_BLOCK, CommandBlockScreen.class);
            case "command_minecart" -> prepareMinecart(client, mod);
            case "jigsaw", "jigsaw_generate" -> prepareBlock(client, mod, Blocks.JIGSAW, JigsawBlockScreen.class);
            case "structure" -> prepareBlock(client, mod, Blocks.STRUCTURE_BLOCK, StructureBlockScreen.class);
            case "beacon" -> prepareBeacon(client, mod);
            case "crafter_slot" -> prepareBlock(client, mod, Blocks.CRAFTER, CrafterScreen.class);
            case "rename_item" -> prepareWorkstationInput(client, mod, Blocks.ANVIL, AnvilScreen.class, false);
            case "container_button" -> prepareWorkstationInput(client, mod, Blocks.STONECUTTER, StonecutterScreen.class, true);
            case "use_block" -> prepareBlock(client, mod, Blocks.CHEST, GenericContainerScreen.class);
            case "update_sign" -> prepareBlock(client, mod, Blocks.OAK_SIGN, SignEditScreen.class);
            case "attack_block" -> prepareAttackBlock(client, mod);
            case "select_trade" -> prepareVillager(client, mod);
            case "boat" -> prepareBoat(client, mod);
            case "recipe_book", "recipe_seen", "inventory_click", "close_container", "place_recipe" -> prepareInventory(client);
            case "query_block" -> prepareQueryBlock(client, mod);
            case "query_entity", "spectator_teleport", "interact_entity", "attack_entity" -> prepareQueryEntity(client, mod);
            case "pick_item" -> preparePickItem(client);
            case "creative_slot" -> firstHotbarItem(client) >= 0;
            default -> true;
        };
    }

    private boolean prepareInventory(MinecraftClient client) {
        if (client.currentScreen instanceof InventoryScreen) return true;
        if (client.currentScreen != null || !ready()) return false;
        client.setScreen(new InventoryScreen(client.player));
        return false;
    }

    private boolean prepareBook(MinecraftClient client) {
        if (client.currentScreen instanceof BookEditScreen) return true;
        if (client.currentScreen != null || !ready()) return false;
        var inventory = client.player.getInventory();
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).isOf(Items.WRITABLE_BOOK)) { slot = i; break; }
        }
        if (slot < 0) return false;
        if (slot >= 9) {
            client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, slot, 0, SlotActionType.SWAP, client.player);
            inventory.selectedSlot = 0;
            return false;
        }
        inventory.selectedSlot = slot;
        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        return false;
    }

    private boolean prepareBlock(MinecraftClient client, Vibrato mod, Block block, Class<?> screenType) {
        if (screenType.isInstance(client.currentScreen)) return true;
        if (client.currentScreen != null) return false;
        BlockPos pos = findBlock(client, block);
        if (pos == null) return false;
        if (client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 16.0) {
            navigation = new GetWithinRangeOfBlockTask(pos, 3);
            return false;
        }
        if (!ready()) return false;
        Vec3d center = Vec3d.ofCenter(pos);
        LookHelper.lookAtInstant(mod, center);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(center, Direction.UP, pos, false));
        return false;
    }

    private boolean prepareMinecart(MinecraftClient client, Vibrato mod) {
        if (client.currentScreen instanceof MinecartCommandBlockScreen) return true;
        if (client.currentScreen != null || !ready()) return false;
        var nearby = client.world.getEntitiesByClass(CommandBlockMinecartEntity.class,
                client.player.getBoundingBox().expand(6), e -> e.isAlive());
        if (nearby.isEmpty()) return false;
        Entity entity = nearby.getFirst();
        if (client.player.squaredDistanceTo(entity) > 25.0) return false;
        LookHelper.lookAtInstant(mod, entity.getBoundingBox().getCenter());
        client.interactionManager.interactEntity(client.player, entity, Hand.MAIN_HAND);
        return false;
    }

    private boolean prepareWorkstationInput(MinecraftClient client, Vibrato mod, Block block,
                                            Class<?> screenType, boolean stoneOnly) {
        if (!screenType.isInstance(client.currentScreen)) return prepareBlock(client, mod, block, screenType);
        var screen = (HandledScreen<?>) client.currentScreen;
        var handler = screen.getScreenHandler();
        if (handler.getSlot(0).hasStack()) return true;
        if (!ready() || !handler.getCursorStack().isEmpty()) return false;
        for (int i = 2; i < handler.slots.size(); i++) {
            var stack = handler.getSlot(i).getStack();
            if (handler.getSlot(i).inventory == client.player.getInventory()
                    && !stack.isEmpty() && (!stoneOnly || stack.isOf(Items.STONE))) {
                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, client.player);
                return false;
            }
        }
        return false;
    }

    private boolean prepareBeacon(MinecraftClient client, Vibrato mod) {
        if (!(client.currentScreen instanceof BeaconScreen screen))
            return prepareBlock(client, mod, Blocks.BEACON, BeaconScreen.class);
        var handler = screen.getScreenHandler();
        if (handler.hasPayment()) return handler.getProperties() > 0;
        if (!ready() || !handler.getCursorStack().isEmpty()) return false;
        for (int i = 1; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).getStack().isOf(Items.IRON_INGOT)) {
                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(handler.syncId, 0, 1, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                break;
            }
        }
        return false;
    }

    private boolean prepareAttackBlock(MinecraftClient client, Vibrato mod) {
        targetBlock = findBlock(client, Blocks.CHEST);
        if (targetBlock == null) return false;
        if (client.player.squaredDistanceTo(Vec3d.ofCenter(targetBlock)) > 16.0) {
            navigation = new GetWithinRangeOfBlockTask(targetBlock, 3);
            return false;
        }
        LookHelper.lookAtInstant(mod, Vec3d.ofCenter(targetBlock));
        return true;
    }

    private boolean prepareVillager(MinecraftClient client, Vibrato mod) {
        if (client.currentScreen instanceof MerchantScreen) return true;
        if (client.currentScreen != null) return false;
        var villagers = client.world.getEntitiesByClass(VillagerEntity.class,
                client.player.getBoundingBox().expand(9), Entity::isAlive);
        if (villagers.isEmpty()) return false;
        return interactNear(client, mod, villagers.getFirst());
    }

    private boolean prepareBoat(MinecraftClient client, Vibrato mod) {
        if (client.player.getVehicle() instanceof BoatEntity) return true;
        if (client.currentScreen != null) return false;
        var boats = client.world.getEntitiesByClass(BoatEntity.class,
                client.player.getBoundingBox().expand(12), Entity::isAlive);
        if (boats.isEmpty()) return false;
        return interactNear(client, mod, boats.getFirst());
    }

    private boolean interactNear(MinecraftClient client, Vibrato mod, Entity entity) {
        if (client.player.squaredDistanceTo(entity) > 16.0) {
            navigation = new GetToEntityTask(entity, 3);
            return false;
        }
        if (!ready()) return false;
        LookHelper.lookAtInstant(mod, entity.getBoundingBox().getCenter());
        client.interactionManager.interactEntity(client.player, entity, Hand.MAIN_HAND);
        return false;
    }

    private boolean prepareQueryBlock(MinecraftClient client, Vibrato mod) {
        targetBlock = findBlock(client, Blocks.COMMAND_BLOCK);
        if (targetBlock == null || client.world.getBlockEntity(targetBlock) == null) return false;
        LookHelper.lookAtInstant(mod, Vec3d.ofCenter(targetBlock));
        return true;
    }

    private boolean prepareQueryEntity(MinecraftClient client, Vibrato mod) {
        var entities = client.world.getEntitiesByClass(ArmorStandEntity.class,
                client.player.getBoundingBox().expand(8), Entity::isAlive);
        if (entities.isEmpty()) return false;
        targetEntity = entities.getFirst();
        LookHelper.lookAtInstant(mod, targetEntity.getBoundingBox().getCenter());
        return true;
    }

    private boolean preparePickItem(MinecraftClient client) {
        if (!client.player.getInventory().getStack(9).isEmpty()) return true;
        if (client.currentScreen != null || !ready()) return false;
        int slot = firstHotbarItem(client);
        if (slot >= 0) client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, 9, slot, SlotActionType.SWAP, client.player);
        return false;
    }

    static int firstHotbarItem(MinecraftClient client) {
        for (int i = 0; i < 9; i++) if (!client.player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }

    private BlockPos findBlock(MinecraftClient client, Block block) {
        for (BlockPos pos : BlockPos.iterateOutwards(client.player.getBlockPos(), 6, 3, 6)) {
            if (client.world.getBlockState(pos).isOf(block)) return pos.toImmutable();
        }
        return null;
    }

    private boolean ready() {
        if (tick < nextInteraction) return false;
        nextInteraction = tick + 8;
        return true;
    }
}
