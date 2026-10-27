package dev.vibrato.human.record;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses trace files written by {@link TraceWriter}. The parser only handles the flat objects this format uses:
 * string, number, boolean and null values, with no nesting.
 */
public final class TraceReader {

    private TraceReader() {}

    /**
     * Reads every non-blank line from {@code in} in order.
     */
    public static List<TraceEvent> read(Reader in) throws IOException {
        List<TraceEvent> events = new ArrayList<>();
        BufferedReader reader = new BufferedReader(in);
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                events.add(parseLine(line));
            }
        }
        return events;
    }

    /**
     * Parses one JSON object line into the matching event.
     */
    public static TraceEvent parseLine(String line) {
        Map<String, String> fields = new Parser(line).parseObject();
        return switch (required(fields, "t")) {
            case "header" -> new TraceEvent.Header(
                    integer(fields, "v"), required(fields, "source"), required(fields, "mcVersion"),
                    integer(fields, "guiScale"), decimal(fields, "sensitivity"), integer(fields, "fps"),
                    required(fields, "note"));
            case "mouse" -> new TraceEvent.Mouse(
                    integer64(fields, "ms"), decimal(fields, "dx"), decimal(fields, "dy"));
            case "key" -> new TraceEvent.Key(
                    integer64(fields, "ms"), required(fields, "key"), bool(fields, "down"));
            case "tick" -> new TraceEvent.Tick(
                    integer64(fields, "n"), integer64(fields, "ms"),
                    decimal(fields, "x"), decimal(fields, "y"), decimal(fields, "z"),
                    decimal(fields, "vx"), decimal(fields, "vy"), decimal(fields, "vz"),
                    decimal(fields, "yaw"), decimal(fields, "pitch"),
                    bool(fields, "ground"), bool(fields, "sprint"),
                    optionalDecimal(fields, "nextX"), optionalDecimal(fields, "nextY"),
                    optionalDecimal(fields, "nextZ"), optionalDecimal(fields, "edge"));
            default -> throw new IllegalArgumentException("unknown trace event type: " + fields.get("t"));
        };
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing field '" + key + "'");
        }
        return value;
    }

    private static int integer(Map<String, String> fields, String key) {
        return Integer.parseInt(required(fields, key));
    }

    private static long integer64(Map<String, String> fields, String key) {
        return Long.parseLong(required(fields, key));
    }

    private static double decimal(Map<String, String> fields, String key) {
        return Double.parseDouble(required(fields, key));
    }

    private static Double optionalDecimal(Map<String, String> fields, String key) {
        String value = fields.get(key);
        return value == null || value.equals("null") ? null : Double.parseDouble(value);
    }

    private static boolean bool(Map<String, String> fields, String key) {
        return switch (required(fields, key)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("field '" + key + "' is not a boolean");
        };
    }

    /**
     * Cursor over one line. Values are kept as text: strings are unescaped, everything else is the raw token, and
     * callers convert by field type.
     */
    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Map<String, String> parseObject() {
            Map<String, String> fields = new LinkedHashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    fields.put(key, parseValue());
                    skipWhitespace();
                    if (peek() == ',') {
                        pos++;
                    } else {
                        expect('}');
                        break;
                    }
                }
            }
            skipWhitespace();
            if (pos != text.length()) {
                throw error("trailing characters");
            }
            return fields;
        }

        private String parseValue() {
            if (peek() == '"') {
                return parseString();
            }
            int start = pos;
            while (pos < text.length() && ",}".indexOf(text.charAt(pos)) < 0
                    && !Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
            if (start == pos) {
                throw error("expected a value");
            }
            return text.substring(start, pos);
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw error("short unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("bad escape \\" + escaped);
                }
            }
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= text.length()) {
                throw error("unexpected end of line");
            }
            return text.charAt(pos++);
        }

        private void expect(char wanted) {
            if (next() != wanted) {
                pos--;
                throw error("expected '" + wanted + "'");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at column " + pos + " in: " + text);
        }
    }
}
