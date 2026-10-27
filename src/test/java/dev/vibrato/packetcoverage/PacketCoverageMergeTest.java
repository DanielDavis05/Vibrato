package dev.vibrato.packetcoverage;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PacketCoverageMergeTest {
    @TempDir Path dir;
    private Path write(String name, PacketCoverage.Snapshot snapshot) throws Exception {
        Path path = dir.resolve(name);
        Files.writeString(path, new Gson().toJson(snapshot));
        return path;
    }
    private static PacketCoverage.Entry entry(String phase, String side, String id, String task, long count) {
        return new PacketCoverage.Entry(phase, side, id, "fixture", task, "none", count);
    }
    @Test void mergeRetainsTaskAndWireEvidenceWithoutInventingCoverage() throws Exception {
        var first = new PacketCoverage.Accumulator();
        first.success(first.capture(), "LOGIN", "CLIENTBOUND", "minecraft:cookie_request");
        var second = new PacketCoverage.Accumulator();
        second.task("ProtocolConnectTask", "none");
        second.success(second.capture(), "CONFIGURATION", "CLIENTBOUND", "minecraft:cookie_request");
        second.wireSuccess(second.capture(), "PLAY", "CLIENTBOUND", "minecraft:bundle_delimiter");
        var merged = PacketCoverageCli.merge(List.of(write("one.json", first.snapshot()), write("two.json", second.snapshot())));
        var report = ProtocolReports.reconcile(ProtocolManifest.load(), merged);
        var login = report.packets().stream().filter(r -> r.packet().state().equals("login") && r.packet().id() == 5 && r.packet().direction().equals("clientbound")).findFirst().orElseThrow();
        var config = report.packets().stream().filter(r -> r.packet().state().equals("configuration") && r.packet().id() == 0 && r.packet().direction().equals("clientbound")).findFirst().orElseThrow();
        var bundle = report.packets().stream().filter(r -> r.packet().state().equals("play") && r.packet().id() == 0 && r.packet().direction().equals("clientbound")).findFirst().orElseThrow();
        assertEquals(1, login.observed());
        assertFalse(login.covered());
        assertTrue(config.covered());
        assertEquals("ProtocolConnectTask", config.attributions().getFirst().rootTask());
        assertTrue(bundle.covered());
        assertEquals("wire", bundle.attributions().getFirst().source());
        assertEquals(2, ProtocolReports.covered(report));
    }
    @Test void rejectsCopiedAndOverlappingSnapshots() throws Exception {
        var accumulator = new PacketCoverage.Accumulator();
        accumulator.task("Task", "none");
        accumulator.success(accumulator.capture(), "LOGIN", "CLIENTBOUND", "minecraft:cookie_request");
        Path first = write("first.json", accumulator.snapshot());
        Path copy = dir.resolve("copy.json");
        Files.copy(first, copy);
        assertThrows(IllegalArgumentException.class, () -> PacketCoverageCli.merge(List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> PacketCoverageCli.merge(List.of(first, copy)));
        accumulator.success(accumulator.capture(), "LOGIN", "CLIENTBOUND", "minecraft:cookie_request");
        Path later = write("later.json", accumulator.snapshot());
        assertThrows(IllegalArgumentException.class, () -> PacketCoverageCli.merge(List.of(first, later)));
        accumulator.reset();
        accumulator.success(accumulator.capture(), "LOGIN", "CLIENTBOUND", "minecraft:cookie_request");
        assertDoesNotThrow(() -> PacketCoverageCli.merge(List.of(first, write("reset.json", accumulator.snapshot()))));
    }
    @Test void invalidEvidenceNeverBecomesCoverage() throws Exception {
        var manifest = ProtocolManifest.load();
        var snapshot = new PacketCoverage.Snapshot(767, "test", List.of(entry("LOGIN", "CLIENTBOUND", "minecraft:cookie_request", "Task", 0)), List.of());
        assertThrows(IllegalArgumentException.class, () -> ProtocolReports.reconcile(manifest, snapshot));
        var unknown = new PacketCoverage.Snapshot(767, "test", List.of(), List.of(entry("PLAY", "CLIENTBOUND", "minecraft:unknown", "Task", 1)));
        var report = ProtocolReports.reconcile(manifest, unknown);
        assertEquals(0, ProtocolReports.covered(report));
        assertEquals(1, report.unknownWireEvidence().size());
    }
}
