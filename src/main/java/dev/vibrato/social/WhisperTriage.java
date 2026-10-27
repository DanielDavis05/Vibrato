package dev.vibrato.social;

import dev.vibrato.butler.WhisperChecker;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Picks out whispers that are conversation rather than commands, so {@link ReplyToWhisperTask} can answer them
 * without stepping on the Butler. Pure: the caller supplies the command-name check.
 */
public final class WhisperTriage {

    public record Whisper(String from, String text) {}

    private WhisperTriage() {}

    public static boolean isConversational(String text, String commandPrefix, Predicate<String> isCommandName) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(commandPrefix) || trimmed.startsWith("`")) {
            return false;
        }
        String firstWord = trimmed.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return !isCommandName.test(firstWord);
    }

    /** Category for an answer: greetings get a greeting, anything else small talk. */
    public static String categoryFor(String text) {
        String first = text.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (first) {
            case "hi", "hey", "hello", "yo", "sup" -> "greeting";
            default -> "small_talk";
        };
    }

    /** Extracts a whisper from a raw chat line, given the server's whisper formats. */
    public static Optional<Whisper> parse(String ourName, String[] formats, String raw) {
        for (String format : formats) {
            var result = WhisperChecker.tryParse(ourName, format, raw);
            if (result != null && result.from != null && result.message != null) {
                return Optional.of(new Whisper(result.from, result.message));
            }
        }
        return Optional.empty();
    }
}
