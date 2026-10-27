package dev.vibrato.packetcoverage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Merge actual captures across reconnects/processes. Exit 2 until all 224 entries have task evidence. */
public final class PacketCoverageCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: PacketCoverageCli report.json capture.json [capture.json ...]");
        List<PacketCoverage.Entry> packets = new ArrayList<>(), wire = new ArrayList<>();
        HashSet<Path> inputs = new HashSet<>();
        for (int i = 1; i < args.length; i++) {
            Path input = Path.of(args[i]).toRealPath();
            if (!inputs.add(input)) throw new IllegalArgumentException("Duplicate capture: " + input);
            var snapshot = new Gson().fromJson(Files.readString(input), PacketCoverage.Snapshot.class);
            if (snapshot == null || snapshot.protocol() != 767 || snapshot.packets() == null) throw new IllegalArgumentException("Not a protocol 767 capture: " + input);
            packets.addAll(snapshot.packets());
            if (snapshot.wirePackets() != null) wire.addAll(snapshot.wirePackets());
        }
        var snapshot = new PacketCoverage.Snapshot(767, "Merged successful observations; source captures retain task/scenario attribution", List.copyOf(packets), List.copyOf(wire));
        var report = ProtocolReports.reconcile(ProtocolManifest.load(), snapshot);
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println(ProtocolReports.summary(report));
        if (ProtocolReports.covered(report) != report.packets().size() || !report.unknownPackets().isEmpty() || !report.unknownWireEvidence().isEmpty()) System.exit(2);
    }
}
