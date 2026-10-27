package dev.vibrato.social;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which slash commands a bot may issue. Default deny: only the allow-list passes, and the never-list is
 * refused with its own reason. Arguments are validated, so an allowed name cannot smuggle in a selector or a chain.
 */
public final class CommandPolicy {

    public record Decision(boolean allowed, String reason) {

        static Decision allow() {
            return new Decision(true, "");
        }

        static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    /** Commands that are never allowed, whatever the arguments. */
    public static final Set<String> NEVER = Set.of(
            "kill", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "stop", "whitelist", "gamemode",
            "tp", "teleport", "give", "clear", "fill", "setblock", "summon", "execute", "gamerule", "difficulty",
            "effect", "enchant", "xp", "time", "weather", "save-all", "reload", "perf");

    public static final String TRIGGER = "trigger";

    /** Allowed names that take no arguments, e.g. for activity parameters. */
    public static final List<String> SAFE_NO_ARGUMENT = List.of("list", "seed", "help");

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern TOPIC = Pattern.compile("[a-z_]{1,32}");
    private static final int MAX_TEXT = 256;

    /** Default policy: trigger is off. */
    public static final CommandPolicy DEFAULT = new CommandPolicy(false);

    private final boolean _allowTrigger;

    public CommandPolicy(boolean allowTrigger) {
        _allowTrigger = allowTrigger;
    }

    public Decision check(String line) {
        if (line == null || line.isBlank()) {
            return Decision.deny("empty command");
        }
        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if (c < 0x20 || c == 0x7f || c == ';' || c == '§') {
                return Decision.deny("unsafe character in command");
            }
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        String[] parts = trimmed.split(" +");
        String name = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        if (NEVER.contains(name)) {
            return Decision.deny("'" + name + "' is never allowed");
        }
        switch (name) {
            case "list", "seed" -> {
                return args.length == 0 ? Decision.allow() : Decision.deny("'" + name + "' takes no arguments");
            }
            case "help" -> {
                if (args.length > 1 || (args.length == 1 && !TOPIC.matcher(args[0]).matches())) {
                    return Decision.deny("help takes at most one plain topic");
                }
                return Decision.allow();
            }
            case "me" -> {
                return text(args, 0);
            }
            case "msg", "tell", "w" -> {
                if (args.length < 2) {
                    return Decision.deny("'" + name + "' needs a player and a message");
                }
                if (!PLAYER_NAME.matcher(args[0]).matches()) {
                    return Decision.deny("invalid player name '" + args[0] + "'");
                }
                return text(args, 1);
            }
            case TRIGGER -> {
                return _allowTrigger ? Decision.allow() : Decision.deny("trigger is disabled by default");
            }
            default -> {
                return Decision.deny("'" + name + "' is not on the allow-list");
            }
        }
    }

    /** Names this policy lets a bot issue, without arguments. */
    public Set<String> allowedNames() {
        return _allowTrigger
                ? Set.of("list", "msg", "tell", "w", "me", "help", "seed", TRIGGER)
                : Set.of("list", "msg", "tell", "w", "me", "help", "seed");
    }

    private static Decision text(String[] args, int from) {
        if (args.length <= from) {
            return Decision.deny("message text is empty");
        }
        String text = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        if (text.length() > MAX_TEXT) {
            return Decision.deny("message longer than " + MAX_TEXT + " characters");
        }
        return Decision.allow();
    }
}
