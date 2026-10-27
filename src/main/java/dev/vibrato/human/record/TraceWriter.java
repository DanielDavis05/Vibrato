package dev.vibrato.human.record;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Buffered JSON Lines writer for motion traces. Output is hand-written JSON, so there is no serializer dependency.
 * The stream is flushed at most once per second, so recording does not hit the disk on every tick.
 */
public final class TraceWriter implements Closeable {

    private static final long FLUSH_INTERVAL_MILLIS = 1000;

    private final Writer out;
    private final LongSupplier clockMillis;
    private long lastFlushMillis;

    public TraceWriter(Writer out, LongSupplier clockMillis) {
        this.out = out;
        this.clockMillis = clockMillis;
        this.lastFlushMillis = clockMillis.getAsLong();
    }

    /**
     * Opens a UTF-8 trace file for writing, creating parent directories and replacing any existing file.
     */
    public static TraceWriter open(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Writer writer = new BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
        return new TraceWriter(writer, System::currentTimeMillis);
    }

    /**
     * Appends one event as a JSON object on its own line.
     */
    public void write(TraceEvent event) throws IOException {
        out.write(toJson(event));
        out.write('\n');
        long now = clockMillis.getAsLong();
        if (now - lastFlushMillis >= FLUSH_INTERVAL_MILLIS) {
            out.flush();
            lastFlushMillis = now;
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    static String toJson(TraceEvent event) {
        return switch (event) {
            case TraceEvent.Header h -> "{\"t\":\"header\",\"v\":" + h.version()
                    + ",\"source\":" + quote(h.source())
                    + ",\"mcVersion\":" + quote(h.mcVersion())
                    + ",\"guiScale\":" + h.guiScale()
                    + ",\"sensitivity\":" + number(h.sensitivity())
                    + ",\"fps\":" + h.fps()
                    + ",\"note\":" + quote(h.note()) + "}";
            case TraceEvent.Mouse m -> "{\"t\":\"mouse\",\"ms\":" + m.ms()
                    + ",\"dx\":" + number(m.dx())
                    + ",\"dy\":" + number(m.dy()) + "}";
            case TraceEvent.Key k -> "{\"t\":\"key\",\"ms\":" + k.ms()
                    + ",\"key\":" + quote(k.key())
                    + ",\"down\":" + k.down() + "}";
            case TraceEvent.Tick t -> tickJson(t);
        };
    }

    private static String tickJson(TraceEvent.Tick t) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"t\":\"tick\",\"n\":").append(t.n())
                .append(",\"ms\":").append(t.ms())
                .append(",\"x\":").append(number(t.x()))
                .append(",\"y\":").append(number(t.y()))
                .append(",\"z\":").append(number(t.z()))
                .append(",\"vx\":").append(number(t.vx()))
                .append(",\"vy\":").append(number(t.vy()))
                .append(",\"vz\":").append(number(t.vz()))
                .append(",\"yaw\":").append(number(t.yaw()))
                .append(",\"pitch\":").append(number(t.pitch()))
                .append(",\"ground\":").append(t.ground())
                .append(",\"sprint\":").append(t.sprint());
        if (t.nextX() != null) {
            json.append(",\"nextX\":").append(number(t.nextX()));
        }
        if (t.nextY() != null) {
            json.append(",\"nextY\":").append(number(t.nextY()));
        }
        if (t.nextZ() != null) {
            json.append(",\"nextZ\":").append(number(t.nextZ()));
        }
        return json.append('}').toString();
    }

    /**
     * Fixed six decimal places, trailing zeros trimmed but at least one digit after the point (3.0, not 3).
     * That is well below a pixel, a block or a hundredth of a degree, so precision is not a concern.
     */
    static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite value in trace: " + value);
        }
        String text = String.format(Locale.ROOT, "%.6f", value + 0.0);
        int end = text.length();
        while (text.charAt(end - 1) == '0' && text.charAt(end - 2) != '.') {
            end--;
        }
        return text.substring(0, end);
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
