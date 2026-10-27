package dev.vibrato.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureChurnTest {

    @Test
    void fourFailuresInsideWindowIsChurning() {
        FailureChurn churn = new FailureChurn(600, 4);
        churn.failed(0);
        churn.failed(100);
        churn.failed(200);
        assertFalse(churn.churning(300));
        churn.failed(300);
        assertTrue(churn.churning(300));
    }

    @Test
    void oldFailuresFallOutOfTheWindow() {
        FailureChurn churn = new FailureChurn(600, 4);
        churn.failed(0);
        churn.failed(100);
        churn.failed(200);
        churn.failed(300);
        assertTrue(churn.churning(300));
        assertFalse(churn.churning(601));
    }

    @Test
    void clearResetsTheCount() {
        FailureChurn churn = new FailureChurn(600, 2);
        churn.failed(0);
        churn.failed(1);
        assertTrue(churn.churning(1));
        churn.clear();
        assertFalse(churn.churning(1));
    }
}
