package dev.vibrato.protocol.scenarios;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
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
            Map.entry("abilities", new Requirement("Join a world with flight permitted", List.of("Player Abilities (serverbound)")))
    );
    private final String action;
    private boolean done;

    public ClientProtocolScenarioTask(String action) {
        if (!ACTIONS.containsKey(action)) throw new IllegalArgumentException("Unknown protocol scenario: " + action);
        this.action = action;
    }

    public static Set<String> supportedActions() { return ACTIONS.keySet(); }
    public static String requirements(String action) { return requirement(action).context(); }
    public static List<String> expectedPackets(String action) { return requirement(action).packets(); }
    private static Requirement requirement(String action) {
        Requirement result = ACTIONS.get(action);
        if (result == null) throw new IllegalArgumentException("Unknown protocol scenario: " + action);
        return result;
    }

    @Override protected void onStart(Vibrato mod) { done = false; }
    @Override protected Task onTick(Vibrato mod) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) return null;
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
                if (client.interactionManager.getCurrentGameMode() != GameMode.CREATIVE
                        || client.player.getInventory().getStack(0).isEmpty()) yield false;
                client.interactionManager.clickCreativeStack(client.player.getInventory().getStack(0).copy(), 36);
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
    @Override protected void onStop(Vibrato mod, Task interruptTask) { }
    @Override public boolean isFinished(Vibrato mod) { return done; }
    @Override protected boolean isEqual(Task other) {
        return other instanceof ClientProtocolScenarioTask task && action.equals(task.action);
    }
    @Override protected String toDebugString() { return "Protocol scenario " + action; }
}
