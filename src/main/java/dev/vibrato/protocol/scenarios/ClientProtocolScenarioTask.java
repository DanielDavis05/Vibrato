package dev.vibrato.protocol.scenarios;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.ingame.CrafterScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.StonecutterScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerSessionC2SPacket;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.PublicPlayerSession;
import java.security.KeyPairGenerator;
import java.security.GeneralSecurityException;
import java.util.UUID;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.MessageAcknowledgmentC2SPacket;
import net.minecraft.network.message.ArgumentSignatureDataMap;
import net.minecraft.network.message.LastSeenMessageList;
import java.time.Instant;
import java.util.BitSet;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateDifficultyC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateDifficultyLockC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.MinecartCommandBlockScreen;
import net.minecraft.world.GameMode;

/** Runs only when explicitly requested; it waits for the required fixture context. */
public final class ClientProtocolScenarioTask extends Task {
    private record Requirement(String context, List<String> packets) { }
    private static final Map<String, Requirement> ACTIONS = Map.ofEntries(
            Map.entry("book_edit", new Requirement("Open a writable book", List.of("Edit Book"))),
            Map.entry("book_sign", new Requirement("Open a writable book", List.of("Edit Book"))),
            Map.entry("command_block", new Requirement("Open a command block as an authorized creative operator", List.of("Program Command Block"))),
            Map.entry("command_minecart", new Requirement("Open a command-block minecart as an authorized creative operator", List.of("Program Command Block Minecart"))),
            Map.entry("jigsaw", new Requirement("Open a jigsaw block as an authorized creative operator", List.of("Program Jigsaw Block"))),
            Map.entry("jigsaw_generate", new Requirement("Open a configured jigsaw block in an isolated world as an authorized creative operator", List.of("Program Jigsaw Block", "Jigsaw Generate"))),
            Map.entry("structure", new Requirement("Open a structure block as an authorized creative operator", List.of("Program Structure Block"))),
            Map.entry("beacon", new Requirement("Open a powered beacon with a payment item in its slot", List.of("Set Beacon Effect"))),
            Map.entry("recipe_book", new Requirement("Open the player inventory", List.of("Change Recipe Book Settings"))),
            Map.entry("debug_samples", new Requirement("Join a world; server operator permission is needed to receive samples", List.of("Debug Sample Subscription"))),
            Map.entry("advancements", new Requirement("Join a world with an available root advancement", List.of("Seen Advancements"))),
            Map.entry("pick_item", new Requirement("Join a world with an item in inventory slot 9", List.of("Pick Item"))),
            Map.entry("creative_slot", new Requirement("Join a world in creative mode with an item in the first hotbar slot", List.of("Set Creative Mode Slot"))),
            Map.entry("query_block", new Requirement("Aim at a block entity as an operator", List.of("Query Block Entity Tag"))),
            Map.entry("query_entity", new Requirement("Aim at an entity as an operator", List.of("Query Entity Tag"))),
            Map.entry("difficulty", new Requirement("Join an isolated unlocked fixture world as an operator", List.of("Change Difficulty"))),
            Map.entry("difficulty_lock", new Requirement("Join an isolated fixture world as an operator; lock state is restored", List.of("Lock Difficulty"))),
            Map.entry("suggestions", new Requirement("Join a world with commands enabled", List.of("Command Suggestions Request"))),
            Map.entry("crafter_slot", new Requirement("Open a crafter with empty first slot and cursor", List.of("Change Container Slot State"))),
            Map.entry("recipe_seen", new Requirement("Open player inventory with an unlocked, newly highlighted recipe", List.of("Set Seen Recipe"))),
            Map.entry("spectator_teleport", new Requirement("Enter spectator mode and aim at an entity", List.of("Teleport To Entity"))),
            Map.entry("ping", new Requirement("Join a Play connection", List.of("Ping Request (play)"))),
            Map.entry("statistics", new Requirement("Join a Play connection with no screen open", List.of("Client Status"))),
            Map.entry("client_information", new Requirement("Join a Play connection", List.of("Client Information (play)"))),
            Map.entry("chat", new Requirement("Join a server that permits chat", List.of("Chat Message"))),
            Map.entry("command", new Requirement("Join a server with the help command", List.of("Chat Command"))),
            Map.entry("chat_ack_empty", new Requirement("Fresh isolated connection with no pending signed messages", List.of("Acknowledge Message"))),
            Map.entry("player_session_untrusted", new Requirement("Terminal negative fixture: an expired self-generated RSA key with an invalid signature; no authenticated identity", List.of("Player Session"))),
            Map.entry("signed_command_empty", new Requirement("Fresh isolated connection with no pending signed messages and operator permission", List.of("Signed Chat Command"))),
            Map.entry("signed_command", new Requirement("Join an authenticated server with profile keys and the msg command", List.of("Signed Chat Command"))),
            Map.entry("move_ground", new Requirement("Direct protocol fixture packet with the current ground state", List.of("Set Player On Ground"))),
            Map.entry("move_position", new Requirement("Walk on fixture floor", List.of("Set Player Position"))),
            Map.entry("move_rotation", new Requirement("Turn on fixture floor", List.of("Set Player Rotation"))),
            Map.entry("move_full", new Requirement("Walk and turn on fixture floor", List.of("Set Player Position and Rotation"))),
            Map.entry("player_command", new Requirement("Sprint on fixture floor", List.of("Player Command"))),
            Map.entry("swing", new Requirement("Join fixture world", List.of("Swing Arm"))),
            Map.entry("held_item", new Requirement("Join fixture world with an item in hotbar", List.of("Set Held Item (serverbound)"))),
            Map.entry("drop_item", new Requirement("Join fixture world with an item in hotbar", List.of("Player Action"))),
            Map.entry("use_item", new Requirement("Join fixture with a writable book", List.of("Use Item"))),
            Map.entry("use_block", new Requirement("Open fixture chest", List.of("Use Item On"))),
            Map.entry("attack_block", new Requirement("Aim at fixture chest", List.of("Player Action"))),
            Map.entry("interact_entity", new Requirement("Interact with fixture armor stand", List.of("Interact"))),
            Map.entry("attack_entity", new Requirement("Attack fixture armor stand", List.of("Interact", "Swing Arm"))),
            Map.entry("update_sign", new Requirement("Open fixture sign editor", List.of("Update Sign"))),
            Map.entry("inventory_click", new Requirement("Open player inventory with an item", List.of("Click Container"))),
            Map.entry("close_container", new Requirement("Open player inventory", List.of("Close Container"))),
            Map.entry("place_recipe", new Requirement("Open player inventory with the crafting table recipe unlocked", List.of("Place Recipe"))),
            Map.entry("rename_item", new Requirement("Open a fixture anvil with an input item", List.of("Rename Item"))),
            Map.entry("container_button", new Requirement("Open fixture stonecutter with stone input", List.of("Click Container Button"))),
            Map.entry("select_trade", new Requirement("Open fixture villager trade screen", List.of("Select Trade"))),
            Map.entry("boat", new Requirement("Ride and steer fixture boat", List.of("Move Vehicle", "Paddle Boat", "Player Input"))),
            Map.entry("abilities", new Requirement("Join a world with flight permitted", List.of("Player Abilities (serverbound)")))
    );
    private final String action;
    private boolean done;
    private int phaseTicks;
    private final ProtocolScenarioPreparation preparation = new ProtocolScenarioPreparation();

