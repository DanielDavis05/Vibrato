package dev.vibrato.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WasteGuardTest {

    @Test
    void defaultsGiveUpAfterTwoMinutesWithoutProgress() {
        WasteGuard guard = WasteGuard.defaults();
        guard.start(0);
        assertFalse(guard.isWasting(2399));
        assertTrue(guard.isWasting(2400));
    }

    @Test
    void progressPostponesTheNoProgressLimit() {
        WasteGuard guard = new WasteGuard(2400, 100_000);
        guard.start(0);
        guard.onProgress(2000);
        assertFalse(guard.isWasting(4399));
        assertTrue(guard.isWasting(4400));
    }

    @Test
    void budgetIsExceededEvenWithProgress() {
        WasteGuard guard = new WasteGuard(2400, 10_000);
        guard.start(0);
        for (long tick = 1000; tick <= 10_000; tick += 1000) {
            guard.onProgress(tick);
        }
        assertFalse(guard.isWasting(10_000));
        assertTrue(guard.isWasting(10_001));
    }

    @Test
    void startBeginsANewActivity() {
        WasteGuard guard = new WasteGuard(100, 1000);
        guard.start(0);
        assertTrue(guard.isWasting(100));
        guard.start(100);
        assertFalse(guard.isWasting(150));
    }
}
