package dev.vibrato.commandsystem;

import dev.vibrato.TaskCatalogue;
import dev.vibrato.util.Dimension;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Local chat completion. The returned ranges are relative to the complete chat input. */
public final class CommandSuggestions {
    private CommandSuggestions() {}

    public static CompletableFuture<Suggestions> suggest(CommandExecutor executor, String input, int cursor) {
        String prefix = executor.getCommandPrefix();
        String beforeCursor = input.substring(0, cursor);
        int segment = beforeCursor.lastIndexOf(';') + 1;
        int start = segment == 0 ? prefix.length() : segment;
        while (start < cursor && Character.isWhitespace(input.charAt(start))) start++;
        int wordEnd = start;
        while (wordEnd < cursor && !Character.isWhitespace(input.charAt(wordEnd))) wordEnd++;
        if (wordEnd == cursor) {
            SuggestionsBuilder builder = new SuggestionsBuilder(beforeCursor, start);
            String partial = beforeCursor.substring(start).toLowerCase(Locale.ROOT);
            executor.allCommands().stream().sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .filter(command -> command.getName().startsWith(partial))
                    .forEach(command -> builder.suggest(command.getName(),
                            new LiteralMessage(prefix + command.getHelpRepresentation() + " — " + command.getDescription())));
            return builder.buildFuture();
        }
        Command command = executor.get(beforeCursor.substring(start, wordEnd));
        int argumentStart = wordEnd;
        while (argumentStart < cursor && Character.isWhitespace(input.charAt(argumentStart))) argumentStart++;
        String arguments = beforeCursor.substring(argumentStart);
        int tokenStart = cursor;
        while (tokenStart > argumentStart && !Character.isWhitespace(input.charAt(tokenStart - 1))
                && input.charAt(tokenStart - 1) != '[' && input.charAt(tokenStart - 1) != ',') tokenStart--;
        SuggestionsBuilder builder = new SuggestionsBuilder(beforeCursor, tokenStart);
        if (command == null) return builder.buildFuture();
        String token = beforeCursor.substring(tokenStart).toLowerCase(Locale.ROOT);
        Collection<String> values = java.util.List.of();
        String name = command.getName();
        if (name.equals("goto") && validGotoCoordinates(beforeCursor.substring(argumentStart, tokenStart))) {
            values = Arrays.stream(Dimension.values()).map(d -> d.name().toLowerCase(Locale.ROOT)).toList();
        } else if (name.equals("locate_structure") && !arguments.contains(" ")) {
            values = java.util.List.of("desert_temple", "stronghold");
        } else if ((name.equals("get") || name.equals("equip") || name.equals("deposit"))
                && (tokenStart == argumentStart || arguments.startsWith("[") && (tokenStart == argumentStart + 1 || beforeCursor.substring(argumentStart, tokenStart).stripTrailing().endsWith(",")))) {
            values = TaskCatalogue.resourceNames();
        } else if (name.equals("inventory") && tokenStart == argumentStart) {
            values = TaskCatalogue.resourceNames();
        }
        values.stream().sorted().filter(value -> value.startsWith(token)).forEach(builder::suggest);
        return builder.buildFuture();
    }
    private static boolean validGotoCoordinates(String previous) {
        String trimmed = previous.trim();
        if (trimmed.isEmpty()) return true;
        String[] coordinates = trimmed.split("\\s+");
        if (coordinates.length > 3) return false;
        for (String coordinate : coordinates) {
            try {
                Integer.parseInt(coordinate);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }
}
