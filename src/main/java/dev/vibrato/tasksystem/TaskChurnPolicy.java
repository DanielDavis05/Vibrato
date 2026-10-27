package dev.vibrato.tasksystem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded sliding windows; task equality is used only for explicitly eligible objectives. */
final class TaskChurnPolicy {
    private static final long WINDOW = 10_000_000_000L;
    private static final int LIMIT = 8;
    private static final int MAX_TARGETS = 128;
    private static final int MAX_RECOVERIES = 2;
    private final List<Entry> entries = new ArrayList<>();

    private static final class Entry {
        final Task target;
        final ArrayDeque<Long> starts = new ArrayDeque<>();
        int recoveries;
        Entry(Task target) { this.target = target; }
    }

    private static boolean same(Task a, Task b) {
        return a.getClass() == b.getClass() && a.equals(b) && b.equals(a);
    }

    /** Returns true on the eighth start within ten seconds. */
    boolean started(Task task, long now) {
        if (!task.isChurnGuardEligible()) return false;
        Entry entry = null;
        for (Entry candidate : entries) if (same(candidate.target, task)) { entry = candidate; break; }
        if (entry == null) {
            if (entries.size() == MAX_TARGETS) entries.remove(0);
            entry = new Entry(task);
            entries.add(entry);
        }
        while (!entry.starts.isEmpty() && now - entry.starts.peekFirst() > WINDOW) entry.starts.removeFirst();
        entry.starts.addLast(now);
        return entry.starts.size() >= LIMIT;
    }

    boolean mayRecover(Task task) {
        for (Entry entry : entries) if (same(entry.target, task)) {
            if (entry.recoveries >= MAX_RECOVERIES) return false;
            entry.recoveries++;
            entry.starts.clear();
            return true;
        }
        return false;
    }

    void clear() { entries.clear(); }
}
