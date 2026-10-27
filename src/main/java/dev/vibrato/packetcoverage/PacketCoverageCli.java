package dev.vibrato.packetcoverage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Merge actual captures across reconnects/processes. Exit 2 until all 224 entries have task evidence. */
public final class PacketCoverageCli {
    /** Reject overlapping snapshots of a session, including copies and legacy byte-identical captures. */
    public static PacketCoverage.Snapshot merge(List<Path> paths) throws Exception {
        List<PacketCoverage.Entry> packets = new ArrayList<>(), wire = new ArrayList<>();
        HashSet<Path> inputs = new HashSet<>();
        HashSet<String> captureIds = new HashSet<>(), digests = new HashSet<>();
        for (Path path : paths) {
            Path input = path.toRealPath();
            if (!inputs.add(input)) throw new IllegalArgumentException("Duplicate capture: " + input);
            byte[] bytes = Files.readAllBytes(input);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!digests.add(digest)) throw new IllegalArgumentException("Duplicate capture contents: " + input);
            var snapshot = new Gson().fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), PacketCoverage.Snapshot.class);
            if (snapshot == null || snapshot.protocol() != 767 || snapshot.packets() == null) throw new IllegalArgumentException("Not a protocol 767 capture: " + input);
            if (snapshot.captureId() != null && !snapshot.captureId().isBlank() && !captureIds.add(snapshot.captureId()))
                throw new IllegalArgumentException("Overlapping snapshots of capture: " + input);
            packets.addAll(snapshot.packets());
            if (snapshot.wirePackets() != null) wire.addAll(snapshot.wirePackets());
        }
        return new PacketCoverage.Snapshot(767, "Merged successful observations; source captures retain task/scenario attribution", List.copyOf(packets), List.copyOf(wire));
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: PacketCoverageCli report.json capture.json [capture.json ...]");
        List<Path> inputs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) inputs.add(Path.of(args[i]));
        var snapshot = merge(inputs);
        var report = ProtocolReports.reconcile(ProtocolManifest.load(), snapshot);
        Path output = Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println(ProtocolReports.summary(report));
        if (ProtocolReports.covered(report) != report.packets().size() || !report.unknownPackets().isEmpty() || !report.unknownWireEvidence().isEmpty()) System.exit(2);
    }
}
