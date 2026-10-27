package dev.vibrato.packetcoverage;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolManifestTest {
    private static PacketCoverageReport.Row row(PacketCoverageReport.Report report, String state, String direction, int id) {
        return report.packets().stream().filter(r -> r.packet().state().equals(state)
                && r.packet().direction().equals(direction) && r.packet().id() == id).findFirst().orElseThrow();
    }
    private static PacketCoverage.Snapshot snapshot(PacketCoverage.Entry... entries) {
        return new PacketCoverage.Snapshot(767, "test", List.of(entries), List.of());
    }
    @Test void manifestHasEveryPacketAndRuntimeIdentity() throws Exception {
        var manifest = ProtocolManifest.load(); // load validates uniqueness of wire and runtime keys
        assertEquals(224, manifest.packets().size());
        assertEquals(223, manifest.packets().stream().filter(p -> p.identifier() != null).count());
        assertEquals(2, manifest.packets().stream().filter(ProtocolManifest.Packet::requiresWireEvidence).count());
        assertEquals("minecraft:bundle_delimiter", manifest.packets().stream().filter(ProtocolManifest.Packet::wireEvidenceRequired).findFirst().orElseThrow().identifier());
        var empty = PacketCoverageReport.reconcile(manifest, snapshot(), List.of());
        assertEquals(224, empty.packets().size());
        assertTrue(empty.packets().stream().noneMatch(PacketCoverageReport.Row::covered));
    }
    @Test void phaseAndDirectionAndTaskAttributionMatter() throws Exception {
        var report = PacketCoverageReport.reconcile(ProtocolManifest.load(), snapshot(
                new PacketCoverage.Entry("LOGIN", "CLIENTBOUND", "minecraft:cookie_request", "login", "none", "none", 2),
                new PacketCoverage.Entry("CONFIGURATION", "CLIENTBOUND", "minecraft:cookie_request", "setup", "ConnectTask", "init", 3),
                new PacketCoverage.Entry("PLAY", "SERVERBOUND", "minecraft:cookie_request", "wrong-side", "Task", "none", 1),
                new PacketCoverage.Entry("PLAY", "SERVERBOUND", "mod:unknown", "play", "Task", "none", 1)), List.of());
        assertEquals(2, row(report, "login", "clientbound", 5).observed());
        assertFalse(row(report, "login", "clientbound", 5).covered());
        var config = row(report, "configuration", "clientbound", 0);
        assertEquals(3, config.duringTask());
        assertTrue(config.covered());
        assertEquals("setup", config.attributions().getFirst().scenario());
        assertFalse(row(report, "play", "clientbound", 22).covered());
        assertEquals(2, report.unknownPackets().size());
    }
    @Test void specialPacketsRequireExplicitWireEvidence() throws Exception {
        var entries = snapshot(new PacketCoverage.Entry("PLAY", "CLIENTBOUND", "minecraft:bundle", "play", "Task", "step", 1),
                new PacketCoverage.Entry("PLAY", "CLIENTBOUND", "minecraft:bundle_delimiter", "play", "Task", "step", 1));
        var noWire = PacketCoverageReport.reconcile(ProtocolManifest.load(), entries, List.of());
        assertFalse(row(noWire, "play", "clientbound", 0).covered());
        assertFalse(row(noWire, "handshaking", "serverbound", 254).covered());
        assertEquals(2, noWire.unknownPackets().size());
        var withWire = PacketCoverageReport.reconcile(ProtocolManifest.load(), entries, List.of(
                new PacketCoverageReport.WireEvidence("PLAY", "CLIENTBOUND", 0, "play", "Task", "step", 2),
                new PacketCoverageReport.WireEvidence("HANDSHAKING", "SERVERBOUND", 254, "legacy", "ProbeTask", "none", 1),
                new PacketCoverageReport.WireEvidence("PLAY", "CLIENTBOUND", 1, "play", "Task", "step", 1)));
        assertEquals(2, row(withWire, "play", "clientbound", 0).duringTask());
        assertTrue(row(withWire, "handshaking", "serverbound", 254).covered());
        assertEquals(1, withWire.unknownWireEvidence().size());
    }
}
