package dev.vibrato.tasksystem;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.director.StepAbandonable;

import java.util.ArrayList;
import java.util.List;

public abstract class TaskChain {

    private final List<Task> _cachedTaskChain = new ArrayList<>();
    private final List<Task> _activeTicks = new ArrayList<>();
    private static final int MAX_TASK_DEPTH = 128;
    private final List<Task> _startedThisTick = new ArrayList<>();
    private final TaskChurnPolicy _churn = new TaskChurnPolicy();
    private Task _churnRoot;
    private int _churnStep;
    private int _churnRecoveries;

    // The displayed chain persists after a tick; recursion checks must instead
    // use only the current call stack, which is unwound even on exceptions.
    boolean canEnter(Task candidate) {
        if (_activeTicks.size() >= MAX_TASK_DEPTH) return false;
        for (Task ancestor : _activeTicks) {
            if (ancestor == candidate || (ancestor.getClass() == candidate.getClass()
                    && ancestor.equals(candidate) && candidate.equals(ancestor))) return false;
        }
        return true;
    }

    void enter(Task task) { _activeTicks.add(task); }
    void leave() { _activeTicks.remove(_activeTicks.size() - 1); }

    void reportCycle(Task candidate) {
        Debug.logWarning("Task cycle or depth limit (" + MAX_TASK_DEPTH + ") blocked: "
                + candidate + " in " + _activeTicks);
    }

    public TaskChain(TaskRunner runner) {
        runner.addTaskChain(this);
    }

    public void tick(Vibrato mod) {
        _cachedTaskChain.clear();
        _startedThisTick.clear();
        onTick(mod);
        if (!churnGuardEnabled() || churnGuardSuppressed(mod) || "off".equalsIgnoreCase(System.getProperty("vibrato.churnGuard", "on"))
                || _cachedTaskChain.isEmpty()) return;
        Task root = _cachedTaskChain.getFirst();
        int step = root instanceof StepAbandonable steps ? steps.stepSerial() : 0;
        if (root != _churnRoot || step != _churnStep) {
            _churn.clear();
            _churnRoot = root;
            _churnStep = step;
            _churnRecoveries = 0;
        }
        Task leaf = _cachedTaskChain.getLast();
        if (leaf.isChurnGuardEligible() && leaf.isFinished(mod)) { _churn.clear(); return; }
        if (root.thisOrChildSatisfies(t -> t.isActive() && t instanceof ITaskUsesCraftingGrid)
                || !root.canBeInterruptedBy(mod, null)) { _churn.clear(); return; }
        // A different kind of work may have made progress that we cannot observe.
        if (!leaf.isChurnGuardEligible()) { _churn.clear(); return; }
        if (leaf.failureReason() != null) return;
        boolean started = false;
        for (Task task : _startedThisTick) if (task == leaf) { started = true; break; }
        if (started && _churn.started(leaf, System.nanoTime())) {
            if (_churnRecoveries < 2 && _churn.mayRecover(leaf) && leaf.recoverFromStall(mod)) {
                _churnRecoveries++;
                return;
            }
            leaf.failFromChurn();
        }
    }

    public void stop(Vibrato mod) {
        _cachedTaskChain.clear();
        resetChurnForNewRoot();
        onStop(mod);
    }

    protected abstract void onStop(Vibrato mod);

    public abstract void onInterrupt(Vibrato mod, TaskChain other);

    protected abstract void onTick(Vibrato mod);

    public abstract float getPriority(Vibrato mod);

    public abstract boolean isActive();

    public abstract String getName();

    public List<Task> getTasks() {
        return _cachedTaskChain;
    }

    void addTaskToChain(Task task) {
        _cachedTaskChain.add(task);
    }

    void recordStart(Task task) { _startedThisTick.add(task); }

    /** Clear when a chain is interrupted or a genuine external progress event occurs. */
    public final void resetChurnGuard() { _churn.clear(); }

    protected final void resetChurnForNewRoot() {
        _churn.clear();
        _churnRoot = null;
        _churnRecoveries = 0;
    }

    protected boolean churnGuardEnabled() { return false; }

    protected boolean churnGuardSuppressed(Vibrato mod) { return false; }

    public String toString() {
        return getName();
    }

}
