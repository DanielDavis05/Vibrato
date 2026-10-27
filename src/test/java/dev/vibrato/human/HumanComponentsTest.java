package dev.vibrato.human;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HumanComponentsTest {
    @Test void componentSwitchesAreIndependentAndGlobalOffWins() {
        String[] names = {"human", "humanLook", "humanKeys", "humanBehavior"};
        String[] saved = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            saved[i] = System.getProperty("vibrato." + names[i]);
            System.clearProperty("vibrato." + names[i]);
        }
        try {
            System.setProperty("vibrato.humanLook", "off");
            assertFalse(HumanComponents.enabled("humanLook"));
            assertTrue(HumanComponents.enabled("humanKeys"));
            assertTrue(HumanComponents.behavior());
            System.setProperty("vibrato.humanBehavior", "off");
            assertFalse(HumanComponents.behavior());
            assertTrue(HumanComponents.enabled("humanKeys"));
            System.setProperty("vibrato.human", "off");
            assertFalse(HumanComponents.enabled("humanKeys"));
            assertFalse(HumanComponents.enabled("humanLook"));
        } finally {
            for (int i = 0; i < names.length; i++) {
                if (saved[i] == null) System.clearProperty("vibrato." + names[i]);
                else System.setProperty("vibrato." + names[i], saved[i]);
            }
        }
    }
}
