package dev.vibrato.packetcoverage;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Packet identity is scoped by protocol phase and direction, not by identifier alone. */
public record ProtocolManifest(int protocol, String minecraftVersion, List<Packet> packets) {
    public record Packet(String state, String direction, int id, String name, String packetType,
                         String identifier, boolean legacy, boolean wireEvidenceRequired) {
        public boolean requiresWireEvidence() { return legacy || wireEvidenceRequired; }
    }

    public static ProtocolManifest load() throws IOException {
        var stream = ProtocolManifest.class.getResourceAsStream("/vibrato/protocol-767.json");
        if (stream == null) throw new IOException("Missing protocol 767 manifest");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            ProtocolManifest manifest = new Gson().fromJson(reader, ProtocolManifest.class);
            if (manifest == null) throw new IOException("Empty protocol manifest");
            manifest.validate();
            return manifest;
        } catch (RuntimeException e) {
            throw new IOException("Invalid protocol manifest", e);
        }
    }

    private void validate() {
        if (protocol != 767 || packets == null || packets.size() != 224) throw new IllegalArgumentException("Unexpected protocol or packet count");
        Set<String> wireKeys = new HashSet<>(), runtimeKeys = new HashSet<>();
        for (Packet p : packets) {
            Objects.requireNonNull(p.state()); Objects.requireNonNull(p.direction()); Objects.requireNonNull(p.name());
            if (p.id() < 0 || !wireKeys.add(key(p.state(), p.direction(), Integer.toString(p.id())))) throw new IllegalArgumentException("Duplicate wire packet");
            if (!p.legacy() && (p.identifier() == null || p.packetType() == null || !runtimeKeys.add(key(p.state(), p.direction(), p.identifier())))) throw new IllegalArgumentException("Missing or duplicate runtime identifier");
        }
    }

    static String key(String state, String direction, String id) {
        return state.toUpperCase(Locale.ROOT) + "/" + direction.toUpperCase(Locale.ROOT) + "/" + id;
    }
}
