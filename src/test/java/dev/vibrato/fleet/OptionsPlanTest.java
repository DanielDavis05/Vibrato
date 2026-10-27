package dev.vibrato.fleet;

import dev.vibrato.fleet.OptionsPlan.Setting;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsPlanTest {

    private static FleetConfig config(String perf) {
        Map<String, String> map = new HashMap<>();
        map.put("vibrato.perf", perf);
        return FleetConfig.parse(map);
    }

    private static Map<String, Object> byName(OptionsPlan plan) {
        Map<String, Object> values = new HashMap<>();
        for (Setting setting : plan.settings()) {
            assertTrue(values.put(setting.option(), setting.value()) == null, "duplicate option " + setting.option());
        }
        return values;
    }

    @Test
    void lowProfileSetsTheHeadlessOptionSet() {
        Map<String, Object> values = byName(OptionsPlan.forConfig(config("low")));
        assertEquals(15, values.size());
        assertEquals(5, values.get("renderDistance"));
        assertEquals(5, values.get("simulationDistance"));
        assertEquals(30, values.get("maxFps"));
        assertEquals(false, values.get("enableVsync"));
        assertEquals("FAST", values.get("graphicsMode"));
        assertEquals("MINIMAL", values.get("particles"));
        assertEquals("OFF", values.get("cloudRenderMode"));
        assertEquals(false, values.get("entityShadows"));
        assertEquals(false, values.get("bobView"));
        assertEquals(0, values.get("biomeBlendRadius"));
        assertEquals(false, values.get("pauseOnLostFocus"));
        assertEquals(0.0, values.get("soundVolume.master"));
        assertEquals(0.0, values.get("soundVolume.music"));
        assertEquals(0.0, values.get("soundVolume.ambient"));
    }

    @Test
    void renderDistanceStaysInTheLowResourceRange() {
        int distance = (Integer) byName(OptionsPlan.forConfig(config("low"))).get("renderDistance");
        assertTrue(distance >= 4 && distance <= 6, "render distance " + distance);
    }

    @Test
    void normalProfileChangesNothing() {
        assertTrue(OptionsPlan.forConfig(config("normal")).settings().isEmpty());
    }

    @Test
    void autostartWithoutExplicitPerfUsesLowProfile() {
        Map<String, String> map = new HashMap<>();
        map.put("vibrato.autostart", "true");
        OptionsPlan plan = OptionsPlan.forConfig(FleetConfig.parse(map));
        assertEquals(15, plan.settings().size());
    }
}
