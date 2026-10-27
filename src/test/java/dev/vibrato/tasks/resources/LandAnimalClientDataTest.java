package dev.vibrato.tasks.resources;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandAnimalClientDataTest {
    @Test void surfaceSelectionUsesAHeightmapTransmittedToClients() {
        assertTrue(LandAnimalSearchTask.SURFACE_HEIGHTMAP.shouldSendToClient());
    }
}
