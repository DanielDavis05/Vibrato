package dev.vibrato.director;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.util.helpers.WorldHelper;
import dev.vibrato.util.time.TimerGame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Plays forever by running randomly sampled plans one step at a time. A step succeeds when its task finishes; it fails
 * when its budget runs out or the task gets stuck, and then it is put on cooldown and the plan moves on.
 */
public class DirectorTask extends Task {

    private static final int DEFAULT_PLAN_LENGTH = 10;
    private static final int FAILURE_COOLDOWN_PLANS = 3;
    /** Upper bound on steps that yield no task within a single tick, so a broken registry cannot hang the tick. */
    private static final int MAX_SKIPS_PER_TICK = 32;

    private final ActivityRegistry _registry;
    private final long _seed;
    private final Persona _persona;
    private final int _planLength;
    private final Path _coverageFile;
    private final PlanSampler _sampler;

    private CoverageTracker _coverage;
    private Set<Fact> _facts = new HashSet<>();
    private long _planSeed;
    private Plan _plan = new Plan(List.of());
    private int _stepIndex;
    private PlannedStep _currentStep;
    private Task _stepTask;
    private TimerGame _stepTimer;

    /**
     * @param coverageFile where coverage is loaded from and saved to, or null to keep it in memory only
     * @param planLength   top-level steps per plan; values at or below zero mean 10
     */
    public DirectorTask(ActivityRegistry registry, long seed, Persona persona, int planLength, Path coverageFile) {
        _registry = registry;
        _seed = seed;
        _persona = persona;
        _planLength = planLength > 0 ? planLength : DEFAULT_PLAN_LENGTH;
        _coverageFile = coverageFile;
        _sampler = new PlanSampler(registry);
        _planSeed = seed;
    }

    @Override
    protected void onStart(Vibrato mod) {
        // Restarts after an interrupt keep the plan position; only the first start loads state.
        if (_coverage == null) {
            _coverage = _coverageFile == null ? new CoverageTracker() : CoverageTracker.load(_coverageFile);
            _facts = startingFacts();
            startNewPlan();
        }
        if (_stepTimer != null) {
            _stepTimer.reset();
        }
    }

    @Override
    protected Task onTick(Vibrato mod) {
        if (_stepTask != null) {
            if (_stepTask.isFinished(mod)) {
                completeStep(true, "finished");
            } else if (_stepTimer.elapsed()) {
                completeStep(false, "over budget");
            } else if (_stepTask.thisOrChildAreTimedOut()) {
                completeStep(false, "stuck");
            } else {
                return _stepTask;
            }
        }
        return nextStepTask(mod);
    }

    @Override
    protected void onStop(Vibrato mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DirectorTask task
                && task._seed == _seed
                && task._persona.equals(_persona)
                && task._registry == _registry;
    }

    @Override
    protected String toDebugString() {
        return "Director (" + _persona.name() + ", step " + Math.min(_stepIndex, _plan.steps().size())
                + "/" + _plan.steps().size() + ")";
    }

    /** The step currently running, as {@code id{k=v,...}}, or "idle" between steps. */
    public String currentStepDescription() {
        return _currentStep == null ? "idle" : _currentStep.signature();
    }

    private Set<Fact> startingFacts() {
        Set<Fact> facts = new HashSet<>();
        facts.add(Fact.dimension(WorldHelper.getCurrentDimension().name().toLowerCase(Locale.ROOT)));
        return facts;
    }

    private void completeStep(boolean success, String reason) {
        PlannedStep step = _currentStep;
        if (success) {
            _coverage.record(step.activity());
            PlanSampler.applyEffects(_facts, step.activity());
        } else {
            _coverage.recordFailure(step.activity().id(), FAILURE_COOLDOWN_PLANS);
            Debug.logInternal("Director: " + step.signature() + " failed (" + reason + "), moving on");
        }
        _stepTask = null;
        _currentStep = null;
    }

    private Task nextStepTask(Vibrato mod) {
        for (int skipped = 0; skipped < MAX_SKIPS_PER_TICK; skipped++) {
            if (_stepIndex >= _plan.steps().size()) {
                finishPlan();
                if (_plan.steps().isEmpty()) {
                    return null;
                }
            }
            PlannedStep step = _plan.steps().get(_stepIndex++);
            Task task = step.activity().create(mod, step.params());
            if (task == null) {
                Debug.logInternal("Director: " + step.signature() + " created no task, skipping");
                _coverage.recordFailure(step.activity().id(), FAILURE_COOLDOWN_PLANS);
                continue;
            }
            _currentStep = step;
            _stepTask = task;
            _stepTimer = new TimerGame(step.activity().budget(step.params()).toMillis() / 1000.0);
            _stepTimer.reset();
            return task;
        }
        return null;
    }

    private void finishPlan() {
        _coverage.tickPlan();
        saveCoverage();
        _planSeed = new SplittableRandom(_planSeed).nextLong();
        startNewPlan();
    }

    private void startNewPlan() {
        _stepIndex = 0;
        try {
            _plan = _sampler.sample(_planSeed, _planLength, _persona, _facts, _coverage);
        } catch (IllegalStateException e) {
            Debug.logInternal("Director could not plan from " + _facts + ": " + e.getMessage());
            _facts = startingFacts();
            _plan = new Plan(List.of());
        }
    }

    private void saveCoverage() {
        if (_coverageFile == null) {
            return;
        }
        try {
            _coverage.save(_coverageFile);
        } catch (IOException e) {
            Debug.logInternal("Director could not save coverage to " + _coverageFile + ": " + e.getMessage());
        }
    }
}
