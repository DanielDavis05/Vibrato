package dev.vibrato.protocol.scenarios;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientProtocolScenarioTaskTest {
    @Test void unknownActionCannotSilentlyRun() {
        assertThrows(IllegalArgumentException.class, () -> new ClientProtocolScenarioTask("unknown"));
        assertThrows(IllegalArgumentException.class, () -> ClientProtocolScenarioTask.requirements("unknown"));
    }

    @Test void privilegedVariantsRequireTheirOwnFixtureAndPacket() {
        assertEquals(java.util.List.of("Program Command Block"),
                ClientProtocolScenarioTask.expectedPackets("command_block"));
        assertEquals(java.util.List.of("Program Command Block Minecart"),
                ClientProtocolScenarioTask.expectedPackets("command_minecart"));
        assertTrue(ClientProtocolScenarioTask.requirements("jigsaw_generate").contains("isolated world"));
        assertTrue(ClientProtocolScenarioTask.expectedPackets("jigsaw_generate").contains("Jigsaw Generate"));
    }

    @Test void everyOfferedActionExplainsItsPrerequisitesAndExpectedTraffic() {
        for (String action : ClientProtocolScenarioTask.supportedActions()) {
            assertFalse(ClientProtocolScenarioTask.requirements(action).isBlank(), action);
            assertFalse(ClientProtocolScenarioTask.expectedPackets(action).isEmpty(), action);
            assertTrue(java.util.Set.of("none", "blocks", "entities", "inventory", "world", "watercraft")
                    .contains(ClientProtocolScenarioTask.fixtureSetup(action)), action);
        }
    }
}
