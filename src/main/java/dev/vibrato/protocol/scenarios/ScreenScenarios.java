package dev.vibrato.protocol.scenarios;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractCommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.JigsawBlockScreen;
import net.minecraft.client.gui.screen.ingame.StructureBlockScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.TranslatableTextContent;

/** Explicit protocol-fixture UI actions. The fixture must open the appropriate vanilla screen.
 * A true result means the UI action was invoked, not that the server accepted or observed it.
 * These actions are intentionally not registered as normal Director activities.
 */
public final class ScreenScenarios {
    private ScreenScenarios() { }

    /** Edit a writable book on the current screen; optionally sign it with the supplied title. */
    public static boolean editBook(String page, String title) {
        Screen current = MinecraftClient.getInstance().currentScreen;
        if (!(current instanceof BookEditScreen screen) || page == null || page.isEmpty()
                || (title != null && (title.isEmpty() || title.length() > 16))) return false;
        for (char c : page.toCharArray()) screen.charTyped(c, 0);
        if (title == null) return press(screen, "gui.done");
        if (!press(screen, "book.signButton")) return false;
        for (char c : title.toCharArray()) screen.charTyped(c, 0);
        return press(screen, "book.finalizeButton");
    }

    /** Change and submit the command in an open command block or command minecart screen. */
    public static boolean programCommandBlock(String command) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (!(screen instanceof AbstractCommandBlockScreen) || command == null) return false;
        TextFieldWidget field = firstTextField(screen);
        if (field == null) return false;
        field.setText(command);
        return press(screen, "gui.done");
    }

    /** Submit the current, server-provided settings on an open jigsaw screen. */
    public static boolean programJigsaw() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof JigsawBlockScreen && press(screen, "gui.done");
    }

    /** Generate from the current jigsaw settings; use only in an isolated fixture world. */
    public static boolean generateJigsaw() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof JigsawBlockScreen && press(screen, "jigsaw_block.generate");
    }

    /** Submit the current settings on an open structure block screen. */
    public static boolean programStructure() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof StructureBlockScreen && press(screen, "gui.done");
    }

    /** Toggle the recipe book of the open player inventory via its vanilla widget. */
    public static boolean toggleRecipeBook() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (!(screen instanceof InventoryScreen inventory)) return false;
        inventory.getRecipeBookWidget().toggleOpen();
        return true;
    }

    /** Select the first primary beacon effect and confirm the payment in the open screen. */
    public static boolean setBeaconEffect() {
        Screen current = MinecraftClient.getInstance().currentScreen;
        if (!(current instanceof BeaconScreen screen) || !screen.getScreenHandler().hasPayment()
                || screen.getScreenHandler().getProperties() < 1) return false;
        // Vanilla's 230x219 beacon screen places the first primary effect and Done here.
        int left = (screen.width - 230) / 2;
        int top = (screen.height - 219) / 2;
        screen.handledScreenTick();
        if (!screen.mouseClicked(left + 53, top + 23, 0)) return false;
        screen.handledScreenTick();
        return screen.mouseClicked(left + 165, top + 108, 0);
    }

    private static TextFieldWidget firstTextField(Screen screen) {
        for (Element child : screen.children()) {
            if (child instanceof TextFieldWidget field) return field;
        }
        return null;
    }

    private static boolean press(Screen screen, String translationKey) {
        for (Element child : screen.children()) {
            if (child instanceof ButtonWidget button && button.visible && button.active
                    && button.getMessage().getContent() instanceof TranslatableTextContent text
                    && text.getKey().equals(translationKey)) {
                button.onPress();
                return true;
            }
        }
        return false;
    }
}
