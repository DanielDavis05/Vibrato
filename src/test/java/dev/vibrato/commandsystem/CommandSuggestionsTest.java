package dev.vibrato.commandsystem;

import dev.vibrato.Vibrato;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandSuggestionsTest {
    private static final List<Command> COMMANDS = List.of(command("get"), command("goto"), command("help"), command("locate_structure"));

    private static Command command(String name) {
        return new Command(name, "description") {
            @Override protected void call(Vibrato mod, ArgParser parser) {}
        };
    }

    private static List<String> completions(String prefix, String input) {
        return CommandSuggestions.suggest(prefix, COMMANDS, input, input.length()).join().getList().stream()
                .map(suggestion -> suggestion.apply(input)).toList();
    }

    @Test void completesCommandNamesAndChainedCommands() {
        assertEquals(List.of("@get"), completions("@", "@ge"));
        assertEquals(List.of("@get diamond; goto"), completions("@", "@get diamond; go"));
        assertEquals(List.of("!get"), completions("!", "!ge"));
    }

    @Test void completesOnlySupportedArgumentPositions() {
        assertEquals(List.of("@goto 20 nether"), completions("@", "@goto 20 ne"));
        assertTrue(completions("@", "@goto nether ne").isEmpty());
        assertEquals(List.of("@locate_structure stronghold"), completions("@", "@locate_structure str"));
        assertTrue(completions("@", "@locate_structure stronghold str").isEmpty());
        assertEquals(List.of("@help all"), completions("@", "@help a"));
    }
}
