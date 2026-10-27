package dev.vibrato.packetcoverage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketCoverageTest {
    @Test void capturesAttributionAndKeepsHistoryAcrossScenarios() {
        var data = new PacketCoverage.Accumulator();
        data.beginScenario("login");
        var login = data.capture();
        data.task("DirectorTask", "explore");
        data.beginScenario("play");
        var play = data.capture();
        data.success(login, "LOGIN", "CLIENTBOUND", "minecraft:hello");
        data.success(play, "PLAY", "SERVERBOUND", "minecraft:move_player_pos");
        data.success(play, "PLAY", "SERVERBOUND", "minecraft:move_player_pos");
        var snapshot = data.snapshot();
        assertEquals(2, snapshot.packets().size());
        assertEquals("login", snapshot.packets().get(0).scenario());
        assertEquals("none", snapshot.packets().get(0).rootTask());
        assertEquals(2, snapshot.packets().get(1).count());
        assertEquals("DirectorTask", snapshot.packets().get(1).rootTask());
        assertEquals("explore", snapshot.packets().get(1).directorStep());
        data.endScenario();
        assertEquals("lifecycle", data.capture().scenario());
        assertEquals(2, snapshot.packets().size());
    }
    @Test void resetRejectsLateCompletionsAndUncommittedWritesDoNotCount() {
        var data = new PacketCoverage.Accumulator();
        var pending = data.capture();
        assertTrue(data.snapshot().packets().isEmpty());
        data.reset();
        data.success(pending, "PLAY", "SERVERBOUND", "minecraft:test");
        assertTrue(data.snapshot().packets().isEmpty());
        data.success(data.capture(), "PLAY", "SERVERBOUND", "minecraft:test");
        assertEquals(1, data.snapshot().packets().getFirst().count());
    }
    @Test void wireEvidenceIsSeparateAndResetInvalidatesPendingObservations() {
        var data = new PacketCoverage.Accumulator();
        var pending = data.capture();
        data.wireSuccess(pending, "PLAY", "CLIENTBOUND", "minecraft:bundle_delimiter");
        assertTrue(data.snapshot().packets().isEmpty());
        assertEquals(1, data.snapshot().wirePackets().getFirst().count());
        data.reset();
        data.wireSuccess(pending, "PLAY", "CLIENTBOUND", "minecraft:bundle_delimiter");
        assertTrue(data.snapshot().wirePackets().isEmpty());
    }
    @Test void externalTaskSurvivesOrdinaryIdleAndEndsOnlyForItsOwner() {
        var data = new PacketCoverage.Accumulator();
        data.task("DirectorTask", "explore");
        data.externalTask("ProtocolConnectTask");
        data.task("none", "none");
        assertEquals("ProtocolConnectTask", data.capture().rootTask());
        data.endExternalTask("OtherTask");
        assertEquals("ProtocolConnectTask", data.capture().rootTask());
        data.endExternalTask("ProtocolConnectTask");
        assertEquals("none", data.capture().rootTask());
        data.externalTask("ProtocolConnectTask");
        data.reset();
        assertEquals("none", data.capture().rootTask());
    }
    @Test void rejectsFreeFormLabels() {
        var data = new PacketCoverage.Accumulator();
        data.beginScenario("private text here");
        assertEquals("none", data.capture().scenario());
    }
}
