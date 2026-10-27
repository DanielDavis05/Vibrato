package dev.vibrato.social;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Categorized chat lines with {@code {placeholder}} slots. Output varies in casing and trailing punctuation, is
 * deterministic for a given generator, and never repeats the previous line of the same category.
 * <p>
 * Templates never claim to be a real person, so a bot using them should not be mistaken for one.
 */
public final class MessageBank {

    public static final List<String> CATEGORIES = List.of(
            "greeting", "farewell", "asking_for_help", "sharing", "reacting_to_death",
            "reacting_to_loot", "small_talk", "trade_offer");

    private static final Map<String, List<String>> TEMPLATES = Map.of(
            "greeting", List.of("hey {player}", "hi {player}", "hello {player}", "oh hey {player}", "yo {player}"),
            "farewell", List.of("gotta head out, see you {player}", "later {player}", "bye for now {player}",
                    "catch you later"),
            "asking_for_help", List.of("anyone got spare {item}? i'd trade for it",
                    "does anyone have {item} to spare", "need {count} {item}, could use a hand",
                    "looking for {item}, if anyone's got some"),
            "sharing", List.of("i have extra {item}, want some {player}", "dropping some {item} for you {player}",
                    "here's some {item}, take what you need"),
            "reacting_to_death", List.of("oof, that was rough", "well that hurt", "rip, going back for my stuff",
                    "that one was my fault"),
            "reacting_to_loot", List.of("nice, {item}", "oh good, {item}", "that's a solid find"),
            "small_talk", List.of("nice build over there", "this seed is kinda wild", "anyone up for mining later",
                    "the sunset looks nice from here"),
            "trade_offer", List.of("i'll trade {count} {item} for emeralds {player}",
                    "anyone want to trade {item} for emeralds?"));

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    private final Map<String, String> _last = new HashMap<>();

    public String render(String category, RandomGenerator rng, Map<String, String> context) {
        List<String> templates = TEMPLATES.get(category);
        if (templates == null) {
            throw new IllegalArgumentException("Unknown message category: " + category);
        }
        String previous = _last.get(category);
        String line;
        do {
            String template = templates.get(rng.nextInt(templates.size()));
            line = style(fill(template, context), rng);
        } while (line.equals(previous));
        _last.put(category, line);
        return line;
    }

    /** Placeholders present in the template; used by tests to check every slot has a value. */
    static List<String> placeholdersOf(String template) {
        List<String> names = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    static List<String> templatesOf(String category) {
        return TEMPLATES.get(category);
    }

    private static String fill(String template, Map<String, String> context) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = context.get(m.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Missing placeholder '" + m.group(1) + "' for: " + template);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String style(String text, RandomGenerator rng) {
        String cased = switch (rng.nextInt(3)) {
            case 0 -> text.toLowerCase(Locale.ROOT);
            case 1 -> Character.toUpperCase(text.charAt(0)) + text.substring(1);
            default -> text;
        };
        String[] endings = {"", "", ".", "!", "?"};
        return cased + endings[rng.nextInt(endings.length)];
    }
}