    public ClientProtocolScenarioTask(String action) {
        if (!ACTIONS.containsKey(action)) throw new IllegalArgumentException("Unknown protocol scenario: " + action);
        this.action = action;
    }

    public static Set<String> supportedActions() { return ACTIONS.keySet(); }
    public static String requirements(String action) { return requirement(action).context(); }
    public static List<String> expectedPackets(String action) { return requirement(action).packets(); }
    /** Server fixture command group, or "none" when no server fixture setup is required. */
    public static String fixtureSetup(String action) {
        requirement(action);
        return switch (action) {
            case "command_block", "jigsaw", "jigsaw_generate", "structure", "beacon", "crafter_slot", "query_block", "use_block", "attack_block", "update_sign", "rename_item", "container_button" -> "blocks";
            case "query_entity", "spectator_teleport", "command_minecart", "interact_entity", "attack_entity", "select_trade" -> "entities";
            case "boat" -> "watercraft";
            case "book_edit", "book_sign", "pick_item", "creative_slot", "recipe_seen", "use_item", "place_recipe" -> "inventory";
            case "difficulty", "difficulty_lock" -> "world";
            default -> "none";
        };
    }
    /** Required fixture game mode; null means no mode change is needed. */
    public static GameMode fixtureGameMode(String action) {
        requirement(action);
        return switch (action) {
            case "inventory_click", "close_container", "place_recipe", "recipe_book", "recipe_seen", "boat", "select_trade", "rename_item", "container_button" -> GameMode.SURVIVAL;
            case "spectator_teleport" -> GameMode.SPECTATOR;
            case "command_block", "command_minecart", "jigsaw", "jigsaw_generate", "structure", "creative_slot", "abilities" -> GameMode.CREATIVE;
            default -> null;
        };
    }
    private static Requirement requirement(String action) {
        Requirement result = ACTIONS.get(action);
        if (result == null) throw new IllegalArgumentException("Unknown protocol scenario: " + action);
        return result;
    }

