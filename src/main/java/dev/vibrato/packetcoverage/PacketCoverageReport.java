package dev.vibrato.packetcoverage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reconciles successful observations with the manifest; listing a packet is never coverage. */
public final class PacketCoverageReport {
    private PacketCoverageReport() {}

    /** Evidence captured at the wire, only for packet forms hidden by Minecraft's decoder. */
    public record WireEvidence(String state, String direction, int wireId, String scenario,
                               String rootTask, String directorStep, long count) {}
    public record Attribution(String scenario, String rootTask, String directorStep, long count, String source) {}
    public record Row(ProtocolManifest.Packet packet, long observed, long duringTask,
                      boolean covered, List<Attribution> attributions) {}
    public record Report(int protocol, List<Row> packets, List<PacketCoverage.Entry> unknownPackets,
                         List<WireEvidence> unknownWireEvidence) {}

    public static Report reconcile(ProtocolManifest manifest, PacketCoverage.Snapshot snapshot, List<WireEvidence> evidence) {
        if (manifest.protocol() != snapshot.protocol()) throw new IllegalArgumentException("Protocol mismatch");
        Map<String, ProtocolManifest.Packet> runtime = new HashMap<>(), wire = new HashMap<>();
        Map<ProtocolManifest.Packet, List<Attribution>> found = new HashMap<>();
        for (var p : manifest.packets()) {
            if (p.requiresWireEvidence()) wire.put(ProtocolManifest.key(p.state(), p.direction(), Integer.toString(p.id())), p);
            else runtime.put(ProtocolManifest.key(p.state(), p.direction(), p.identifier()), p);
        }
        List<PacketCoverage.Entry> unknown = new ArrayList<>();
        for (var entry : snapshot.packets()) {
            if (entry.count() <= 0) throw new IllegalArgumentException("Packet counts must be positive");
            var p = runtime.get(ProtocolManifest.key(entry.phase(), entry.side(), entry.id()));
            if (p == null) unknown.add(entry);
            else found.computeIfAbsent(p, ignored -> new ArrayList<>()).add(new Attribution(entry.scenario(), entry.rootTask(), entry.directorStep(), entry.count(), "packet"));
        }
        List<WireEvidence> unknownWire = new ArrayList<>();
        for (var entry : evidence) {
            if (entry.count() <= 0) throw new IllegalArgumentException("Wire counts must be positive");
            var p = wire.get(ProtocolManifest.key(entry.state(), entry.direction(), Integer.toString(entry.wireId())));
            if (p == null) unknownWire.add(entry);
            else found.computeIfAbsent(p, ignored -> new ArrayList<>()).add(new Attribution(entry.scenario(), entry.rootTask(), entry.directorStep(), entry.count(), "wire"));
        }
        List<Row> rows = new ArrayList<>();
        for (var p : manifest.packets()) {
            var attribution = List.copyOf(found.getOrDefault(p, List.of()));
            long observed = 0, duringTask = 0;
            for (var a : attribution) {
                observed = Math.addExact(observed, a.count());
                if (a.rootTask() != null && !a.rootTask().isBlank() && !a.rootTask().equals("none")) duringTask = Math.addExact(duringTask, a.count());
            }
            rows.add(new Row(p, observed, duringTask, duringTask > 0, attribution));
        }
        return new Report(manifest.protocol(), List.copyOf(rows), List.copyOf(unknown), List.copyOf(unknownWire));
    }
}
