package dev.vibrato.packetcoverage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProtocolSuiteTaskTest {
    @Test void temporaryInterruptionResumesTheCurrentOperation() {
        var suite = new ProtocolSuiteTask();
        suite.onStart(null);
        var operation = suite.onTick(null);
        suite.onStop(null, null);
        suite.onStart(null);
        assertSame(operation, suite.onTick(null));
    }
}
