package dev.vibrato.human.wire;

import dev.vibrato.human.motion.Key;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class KeyAdapterTest {

    @Test
    void roundTripsEveryCombination() {
        for (int bits = 0; bits < 128; bits++) {
            KeyAdapter.Flags flags = new KeyAdapter.Flags((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                    (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0, (bits & 64) != 0);
            assertEquals(flags, KeyAdapter.toFlags(KeyAdapter.toKeys(flags)));
        }
    }

    @Test
    void mapsFlagsToKeys() {
        KeyAdapter.Flags flags = new KeyAdapter.Flags(true, false, false, true, true, false, false);
        assertEquals(EnumSet.of(Key.FORWARD, Key.RIGHT, Key.JUMP), KeyAdapter.toKeys(flags));
    }

    @Test
    void axisOpposingKeysCancel() {
        assertEquals(1f, KeyAdapter.axis(true, false));
        assertEquals(-1f, KeyAdapter.axis(false, true));
        assertEquals(0f, KeyAdapter.axis(true, true));
        assertEquals(0f, KeyAdapter.axis(false, false));
    }
}
