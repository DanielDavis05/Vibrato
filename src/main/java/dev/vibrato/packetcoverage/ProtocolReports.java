package dev.vibrato.packetcoverage;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Complete inventory of the requested packets, including every still-unobserved entry. */
public final class ProtocolReports {
    private static volatile ProtocolManifest manifest;
    private ProtocolReports() {}

    public static PacketCoverageReport.Report current() throws IOException {
        ProtocolManifest loaded = manifest;
        if (loaded == null) synchronized (ProtocolReports.class) {
            if (manifest == null) manifest = ProtocolManifest.load();
            loaded = manifest;
        }
        return reconcile(loaded, PacketCoverage.snapshot());
    }

    public static PacketCoverageReport.Report reconcile(ProtocolManifest manifest, PacketCoverage.Snapshot snapshot) {
        List<PacketCoverageReport.WireEvidence> evidence = new ArrayList<>();
        if (snapshot.wirePackets() != null) for (var entry : snapshot.wirePackets()) {
            var matching = manifest.packets().stream().filter(p -> p.requiresWireEvidence()
                    && p.state().equalsIgnoreCase(entry.phase()) && p.direction().equalsIgnoreCase(entry.side())
                    && (entry.id().equals(p.identifier()) || entry.id().equals("0x" + Integer.toHexString(p.id())))).findFirst();
            int wireId = matching.map(ProtocolManifest.Packet::id).orElse(-1);
            evidence.add(new PacketCoverageReport.WireEvidence(entry.phase(), entry.side(), wireId,
                    entry.scenario(), entry.rootTask(), entry.directorStep(), entry.count()));
        }
        return PacketCoverageReport.reconcile(manifest, snapshot, evidence);
    }

    public static long covered(PacketCoverageReport.Report report) {
        return report.packets().stream().filter(PacketCoverageReport.Row::covered).count();
    }

    public static String summary(PacketCoverageReport.Report report) {
        return covered(report) + "/" + report.packets().size() + " packet types observed during tasks";
    }

    public static Path write() throws IOException {
        PacketCoverage.writeReport();
        Path path = Path.of(System.getProperty("vibrato.protocolReport", "vibrato/protocol-coverage.json"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(current()));
        return path;
    }
}
