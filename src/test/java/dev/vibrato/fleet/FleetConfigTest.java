package dev.vibrato.fleet;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetConfigTest {

    private static FleetConfig parse(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return FleetConfig.parse(map);
    }

    private static IllegalArgumentException rejected(String... keyValues) {
        return assertThrows(IllegalArgumentException.class, () -> parse(keyValues));
    }

    @Test
    void emptyInputGivesDefaults() {
        FleetConfig config = parse();
        assertEquals("solo", config.botId());
        assertEquals(FleetConfig.derivedSeed("solo"), config.seed());
        assertNull(config.persona());
        assertFalse(config.autostart());
        assertEquals(10, config.planLength());
        assertNull(config.serverAddress());
        assertEquals(FleetConfig.PerfMode.NORMAL, config.perfMode());
        assertEquals("vibrato", config.dataDir());
    }

    @Test
    void autostartDefaultsPerfToLow() {
        FleetConfig config = parse("vibrato.autostart", "true");
        assertTrue(config.autostart());
        assertEquals(FleetConfig.PerfMode.LOW, config.perfMode());
    }

    @Test
    void explicitPerfWinsOverAutostartDefault() {
        assertEquals(FleetConfig.PerfMode.NORMAL, parse("vibrato.autostart", "true", "vibrato.perf", "normal").perfMode());
        assertEquals(FleetConfig.PerfMode.LOW, parse("vibrato.perf", "LOW").perfMode());
    }

    @Test
    void explicitValuesAreUsed() {
        FleetConfig config = parse(
                "vibrato.botId", "Vibrato03",
                "vibrato.seed", "-42",
                "vibrato.persona", "builder",
                "vibrato.autostart", "TRUE",
                "vibrato.planLength", "25",
                "vibrato.startDelay", "0",
                "vibrato.server", "localhost:25565",
                "vibrato.dataDir", "/tmp/bot3");
        assertEquals("Vibrato03", config.botId());
        assertEquals(-42L, config.seed());
        assertEquals("builder", config.persona());
        assertTrue(config.autostart());
        assertEquals(25, config.planLength());
        assertEquals(0, config.startDelaySeconds());
        assertEquals("localhost:25565", config.serverAddress());
        assertEquals("/tmp/bot3", config.dataDir());
    }

    @Test
    void derivedSeedMatchesFnv1aVectorAndIsDeterministic() {
        // Published FNV-1a 64-bit test vector for "a".
        assertEquals(0xaf63dc4c8601ec8cL, FleetConfig.derivedSeed("a"));
        assertEquals(FleetConfig.derivedSeed("Vibrato07"), FleetConfig.derivedSeed("Vibrato07"));
        assertFalse(FleetConfig.derivedSeed("Vibrato07") == FleetConfig.derivedSeed("Vibrato08"));
    }

    @Test
    void derivedStartDelayStaysInRangeAndIsDeterministic() {
        boolean seenDifferent = false;
        int first = FleetConfig.derivedStartDelay(1L);
        for (int i = 0; i < 2000; i++) {
            long seed = FleetConfig.derivedSeed("bot" + i);
            int delay = FleetConfig.derivedStartDelay(seed);
            assertTrue(delay >= 5 && delay <= 25, "delay out of range: " + delay);
            assertEquals(delay, FleetConfig.derivedStartDelay(seed));
            seenDifferent |= delay != first;
        }
        assertTrue(seenDifferent, "start delays should spread across bots");
    }

    @Test
    void absentSeedIsDerivedFromBotIdAndStartDelayFromSeed() {
        FleetConfig config = parse("vibrato.botId", "Vibrato01");
        assertEquals(FleetConfig.derivedSeed("Vibrato01"), config.seed());
        assertEquals(FleetConfig.derivedStartDelay(config.seed()), config.startDelaySeconds());
    }

    @Test
    void rejectsBadBotId() {
        assertTrue(rejected("vibrato.botId", "has space").getMessage().contains("botId"));
        assertTrue(rejected("vibrato.botId", "../escape").getMessage().contains("botId"));
        assertTrue(rejected("vibrato.botId", "x".repeat(33)).getMessage().contains("botId"));
    }

    @Test
    void rejectsBadSeed() {
        assertTrue(rejected("vibrato.seed", "12.5").getMessage().contains("vibrato.seed"));
        assertTrue(rejected("vibrato.seed", "99999999999999999999999").getMessage().contains("vibrato.seed"));
    }

    @Test
    void rejectsBadPersona() {
        assertTrue(rejected("vibrato.persona", "Builder").getMessage().contains("persona"));
        assertTrue(rejected("vibrato.persona", "1st").getMessage().contains("persona"));
    }

    @Test
    void rejectsBadBooleans() {
        assertTrue(rejected("vibrato.autostart", "yes").getMessage().contains("vibrato.autostart"));
    }

    @Test
    void rejectsOutOfRangeAndNonNumericIntegers() {
        assertTrue(rejected("vibrato.planLength", "0").getMessage().contains("planLength"));
        assertTrue(rejected("vibrato.planLength", "201").getMessage().contains("planLength"));
        assertTrue(rejected("vibrato.planLength", "ten").getMessage().contains("planLength"));
        assertTrue(rejected("vibrato.startDelay", "-1").getMessage().contains("startDelay"));
        assertTrue(rejected("vibrato.startDelay", "601").getMessage().contains("startDelay"));
    }

    @Test
    void rejectsBadServerAddress() {
        assertTrue(rejected("vibrato.server", "localhost").getMessage().contains("host:port"));
        assertTrue(rejected("vibrato.server", "localhost:99999").getMessage().contains("port"));
        assertTrue(rejected("vibrato.server", "localhost:0").getMessage().contains("port"));
        assertTrue(rejected("vibrato.server", "host name:25565").getMessage().contains("host:port"));
    }

    @Test
    void rejectsUnknownPerfMode() {
        assertTrue(rejected("vibrato.perf", "ultra").getMessage().contains("vibrato.perf"));
    }

    @Test
    void blankValuesFallBackToDefaults() {
        FleetConfig config = parse("vibrato.botId", "  ", "vibrato.server", "");
        assertEquals("solo", config.botId());
        assertNull(config.serverAddress());
    }
}
