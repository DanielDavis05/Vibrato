package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskChurnPolicyTest {
    private static class Target extends Task {
        final int target;
        Target(int target) { this.target = target; }
        @Override public boolean isChurnGuardEligible() { return true; }
        @Override protected void onStart(Vibrato mod) { }
        @Override protected Task onTick(Vibrato mod) { return null; }
        @Override protected void onStop(Vibrato mod, Task interrupt) { }
        @Override protected boolean isEqual(Task other) { return other instanceof Target t && target == t.target; }
        @Override protected String toDebugString() { return "target " + target; }
    }
    private static final class OtherTarget extends Target { OtherTarget(int n) { super(n); } }
    private static final class Ineligible extends Target {
        Ineligible(int n) { super(n); }
        @Override public boolean isChurnGuardEligible() { return false; }
    }
    private static final class Alternating extends Task {
        int next;
        @Override protected void onStart(Vibrato mod) { }
        @Override protected Task onTick(Vibrato mod) { return new Target(next++ % 2); }
        @Override protected void onStop(Vibrato mod, Task interrupt) { }
        @Override protected boolean isEqual(Task other) { return this == other; }
        @Override protected String toDebugString() { return "alternating"; }
    }
    private static final class GuardChain extends TaskChain {
        final Task root = new Alternating();
        GuardChain() { super(new TaskRunner(null)); }
        @Override protected boolean churnGuardEnabled() { return true; }
        @Override protected void onTick(Vibrato mod) { root.tick(mod, this); }
        @Override protected void onStop(Vibrato mod) { }
        @Override public void onInterrupt(Vibrato mod, TaskChain other) { resetChurnGuard(); }
        @Override public float getPriority(Vibrato mod) { return 0; }
        @Override public boolean isActive() { return true; }
        @Override public String getName() { return "test"; }
    }

    @Test void alternationTripsOnlyTheRepeatedObjective() {
        TaskChurnPolicy policy = new TaskChurnPolicy();
        for (int i = 0; i < 7; i++) {
            assertFalse(policy.started(new Target(1), i * 1_000_000_000L));
            assertFalse(policy.started(new Target(2), i * 1_000_000_000L));
        }
        assertTrue(policy.started(new Target(1), 7_000_000_000L));
        assertTrue(policy.mayRecover(new Target(1)));
        assertFalse(policy.started(new Target(1), 8_000_000_000L));
    }

    @Test void windowsExpireAndDistinctClassesAndIneligibleTasksDoNotCombine() {
        TaskChurnPolicy policy = new TaskChurnPolicy();
        for (int i = 0; i < 20; i++) {
            assertFalse(policy.started(new Target(1), i * 11_000_000_000L));
            assertFalse(policy.started(new Ineligible(1), i));
        }
        for (int i = 0; i < 7; i++) assertFalse(policy.started(new OtherTarget(1), i));
        policy.clear();
        assertFalse(policy.started(new OtherTarget(1), 8));
    }

    @Test void alternatingChildrenBubbleFailureAfterBound() {
        GuardChain chain = new GuardChain();
        for (int i = 0; i < 16; i++) chain.tick(null);
        assertNotNull(chain.root.failureReason());
        assertTrue(chain.root.failureReason().contains("target 0"));
    }

    @Test void switchDisablesGuard() {
        String previous = System.getProperty("vibrato.churnGuard");
        try {
            System.setProperty("vibrato.churnGuard", "off");
            GuardChain chain = new GuardChain();
            for (int i = 0; i < 40; i++) chain.tick(null);
            assertNull(chain.root.failureReason());
        } finally {
            if (previous == null) System.clearProperty("vibrato.churnGuard");
            else System.setProperty("vibrato.churnGuard", previous);
        }
    }
}
