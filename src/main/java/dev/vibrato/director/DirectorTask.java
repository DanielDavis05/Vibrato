package dev.vibrato.director;

import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.nav.SurfaceAccess;
import dev.vibrato.tasks.movement.IdleTask;
import dev.vibrato.tasks.movement.TimeoutWanderTask;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import dev.vibrato.util.time.TimerGame;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Plays forever by running randomly sampled plans one step at a time. A step succeeds when its task finishes; it fails
 * when its budget runs out or the task gets stuck, and then it is put on cooldown and the plan moves on.
 */
public class DirectorTask extends Task implements StepAbandonable {

    /** Activities that deliberately stay put, so navigation safety must not try to unstick them. */
    private static final Set<String> STATIONARY_ACTIVITIES = Set.of(
            "fish", "sleep_through_night", "set_spawn_point", "night_shelter", "wait_shelter");

    private static final int DEFAULT_PLAN_LENGTH = 10;
    private static final int FAILURE_COOLDOWN_PLANS = 3;
    /** Upper bound on steps that yield no task within a single tick, so a broken registry cannot hang the tick. */
    private static final int MAX_SKIPS_PER_TICK = 32;
    private static final double BREATHER_SECONDS = 15;
    private static final double TICKS_PER_SECOND = 20;

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
    private StepHealth _health = new StepHealth();
    private Task _stuckWitness;
    private final FailureChurn _churn = new FailureChurn();
    private InterruptTracker _interrupts = new InterruptTracker();
    private int _stepSerial;
    private long _tick;
    private long _stepStartTick;
    private boolean _breatherPending;
    private boolean _onBreather;

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
            _facts = observedFacts(mod);
            startNewPlan(mod);
        }
        if (_stepTimer != null) {
            _stepTimer.reset();
        }
        if (_stepTask != null && !_onBreather) {
            // Restarted in the middle of a step: something (a chain) interrupted it.
            _interrupts.interrupted(_tick, progressKey(mod));
        }
    }

    @Override
    public void abandonCurrentStep(String reason) {
        if (_currentStep != null) {
            completeStep(false, reason);
        }
    }

    @Override
    public int stepSerial() {
        return _stepSerial;
    }

    /** True while the running step is one that deliberately stays in place. */
    public boolean currentStepStandsStill() {
        return _currentStep != null && STATIONARY_ACTIVITIES.contains(_currentStep.activity().id());
    }

    private static long progressKey(Vibrato mod) {
        var player = mod.getPlayer();
        var inventory = player.getInventory();
        long items = 0;
        for (int i = 0; i < inventory.size(); i++) {
            items += inventory.getStack(i).getCount();
        }
        return Objects.hash(player.getBlockX(), player.getBlockY(), player.getBlockZ(), items);
    }

    @Override
    protected Task onTick(Vibrato mod) {
        _tick++;
        if (_onBreather) {
            if (!_stepTimer.elapsed()) {
                return _stepTask;
            }
            _onBreather = false;
            _stepTask = null;
        } else if (_stepTask != null) {
            if (_interrupts.harassed(_tick)) {
                completeStep(false, "harassed by interruptions");
            } else if (_stepTask.isFinished(mod)) {
                completeStep(true, "finished");
            } else if (_stepTimer.elapsed()) {
                completeStep(false, "over budget");
            } else {
                // A wander inside the step is normal (waiting for a path); only a persistent one counts as stuck.
                _stuckWitness = null;
                boolean stuckNow = _stepTask.thisOrChildSatisfies(task -> {
                    if (task instanceof TimeoutWanderTask) {
                        _stuckWitness = task;
                        return true;
                    }
                    return false;
                });
                _health.tick(stuckNow);
                if (!_health.verdict()) {
                    return _stepTask;
                }
                completeStep(false, "stuck in " + _stuckWitness);
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

    public String currentStepId() {
        return _currentStep == null ? "none" : _currentStep.activity().id();
    }

    /** The step currently running, as {@code id{k=v,...}}, or "idle" between steps. */
    public String currentStepDescription() {
        return _currentStep == null ? "idle" : _currentStep.signature();
    }

    /** Facts the bot can read off the world right now. */
    private static Set<Fact> observedFacts(Vibrato mod) {
        String dimension = WorldHelper.getCurrentDimension().name().toLowerCase(Locale.ROOT);
        boolean atSurface = !"overworld".equals(dimension) || SurfaceAccess.isAtSurface(mod);
        List<String> held = new ArrayList<>();
        var inventory = mod.getPlayer().getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                held.add(Registries.ITEM.getId(stack.getItem()).getPath());
            }
        }
        return WorldFacts.compose(dimension, atSurface, held);
    }

    /** Re-reads what the world shows, and keeps the facts that cannot be observed (spawn set, food stocked, ...). */
    private Set<Fact> factsAtPlanBoundary(Vibrato mod) {
        Set<Fact> facts = observedFacts(mod);
        for (Fact fact : _facts) {
            if (!WorldFacts.isObservable(fact)) {
                facts.add(fact);
            }
        }
        return facts;
    }

    @Override
    public void tick(Vibrato mod, TaskChain parentChain) {
        // A broken activity must fail its step, never the whole client.
        StepGuard.run(() -> super.tick(mod, parentChain), this::onStepException);
    }

    private void onStepException(RuntimeException e) {
        if (_currentStep != null) {
            Debug.logInternal("Director step " + _currentStep.signature() + " threw: " + e);
            e.printStackTrace();
            completeStep(false, "exception: " + e.getMessage());
        } else {
            Debug.logInternal("Director threw outside a step: " + e);
            e.printStackTrace();
            _stepTask = null;
            _onBreather = false;
        }
    }

    private void completeStep(boolean success, String reason) {
        PlannedStep step = _currentStep;
        double seconds = (_tick - _stepStartTick) / TICKS_PER_SECOND;
        if (success) {
            _coverage.record(step.activity());
            PlanSampler.applyEffects(_facts, step.activity());
            Debug.logInternal("Director step " + step.signature() + " succeeded after " + seconds + "s: " + reason);
        } else {
            Debug.logInternal("Director step " + step.signature() + " failed after " + seconds + "s: " + reason);
            failed(step);
        }
        _stepTask = null;
        _currentStep = null;
        dev.vibrato.packetcoverage.PacketCoverage.setTask(getClass().getSimpleName(), "none");
        _stuckWitness = null;
    }

    /** Puts the activity on cooldown, and starts a breather when failures pile up. */
    private void failed(PlannedStep step) {
        _churn.failed(_tick);
        int cooldown = FAILURE_COOLDOWN_PLANS;
        if (_churn.churning(_tick)) {
            cooldown *= 2;
            _breatherPending = true;
            _churn.clear();
            Debug.logInternal("Director: failures are piling up, taking a breather and doubling cooldowns");
        }
        _coverage.recordFailure(step.activity().id(), cooldown);
    }

    private Task nextStepTask(Vibrato mod) {
        if (_breatherPending) {
            _breatherPending = false;
            _onBreather = true;
            _stepTask = new IdleTask();
            _stepTimer = new TimerGame(BREATHER_SECONDS);
            _stepTimer.reset();
            return _stepTask;
        }
        for (int skipped = 0; skipped < MAX_SKIPS_PER_TICK; skipped++) {
            if (_stepIndex >= _plan.steps().size()) {
                finishPlan(mod);
                if (_plan.steps().isEmpty()) {
                    return null;
                }
            }
            PlannedStep step = _plan.steps().get(_stepIndex++);
            // Plans assume that every earlier step succeeds. Recheck against the real world before
            // starting a step, so a failed provider or a new cooldown cannot cause a cascade.
            _facts = factsAtPlanBoundary(mod);
            if (!PlanSampler.canRunNow(step.activity(), _facts, _coverage)) {
                Debug.logInternal("Director skipping " + step.signature() + ": prerequisites or cooldown changed");
                continue;
            }
            String[] error = {null};
            Task task = StepGuard.call(() -> step.activity().create(mod, step.params()), e -> {
                error[0] = "exception: " + e;
                Debug.logInternal("Director step " + step.signature() + " threw while building its task: " + e);
                e.printStackTrace();
            });
            if (task == null) {
                String reason = error[0] != null ? error[0] : "no task was built";
                Debug.logInternal("Director step " + step.signature() + " failed: " + reason);
                failed(step);
                continue;
            }
            _currentStep = step;
            dev.vibrato.packetcoverage.PacketCoverage.setTask(getClass().getSimpleName(), step.activity().id());
            _stepTask = task;
            _stepStartTick = _tick;
            _health = new StepHealth();
            _interrupts = new InterruptTracker();
            _stepSerial++;
            _stepTimer = new TimerGame(step.activity().budget(step.params()).toMillis() / 1000.0);
            _stepTimer.reset();
            return task;
        }
        return null;
    }

    private void finishPlan(Vibrato mod) {
        _coverage.tickPlan();
        saveCoverage();
        _planSeed = new SplittableRandom(_planSeed).nextLong();
        _facts = factsAtPlanBoundary(mod);
        startNewPlan(mod);
    }

    private void startNewPlan(Vibrato mod) {
        _stepIndex = 0;
        try {
            Plan plan = _sampler.sample(_planSeed, _planLength, _persona, _facts, _coverage);
            if (_facts.contains(WorldFacts.UNDERGROUND) && !WorldFacts.hasPickaxe(_facts)) {
                plan = escapeFirst(plan);
            }
            _plan = plan;
            for (int i = 0; i < plan.steps().size(); i++) {
                PlannedStep step = plan.steps().get(i);
                Debug.logInternal("Director plan " + (i + 1) + "/" + plan.steps().size() + ": "
                        + step.signature() + " [" + step.activity().area() + "]");
            }
        } catch (IllegalStateException e) {
            Debug.logInternal("Director could not plan from " + _facts + ": " + e.getMessage());
            _facts = observedFacts(mod);
            _plan = new Plan(List.of());
        }
    }

    /** Underground without a pickaxe, the plan must start by getting to the surface. */
    private Plan escapeFirst(Plan plan) {
        Activity escape = _registry.get("escape_underground");
        if (escape == null || (!plan.steps().isEmpty() && plan.steps().get(0).activity() == escape)) {
            return plan;
        }
        return plan.prepend(new PlannedStep(escape, ParamValues.EMPTY));
    }

    /** True while the running step is the given activity. */
    public boolean currentStepIs(String id) {
        return _currentStep != null && _currentStep.activity().id().equals(id);
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
