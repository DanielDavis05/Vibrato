package dev.vibrato.director;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Draws random plans from the registry. Activities are picked by weight (persona bias, boosted for under-used
 * {@link FeatureArea}s); unmet prerequisites are filled by inserting provider activities, depth-limited.
 * Same inputs always give the same plan.
 */
public final class PlanSampler {

    /** How many provider levels may be stacked under one step. */
    static final int MAX_DEPTH = 3;

    static final String DIMENSION_PREFIX = "dim:";

    private static final String ALGORITHM = "L64X128MixRandom";

    private final ActivityRegistry registry;

    public PlanSampler(ActivityRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param length number of top-level steps; provider steps are added on top of these
     * @throws IllegalStateException if no activity can be chained from the facts
     */
    public Plan sample(long seed, int length, Persona persona, Set<Fact> startFacts, CoverageTracker coverage) {
        RandomGenerator rng = RandomGeneratorFactory.of(ALGORITHM).create(seed);
        Set<Fact> facts = new HashSet<>(startFacts);
        List<PlannedStep> steps = new ArrayList<>();
        for (int chosen = 0; chosen < length; chosen++) {
            if (!appendCandidate(steps, facts, rng, persona, coverage)) {
                throw new IllegalStateException("No activity can run after " + steps.size()
                        + " steps from facts " + facts + " (registry has " + registry.size() + " activities)");
            }
        }
        return new Plan(steps);
    }

    /** Facts gained from running an activity. A {@code dim:} fact replaces the previous dimension. */
    static void applyEffects(Set<Fact> facts, Activity activity) {
        Set<Fact> provides = activity.provides();
        if (provides.stream().anyMatch(fact -> fact.key().startsWith(DIMENSION_PREFIX))) {
            facts.removeIf(fact -> fact.key().startsWith(DIMENSION_PREFIX));
        }
        facts.addAll(provides);
    }

    /** A precomputed step can become invalid when an earlier step fails or the world changes. */
    static boolean canRunNow(Activity activity, Set<Fact> facts, CoverageTracker coverage) {
        return !coverage.isOnCooldown(activity.id()) && facts.containsAll(activity.requires());
    }

    static ParamValues sampleParams(Activity activity, RandomGenerator rng) {
        Map<String, Object> values = new HashMap<>();
        for (ParamSpec spec : activity.params()) {
            values.put(spec.name(), spec.sample(rng));
        }
        return new ParamValues(values);
    }

    static List<Fact> sortedFacts(Set<Fact> facts) {
        return facts.stream().sorted(Comparator.comparing(Fact::key)).toList();
    }

    private boolean appendCandidate(List<PlannedStep> steps, Set<Fact> facts, RandomGenerator rng,
                                    Persona persona, CoverageTracker coverage) {
        List<Activity> pool = eligibleFromPool(registry.all(), coverage);
        while (!pool.isEmpty()) {
            Activity candidate = pick(pool, rng, persona, coverage, steps);
            pool.remove(candidate);
            List<PlannedStep> trial = new ArrayList<>(steps);
            Set<Fact> trialFacts = new HashSet<>(facts);
            if (!satisfy(candidate, 0, trial, trialFacts, rng, persona, coverage)) {
                continue;
            }
            if (candidate.id().equals(lastId(trial))) {
                continue;
            }
            trial.add(new PlannedStep(candidate, sampleParams(candidate, rng)));
            applyEffects(trialFacts, candidate);
            steps.clear();
            steps.addAll(trial);
            facts.clear();
            facts.addAll(trialFacts);
            return true;
        }
        return false;
    }

    /** Adds provider steps until every requirement of {@code activity} holds. {@code depth} is its provider level. */
    private boolean satisfy(Activity activity, int depth, List<PlannedStep> steps, Set<Fact> facts,
                            RandomGenerator rng, Persona persona, CoverageTracker coverage) {
        for (Fact fact : sortedFacts(activity.requires())) {
            if (facts.contains(fact)) {
                continue;
            }
            if (depth >= MAX_DEPTH || !insertProvider(fact, depth + 1, steps, facts, rng, persona, coverage)) {
                return false;
            }
        }
        return true;
    }

    private boolean insertProvider(Fact fact, int depth, List<PlannedStep> steps, Set<Fact> facts,
                                   RandomGenerator rng, Persona persona, CoverageTracker coverage) {
        List<Activity> pool = eligibleFromPool(registry.providersOf(fact), coverage);
        while (!pool.isEmpty()) {
            Activity provider = pick(pool, rng, persona, coverage, steps);
            pool.remove(provider);
            List<PlannedStep> trial = new ArrayList<>(steps);
            Set<Fact> trialFacts = new HashSet<>(facts);
            if (!satisfy(provider, depth, trial, trialFacts, rng, persona, coverage)) {
                continue;
            }
            if (provider.id().equals(lastId(trial))) {
                continue;
            }
            trial.add(new PlannedStep(provider, sampleParams(provider, rng)));
            applyEffects(trialFacts, provider);
            steps.clear();
            steps.addAll(trial);
            facts.clear();
            facts.addAll(trialFacts);
            return true;
        }
        return false;
    }

    private static List<Activity> eligibleFromPool(List<Activity> activities, CoverageTracker coverage) {
        List<Activity> pool = new ArrayList<>();
        for (Activity activity : activities) {
            if (!coverage.isOnCooldown(activity.id())) {
                pool.add(activity);
            }
        }
        return pool;
    }

    private static Activity pick(List<Activity> pool, RandomGenerator rng, Persona persona,
                                 CoverageTracker coverage, List<PlannedStep> steps) {
        Map<FeatureArea, Integer> plannedAreas = new EnumMap<>(FeatureArea.class);
        Map<String, Integer> plannedActivities = new HashMap<>();
        for (PlannedStep step : steps) {
            Activity activity = step.activity();
            plannedAreas.merge(activity.area(), 1, Integer::sum);
            plannedActivities.merge(activity.id(), 1, Integer::sum);
        }

        // Divide each area's share among its eligible activities. A large registry area
        // therefore does not gain probability simply by having more entries. Novelty
        // changes the distribution within an area without starving single-entry areas.
        Map<FeatureArea, Double> areaTotals = new EnumMap<>(FeatureArea.class);
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            Activity activity = pool.get(i);
            int used = coverage.uses(activity.id());
            int planned = plannedActivities.getOrDefault(activity.id(), 0);
            weights[i] = activity.weight(persona) / Math.sqrt(1.0 + used + 3.0 * planned);
            areaTotals.merge(activity.area(), weights[i], Double::sum);
        }
        double total = 0;
        for (int i = 0; i < pool.size(); i++) {
            FeatureArea area = pool.get(i).area();
            int used = coverage.uses(area);
            int planned = plannedAreas.getOrDefault(area, 0);
            double areaWeight = persona.weightFor(area) / (1.0 + used + planned * (1.0 + used / 4.0));
            weights[i] = weights[i] / areaTotals.get(area) * areaWeight;
            total += weights[i];
        }
        double roll = rng.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            roll -= weights[i];
            if (roll < 0) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    private static String lastId(List<PlannedStep> steps) {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1).activity().id();
    }
}