    @Override protected void onStart(Vibrato mod) { done = false; phaseTicks = 0; }
    @Override protected Task onTick(Vibrato mod) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) return null;
        if (!preparation.prepare(action, mod)) {
            setDebugState("Preparing fixture: " + requirements(action));
            return preparation.navigationTask();
        }
        done = switch (action) {
            case "book_edit" -> ScreenScenarios.editBook("Protocol fixture page", null);
            case "book_sign" -> ScreenScenarios.editBook("Protocol fixture page", "Fixture");
            case "command_block" -> client.currentScreen instanceof CommandBlockScreen && ScreenScenarios.programCommandBlock("say protocol fixture");
            case "command_minecart" -> client.currentScreen instanceof MinecartCommandBlockScreen && ScreenScenarios.programCommandBlock("say protocol fixture");
            case "jigsaw" -> ScreenScenarios.programJigsaw();
            case "jigsaw_generate" -> ScreenScenarios.generateJigsaw();
            case "structure" -> ScreenScenarios.programStructure();
            case "beacon" -> ScreenScenarios.setBeaconEffect();
            case "recipe_book" -> ScreenScenarios.toggleRecipeBook();
            case "debug_samples" -> {
                var hud = client.getDebugHud();
                if (!hud.shouldRenderTickCharts()) hud.toggleRenderingAndTickCharts();
                yield hud.shouldRenderTickCharts();
            }
            case "advancements" -> openAdvancements(client);
            case "pick_item" -> {
                if (client.currentScreen != null || client.player.getInventory().getStack(9).isEmpty()) yield false;
                client.interactionManager.pickFromInventory(9);
                yield true;
            }
            case "creative_slot" -> {
                int slot = ProtocolScenarioPreparation.firstHotbarItem(client);
                if (client.interactionManager.getCurrentGameMode() != GameMode.CREATIVE || slot < 0) yield false;
                client.interactionManager.clickCreativeStack(client.player.getInventory().getStack(slot).copy(), 36 + slot);
                yield true;
            }
            case "query_block" -> {
                if (!client.player.hasPermissionLevel(2) || preparation.targetBlock() == null) yield false;
                client.getNetworkHandler().getDataQueryHandler().queryBlockNbt(preparation.targetBlock(), ignored -> { });
                yield true;
            }
            case "query_entity" -> {
                if (!client.player.hasPermissionLevel(2) || preparation.targetEntity() == null) yield false;
                client.getNetworkHandler().getDataQueryHandler().queryEntityNbt(preparation.targetEntity().getId(), ignored -> { });
                yield true;
            }
            case "difficulty" -> {
                if (!client.player.hasPermissionLevel(2) || client.world == null || client.world.getLevelProperties().isDifficultyLocked()) yield false;
                var current = client.world.getDifficulty();
                var next = current == net.minecraft.world.Difficulty.NORMAL ? net.minecraft.world.Difficulty.EASY : net.minecraft.world.Difficulty.NORMAL;
                client.getNetworkHandler().sendPacket(new UpdateDifficultyC2SPacket(next));
                yield true;
            }
            case "difficulty_lock" -> {
                if (!client.player.hasPermissionLevel(2) || client.world == null) yield false;
                boolean previous = client.world.getLevelProperties().isDifficultyLocked();
                client.getNetworkHandler().sendPacket(new UpdateDifficultyLockC2SPacket(!previous));
                client.getNetworkHandler().sendPacket(new UpdateDifficultyLockC2SPacket(previous));
                yield true;
            }
            case "suggestions" -> {
                client.getNetworkHandler().sendPacket(new RequestCommandCompletionsC2SPacket(0, "help "));
                yield true;
            }
            case "crafter_slot" -> toggleCrafterSlot(client);
            case "recipe_seen" -> displayRecipe(client);
            case "spectator_teleport" -> {
                if (!client.player.isSpectator() || preparation.targetEntity() == null) yield false;
                client.getNetworkHandler().sendPacket(new SpectatorTeleportC2SPacket(preparation.targetEntity().getUuid()));
                yield true;
            }
            case "ping" -> {
                client.getNetworkHandler().sendPacket(new QueryPingC2SPacket(net.minecraft.util.Util.getMeasuringTimeMs()));
                yield true;
            }
            case "statistics" -> {
                if (client.currentScreen != null) yield false;
                client.setScreen(new StatsScreen(null, client.player.getStatHandler()));
                yield true;
            }
            case "client_information" -> {
                client.options.sendClientSettings();
                yield true;
            }
            case "chat" -> {
                client.getNetworkHandler().sendChatMessage("Protocol fixture message");
                yield true;
            }
            case "command" -> {
                client.getNetworkHandler().sendChatCommand("help");
                yield true;
            }
            case "chat_ack_empty" -> {
                client.getNetworkHandler().sendPacket(new MessageAcknowledgmentC2SPacket(0));
                yield true;
            }
            case "player_session_untrusted" -> {
                // Explicit negative test: the key is generated locally and has no Mojang signature.
                try {
                    var generator = KeyPairGenerator.getInstance("RSA");
                    generator.initialize(1024);
                    var publicKey = generator.generateKeyPair().getPublic();
                    var keyData = new PlayerPublicKey.PublicKeyData(Instant.EPOCH, publicKey, new byte[256]);
                    client.getNetworkHandler().sendPacket(new PlayerSessionC2SPacket(
                            new PublicPlayerSession.Serialized(UUID.randomUUID(), keyData)));
                    yield true;
                } catch (GeneralSecurityException e) {
                    throw new IllegalStateException("Cannot generate negative fixture key", e);
                }
            }
            case "signed_command_empty" -> {
                if (!client.player.hasPermissionLevel(2)) yield false;
                client.getNetworkHandler().sendPacket(new ChatCommandSignedC2SPacket("time query daytime", Instant.now(), 0L,
                        ArgumentSignatureDataMap.EMPTY, new LastSeenMessageList.Acknowledgment(0, new BitSet())));
                yield true;
            }
            case "signed_command" -> {
                if (!client.providesProfileKeys()) yield false;
                client.getNetworkHandler().sendChatCommand("msg " + client.player.getGameProfile().getName() + " protocol fixture");
                yield true;
            }
            case "move_position", "move_rotation", "move_full", "player_command", "boat" -> drive(client);
            case "move_ground" -> {
                client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(client.player.isOnGround()));
                yield true;
            }
            case "swing" -> { client.player.swingHand(Hand.MAIN_HAND); yield true; }
            case "held_item" -> {
                client.player.getInventory().selectedSlot = (client.player.getInventory().selectedSlot + 1) % 9;
                client.interactionManager.tick();
                yield true;
            }
            case "drop_item" -> {
                int slot = ProtocolScenarioPreparation.firstHotbarItem(client);
                if (slot < 0) yield false;
                client.player.getInventory().selectedSlot = slot;
                client.interactionManager.tick();
                yield client.player.dropSelectedItem(false);
            }
            case "use_item", "use_block" -> true; // Preparation performed the native interaction.
            case "attack_block" -> {
                var pos = preparation.targetBlock();
                if (pos == null) yield false;
                yield client.interactionManager.attackBlock(pos, Direction.UP);
            }
            case "interact_entity" -> {
                if (preparation.targetEntity() == null) yield false;
                client.interactionManager.interactEntity(client.player, preparation.targetEntity(), Hand.MAIN_HAND);
                yield true;
            }
            case "attack_entity" -> {
                if (preparation.targetEntity() == null) yield false;
                client.interactionManager.attackEntity(client.player, preparation.targetEntity());
                client.player.swingHand(Hand.MAIN_HAND);
                yield true;
            }
            case "update_sign" -> {
                if (!(client.currentScreen instanceof SignEditScreen screen)) yield false;
                for (char c : "Protocol fixture".toCharArray()) screen.charTyped(c, 0);
                screen.close();
                yield true;
            }
            case "inventory_click" -> clickInventory(client);
            case "close_container" -> {
                if (!(client.currentScreen instanceof InventoryScreen)) yield false;
                client.player.closeHandledScreen();
                yield true;
            }
            case "place_recipe" -> {
                if (!(client.currentScreen instanceof InventoryScreen screen)) yield false;
                var recipe = client.getNetworkHandler().getRecipeManager().get(Identifier.of("minecraft", "crafting_table"));
                if (recipe.isEmpty() || !client.player.getRecipeBook().contains(recipe.get())) yield false;
                client.interactionManager.clickRecipe(screen.getScreenHandler().syncId, recipe.get(), false);
                yield true;
            }
            case "rename_item" -> {
                if (!(client.currentScreen instanceof AnvilScreen screen)) yield false;
                boolean changed = false;
                for (var child : screen.children()) {
                    if (child instanceof TextFieldWidget field && field.isActive()) {
                        field.setText("Protocol fixture item");
                        changed = true;
                        break;
                    }
                }
                yield changed;
            }
            case "container_button" -> {
                if (!(client.currentScreen instanceof StonecutterScreen screen)) yield false;
                var handler = screen.getScreenHandler();
                if (handler.getAvailableRecipeCount() == 0 || !handler.onButtonClick(client.player, 0)) yield false;
                client.interactionManager.clickButton(handler.syncId, 0);
                yield true;
            }
            case "select_trade" -> {
                if (!(client.currentScreen instanceof MerchantScreen screen) || screen.getScreenHandler().getRecipes().isEmpty()) yield false;
                client.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(0));
                yield true;
            }
            case "abilities" -> {
                if (!client.player.getAbilities().allowFlying) yield false;
                client.player.getAbilities().flying = !client.player.getAbilities().flying;
                client.player.sendAbilitiesUpdate();
                yield true;
            }
            default -> false;
        };
        if (!done) setDebugState("Waiting for fixture: " + requirements(action));
        return null;
    }
    private boolean drive(MinecraftClient client) {
        phaseTicks++;
        if ((phaseTicks == 1 && action.equals("move_rotation")) || action.equals("move_full"))
            client.player.setYaw(client.player.getYaw() + (action.equals("move_full") ? 2.0f : 35.0f));
        if (action.equals("move_position") || action.equals("move_full") || action.equals("player_command") || action.equals("boat"))
            client.options.forwardKey.setPressed(true);
        if (action.equals("player_command")) client.options.sprintKey.setPressed(true);
        if (phaseTicks < 20) return false;
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        return true;
    }
    private static boolean clickInventory(MinecraftClient client) {
        if (!(client.currentScreen instanceof InventoryScreen screen)) return false;
        var handler = screen.getScreenHandler();
        if (!handler.getCursorStack().isEmpty()) return false;
        for (int i = 9; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack()) {
                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                return true;
            }
        }
        return false;
    }
    private static boolean toggleCrafterSlot(MinecraftClient client) {
        if (!(client.currentScreen instanceof CrafterScreen screen) || client.player.isSpectator()) return false;
        var handler = screen.getScreenHandler();
        if (!handler.getSlot(0).getStack().isEmpty() || !handler.getCursorStack().isEmpty()) return false;
        boolean enabled = handler.isSlotDisabled(0);
        handler.setSlotEnabled(0, enabled);
        client.interactionManager.slotChangedState(0, handler.syncId, enabled);
        return true;
    }
    private static boolean displayRecipe(MinecraftClient client) {
        if (!(client.currentScreen instanceof InventoryScreen screen)) return false;
        var book = client.player.getRecipeBook();
        for (var recipe : client.getNetworkHandler().getRecipeManager().values()) {
            if (book.contains(recipe) && book.shouldDisplay(recipe)) {
                var widget = screen.getRecipeBookWidget();
                if (widget.isOpen()) widget.toggleOpen();
                widget.toggleOpen();
                return true;
            }
        }
        return false;
    }
    private static boolean openAdvancements(MinecraftClient client) {
        if (client.currentScreen != null) return false;
        var manager = client.getNetworkHandler().getAdvancementHandler();
        var roots = manager.getManager().getRoots().iterator();
        if (!roots.hasNext()) return false;
        PlacedAdvancement root = roots.next();
        client.setScreen(new AdvancementsScreen(manager));
        manager.selectTab(root.getAdvancementEntry(), true);
        client.setScreen(null);
        return true;
    }
    @Override protected void onStop(Vibrato mod, Task interruptTask) {
        var client = MinecraftClient.getInstance();
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
    }
    @Override public boolean isFinished(Vibrato mod) { return done; }
    @Override protected boolean isEqual(Task other) {
        return other instanceof ClientProtocolScenarioTask task && action.equals(task.action);
    }
    @Override protected String toDebugString() { return "Protocol scenario " + action; }
}
