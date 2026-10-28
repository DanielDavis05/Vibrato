package dev.vibrato.tasks.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApproachRangeTest {
    @Test void approachesFromActualDistanceInsteadOfOverflowingMaximumInteger() {
        assertEquals(49, ApproachRange.initial(50));
        assertEquals(4, ApproachRange.next(5));
        assertEquals(0, ApproachRange.next(0));
        assertEquals(46340, ApproachRange.initial(Integer.MAX_VALUE));
        assertTrue((long) ApproachRange.initial(Integer.MAX_VALUE) * ApproachRange.initial(Integer.MAX_VALUE) <= Integer.MAX_VALUE);
    }
}
