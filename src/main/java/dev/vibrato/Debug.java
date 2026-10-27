package dev.vibrato;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Debug library or use Minecraft's built in debugger
public class Debug {

    public static void logInternal(String message) {
        System.out.println("VIBRATO: " + message);
    }

    public static void logInternal(String format, Object... args) {
        logInternal(String.format(format, args));
    }

    public static Vibrato jankModInstance;

    private static String getLogPrefix() {
        if (jankModInstance != null) {
            return jankModInstance.getModSettings().getChatLogPrefix();
        }
        return "[Vibrato] ";
    }

    private static MutableText chatPrefix() {
        return Text.literal(getLogPrefix()).formatted(Formatting.AQUA, Formatting.BOLD);
    }

    /** Command references can be selected to edit them in chat without executing them. */
    private static MutableText chatBody(String message) {
        MutableText result = Text.empty();
        String commandPrefix = jankModInstance == null ? "@" : jankModInstance.getModSettings().getCommandPrefix();
        if (commandPrefix.isEmpty()) return result.append(message);
        Matcher matcher = Pattern.compile(Pattern.quote(commandPrefix) + "[a-zA-Z0-9_]+").matcher(message);
        int end = 0;
        while (matcher.find()) {
            result.append(message.substring(end, matcher.start()));
            String command = matcher.group();
            result.append(Text.literal(command).styled(style -> style.withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command + " "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Select to edit in chat")))));
            end = matcher.end();
        }
        return result.append(message.substring(end));
    }

    public static void logMessage(String message, boolean prefix) {
        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().player != null) {
            MutableText text = Text.empty();
            if (prefix) text.append(chatPrefix());
            MinecraftClient.getInstance().player.sendMessage(text.append(chatBody(message)), false);
        } else {
            logInternal(message);
        }
    }

    public static void logMessage(String message) {
        logMessage(message, true);
    }

    public static void logMessage(String format, Object... args) {
        logMessage(String.format(format, args));
    }

    public static void logWarning(String message) {
        logInternal("WARNING: " + message);
        if (jankModInstance != null && !jankModInstance.getModSettings().shouldHideAllWarningLogs()) {
            if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.empty().append(chatPrefix()).append(chatBody(message).formatted(Formatting.RED)), false);
                //MinecraftClient.getInstance().player.sendChatMessage(msg);
            }
        }
    }

    public static void logWarning(String format, Object... args) {
        logWarning(String.format(format, args));
    }

    public static void logError(String message) {
        String stacktrace = getStack(2);
        System.err.println(message);
        System.err.println("at:");
        System.err.println(stacktrace);
        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.empty().append(chatPrefix()).append(Text.literal("[ERROR] " + message + "\nat:\n" + stacktrace).formatted(Formatting.RED)), false);
        }
    }

    public static void logError(String format, Object... args) {
        logError(String.format(format, args));
    }

    public static void logStack() {
        logInternal("STACKTRACE: \n" + getStack(2));
    }

    private static String getStack(int toSkip) {
        StringBuilder stacktrace = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (toSkip-- <= 0) {
                stacktrace.append(ste.toString()).append("\n");
            }
        }
        return stacktrace.toString();
    }
}
