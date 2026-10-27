package dev.vibrato.packetcoverage;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Opt-in metadata only. Context is captured when processing/writing begins, then committed on success. */
public final class PacketCoverage {
    public record Context(long generation, String scenario, String rootTask, String directorStep) {}
    public record Key(String phase, String side, String id, String scenario, String rootTask, String directorStep) {}
    public record Entry(String phase, String side, String id, String scenario, String rootTask, String directorStep, long count) {}
    public record Snapshot(int protocol, String semantics, List<Entry> packets, List<Entry> wirePackets) {}

    public static final class Accumulator {
        private long generation;
        private String scenario = "lifecycle";
        private String rootTask = "none";
        private String directorStep = "none";
        private final Map<Key, Long> counts = new HashMap<>();
        private final Map<Key, Long> wireCounts = new HashMap<>();

        public synchronized Context capture() { return new Context(generation, scenario, rootTask, directorStep); }
        public synchronized void beginScenario(String label) { scenario = safeLabel(label); }
        public synchronized void endScenario() { scenario = "lifecycle"; }
        public synchronized void task(String root, String step) { rootTask = safeLabel(root); directorStep = safeLabel(step); }
        public synchronized void reset() { generation++; counts.clear(); wireCounts.clear(); scenario = "lifecycle"; rootTask = "none"; directorStep = "none"; }
        public synchronized void success(Context context, String phase, String side, String id) {
            if (context.generation() != generation) return;
            Key key = new Key(phase, side, id, context.scenario(), context.rootTask(), context.directorStep());
            counts.merge(key, 1L, Long::sum);
        }
        public synchronized void wireSuccess(Context context, String phase, String side, String id) {
            if (context.generation() != generation) return;
            Key key = new Key(phase, side, id, context.scenario(), context.rootTask(), context.directorStep());
            wireCounts.merge(key, 1L, Long::sum);
        }
        public synchronized Snapshot snapshot() {
            return new Snapshot(767, "successful client-side packet handling and successful outbound channel writes; wirePackets are separately decoded framing packets", entries(counts), entries(wireCounts));
        }
        private static List<Entry> entries(Map<Key, Long> counts) {
            List<Entry> entries = new ArrayList<>();
            counts.forEach ( (k, v) -> entries.add(new Entry(k.phase(), k.side(), k.id(), k.scenario(), k.rootTask(), k.directorStep(), v)) );
            entries.sort(Comparator.comparing(Entry::phase).thenComparing(Entry::side).thenComparing(Entry::id)
                    .thenComparing(Entry::scenario).thenComparing(Entry::rootTask).thenComparing(Entry::directorStep));
            return List.copyOf(entries);
        }
        private static String safeLabel(String value) {
            if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,100}")) return "none";
            return value;
        }
    }

    private static final boolean ENABLED = Boolean.getBoolean("vibrato.packetCoverage");
    private static final Accumulator DATA = new Accumulator();
    static {
        if (ENABLED) DATA.beginScenario(System.getProperty("vibrato.packetCoverageScenario", "lifecycle"));
        if (ENABLED) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { writeReport(); } catch (IOException e) { System.err.println("Packet coverage report failed: " + e.getMessage()); }
        }, "vibrato-packet-coverage"));
    }
    private PacketCoverage() {}
    public static boolean enabled() { return ENABLED; }
    public static void beginScenario(String label) { if (ENABLED) DATA.beginScenario(label); }
    public static void endScenario() { if (ENABLED) DATA.endScenario(); }
    public static void setTask(String root, String step) { if (ENABLED) DATA.task(root, step); }
    public static void reset() { if (ENABLED) DATA.reset(); }
    public static Context capture() { return ENABLED ? DATA.capture() : null; }
    public static void success(Context context, String phase, String side, String id) {
        if (context != null) DATA.success(context, phase, side, id);
    }
    public static void wireSuccess(Context context, String phase, String side, String id) {
        if (context != null) DATA.wireSuccess(context, phase, side, id);
    }
    public static Snapshot snapshot() { return DATA.snapshot(); }
    public static void writeReport() throws IOException {
        if (!ENABLED) return;
        Path file = Path.of(System.getProperty("vibrato.packetCoverageFile", "vibrato/packet-coverage.json"));
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()));
    }
}
