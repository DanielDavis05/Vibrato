package dev.vibrato.fleet;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Per-instance identity and launch settings for one bot client, read from {@code -Dvibrato.*} system properties.
 * Parsing is pure so it can be tested without a running client.
 */
public record FleetConfig(
        String botId,
        long seed,
        String persona,
        boolean autostart,
        int planLength,
        int startDelaySeconds,
        String serverAddress,
        PerfMode perfMode,
        String dataDir
) {

    public enum PerfMode {
        LOW, NORMAL;

        static PerfMode parse(String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "low" -> LOW;
                case "normal" -> NORMAL;
                default -> throw new IllegalArgumentException("vibrato.perf must be 'low' or 'normal', got '" + text + "'");
            };
        }
    }

    public static final String DEFAULT_BOT_ID = "solo";
    public static final String DEFAULT_DATA_DIR = "vibrato";
    public static final int DEFAULT_PLAN_LENGTH = 10;
    public static final int MAX_PLAN_LENGTH = 200;
    public static final int MIN_START_DELAY = 5;
    public static final int MAX_START_DELAY = 25;
    public static final int MAX_EXPLICIT_START_DELAY = 600;

    private static final Pattern BOT_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern PERSONA = Pattern.compile("[a-z][a-z_]{0,31}");
    private static final Pattern HOST_PORT = Pattern.compile("[A-Za-z0-9._-]+:\\d{1,5}");

    private static final String[] KEYS = {
            "vibrato.botId", "vibrato.seed", "vibrato.persona", "vibrato.autostart", "vibrato.planLength",
            "vibrato.startDelay", "vibrato.server", "vibrato.perf", "vibrato.dataDir"
    };

    /** Reads the configuration from the JVM system properties. Throws {@link IllegalArgumentException} on bad values. */
    public static FleetConfig fromSystem() {
        Map<String, String> values = new HashMap<>();
        for (String key : KEYS) {
            String value = System.getProperty(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return parse(values);
    }

    /** Parses a raw property map. Missing or blank keys fall back to their defaults. */
    public static FleetConfig parse(Map<String, String> values) {
        String botId = optional(values, "vibrato.botId");
        if (botId == null) {
            botId = DEFAULT_BOT_ID;
        } else if (!BOT_ID.matcher(botId).matches()) {
            throw new IllegalArgumentException("vibrato.botId must be 1-32 characters of [A-Za-z0-9_-], got '" + botId + "'");
        }

        String seedText = optional(values, "vibrato.seed");
        long seed = seedText == null ? derivedSeed(botId) : parseSeed(seedText);

        String persona = optional(values, "vibrato.persona");
        if (persona != null && !PERSONA.matcher(persona).matches()) {
            throw new IllegalArgumentException("vibrato.persona must be a lowercase name like 'builder', got '" + persona + "'");
        }

        boolean autostart = parseBoolean(values, "vibrato.autostart", false);

        int planLength = parseInt(values, "vibrato.planLength", DEFAULT_PLAN_LENGTH, 1, MAX_PLAN_LENGTH);

        int startDelay = optional(values, "vibrato.startDelay") == null
                ? derivedStartDelay(seed)
                : parseInt(values, "vibrato.startDelay", 0, 0, MAX_EXPLICIT_START_DELAY);

        String server = optional(values, "vibrato.server");
        if (server != null && !HOST_PORT.matcher(server).matches()) {
            throw new IllegalArgumentException("vibrato.server must be host:port, got '" + server + "'");
        }
        if (server != null) {
            int port = Integer.parseInt(server.substring(server.lastIndexOf(':') + 1));
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("vibrato.server port must be 1-65535, got " + port);
            }
        }

        String perfText = optional(values, "vibrato.perf");
        PerfMode perf = perfText == null
                ? (autostart ? PerfMode.LOW : PerfMode.NORMAL)
                : PerfMode.parse(perfText);

        String dataDir = optional(values, "vibrato.dataDir");
        if (dataDir == null) {
            dataDir = DEFAULT_DATA_DIR;
        }

        return new FleetConfig(botId, seed, persona, autostart, planLength, startDelay, server, perf, dataDir);
    }

    /** Stable 64-bit seed from the bot id (FNV-1a), so the same id always gets the same behaviour. */
    public static long derivedSeed(String botId) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : botId.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xffL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Start delay in [5, 25] seconds, derived from the seed so bots launched together do not act in the same tick. */
    public static int derivedStartDelay(long seed) {
        long mixed = seed ^ (seed >>> 31);
        mixed *= 0x9E3779B97F4A7C15L;
        return MIN_START_DELAY + (int) Long.remainderUnsigned(mixed >>> 1, MAX_START_DELAY - MIN_START_DELAY + 1);
    }

    private static String optional(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static long parseSeed(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("vibrato.seed must be a 64-bit integer, got '" + text + "'", e);
        }
    }

    private static boolean parseBoolean(Map<String, String> values, String key, boolean fallback) {
        String text = optional(values, key);
        if (text == null) {
            return fallback;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false, got '" + text + "'");
        };
    }

    private static int parseInt(Map<String, String> values, String key, int fallback, int min, int max) {
        String text = optional(values, key);
        if (text == null) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer, got '" + text + "'", e);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", got " + value);
        }
        return value;
    }
}
