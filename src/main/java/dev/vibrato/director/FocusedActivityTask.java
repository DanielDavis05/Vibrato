package dev.vibrato.director;

import com.google.gson.GsonBuilder;
import dev.vibrato.Debug;
import dev.vibrato.Vibrato;
import dev.vibrato.nav.SurfaceAccess;
import dev.vibrato.packetcoverage.PacketCoverage;
import dev.vibrato.tasks.build.BuildBlueprintTask;
import dev.vibrato.tasks.combat.SlayMobTask;
import dev.vibrato.tasks.construction.PlaceBlockTask;
import dev.vibrato.tasks.enchant.EnchantItemTask;
import dev.vibrato.tasks.enchant.GrindstoneTask;
import dev.vibrato.tasks.life.UnsupportedLifeTask;
import dev.vibrato.tasks.trade.AbstractVillagerTradeTask;
import dev.vibrato.tasks.travel.CompassDirection;
import dev.vibrato.tasksystem.Task;
import dev.vibrato.tasksystem.TaskChain;
import dev.vibrato.util.helpers.WorldHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Runs one fixed seeded goal and its providers; failed or unproven goals never become successful steps. */
public class FocusedActivityTask extends Task implements StepAbandonable {
    private final ActivityRegistry registry;
    private final String goal;
    private final long seed;
    private final Persona persona;
    private final Path reportPath;
    private final LongSupplier nanos;
    private final List<Map<String, Object>> outcomes = new ArrayList<>();
    private final Set<Fact> learned = new HashSet<>();
    private final FocusedActivityProof.Progress progress = new FocusedActivityProof.Progress();
    private List<PlannedStep> plan;
    private PlannedStep step;
    private Task child;
    private int index, serial;
    private long startedNanos, stepNanos, finishedNanos;
    private double startedWall, goalStartedWall, goalFinishedWall;
    private Double startClientSeconds, currentClientSeconds, goalStartClientSeconds, goalEndClientSeconds;
    private boolean complete, temporaryInterrupt;
    private FocusedActivityProof.Inventory before;
    private Map<String, Long> packetBaseline = Map.of();
    private String context;
    private Map<String, Object> mechanic = Map.of();
    private BlockPos travelTarget;
    private Vec3d lastBoatPosition;
    private final StepDeathTracker<Object> death = new StepDeathTracker<>();

    public FocusedActivityTask(ActivityRegistry registry, String goal, long seed, Persona persona) {
        this(registry, goal, seed, persona,
                MinecraftClient.getInstance().runDirectory.toPath().resolve("activity-result.json"), System::nanoTime);
    }

    FocusedActivityTask(ActivityRegistry registry, String goal, long seed, Persona persona, Path reportPath, LongSupplier nanos) {
        this.registry = registry; this.goal = goal; this.seed = seed; this.persona = persona;
        this.reportPath = reportPath; this.nanos = nanos;
    }

    public static Set<String> supportedIds() { return FocusedActivityProof.MECHANICS.keySet(); }
    public String activityId() { return goal; }
    @Override public int stepSerial() { return serial; }
    public boolean currentStepStandsStill() {
        return step != null && Set.of("fish", "breed_animals", "organize_inventory").contains(step.activity().id());
    }

    @Override protected void onStart(Vibrato mod) {
        if (plan != null) {
            if (context != null) PacketCoverage.beginScenario(context);
            return;
        }
        startedNanos = nanos.getAsLong();
        startedWall = System.currentTimeMillis() / 1000.0;
        startClientSeconds = clientSeconds(mod);
        report("started", null);
        if (!supportedIds().contains(goal)) { reject("activity has no completion proof adapter: " + goal); return; }
        if (!packetEvidenceEnabled()) { reject("focused activity requires -Dvibrato.packetCoverage=true"); return; }
        try {
            plan = new PlanSampler(registry).sampleGoal(seed, goal, persona, observeFacts(mod), new CoverageTracker()).steps();
        } catch (RuntimeException error) { reject("planning failed: " + error.getMessage()); }
    }

    @Override public void tick(Vibrato mod, TaskChain chain) {
        try { super.tick(mod, chain); }
        catch (RuntimeException error) { reject("activity exception: " + error); error.printStackTrace(); }
    }

    @Override protected Task onTick(Vibrato mod) {
        if (complete || failureReason() != null) return null;
        currentClientSeconds = clientSeconds(mod);
        if (!playerAlive(mod)) { reject("player died during focused activity"); return null; }
        if (step != null) {
            if (isGoal()) collectProgress(mod);
            if (finishedNanos != 0) {
                FocusedActivityProof.Verdict verdict = FocusedActivityProof.verify(goal, step.params(), before,
                        inventory(mod), progress, packetDeltas());
                mechanic = verdict.mechanic();
                if (verdict.confirmed()) {
                    finishStep("completed", verdict.reason()); complete = true; report("completed", null);
                } else if (nanos.getAsLong() - finishedNanos >= 5_000_000_000L) reject(verdict.reason());
                return null;
            }
            if (knownFailed(child)) { reject("activity task reported failure: " + child); return null; }
            if (child.isActive() && child.isFinished(mod)) {
                if (isGoal()) {
                    progress.taskOracle = goalOracle(child);
                    if (child instanceof AbstractVillagerTradeTask trade) progress.traded = trade.gained();
                    finishedNanos = nanos.getAsLong();
                    goalFinishedWall = System.currentTimeMillis() / 1000.0;
                    goalEndClientSeconds = currentClientSeconds;
                    return null;
                }
                learned.addAll(step.activity().provides());
                finishStep("completed", "prerequisite task finished");
                step = null; child = null;
            } else if (nanos.getAsLong() - stepNanos >= step.activity().budget(step.params()).toNanos()) {
                reject("activity budget exceeded: " + step.activity().id()); return null;
            } else return child;
        }
        if (hasChild()) return null; // detach before building an equal next task
        if (plan == null || index >= plan.size()) { reject("focused plan ended without goal proof"); return null; }
        step = plan.get(index++);
        stepNanos = nanos.getAsLong();
        Set<Fact> facts = new HashSet<>(learned);
        facts.removeIf(f -> f.key().startsWith("has:") || WorldFacts.isObservable(f));
        facts.addAll(observeFacts(mod));
        if (!facts.containsAll(step.activity().requires())) {
            reject("prerequisites missing before " + step.activity().id() + ": " + step.activity().requires()); return null;
        }
        child = step.activity().create(mod, step.params());
        if (child == null || knownFailed(child)) { reject("activity did not create a supported task: " + step.activity().id()); return null; }
        serial++;
        context = "activity." + goal + "." + (isGoal() ? "goal" : "provider." + serial);
        PacketCoverage.beginScenario(context);
        if (isGoal()) {
            goalStartedWall = System.currentTimeMillis() / 1000.0;
            goalStartClientSeconds = currentClientSeconds;
            before = inventory(mod); progress.inventory(before); packetBaseline = packetCounts();
            if (goal.equals("travel_boat_cross")) travelTarget = CompassDirection.valueOf(step.params().getString("direction"))
                    .offset(mod.getPlayer().getBlockPos(), step.params().getInt("distance"));
        }
        outcomes.add(row("started", null)); report("started", null);
        return child;
    }

    private boolean isGoal() { return index == plan.size(); }
    protected boolean packetEvidenceEnabled() { return PacketCoverage.enabled(); }
    protected double clientSeconds(Vibrato mod) { return mod.getPlayer().age / 20.0; }
    protected boolean playerAlive(Vibrato mod) {
        return death.observe(mod.getPlayer(), p -> ((net.minecraft.client.network.ClientPlayerEntity)p).getHealth() <= 0)
                == StepDeathTracker.State.ALIVE;
    }
    protected Set<Fact> observeFacts(Vibrato mod) {
        String dimension = WorldHelper.getCurrentDimension().name().toLowerCase(Locale.ROOT);
        List<String> held = new ArrayList<>();
        for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) {
            ItemStack stack = mod.getPlayer().getInventory().getStack(i);
            if (!stack.isEmpty()) held.add(Registries.ITEM.getId(stack.getItem()).getPath());
        }
        Set<Fact> facts = WorldFacts.compose(dimension, !dimension.equals("overworld") || SurfaceAccess.isAtSurface(mod), held);
        held.forEach(item -> facts.add(Fact.has(item)));
        return facts;
    }

    protected FocusedActivityProof.Inventory inventory(Vibrato mod) {
        Map<String, Integer> counts = new HashMap<>(), enchanted = new HashMap<>(), named = new HashMap<>();
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < mod.getPlayer().getInventory().size(); i++) {
            ItemStack stack = mod.getPlayer().getInventory().getStack(i);
            String item = Registries.ITEM.getId(stack.getItem()).getPath();
            slots.add(item + ":" + stack.getCount());
            if (stack.isEmpty()) continue;
            counts.merge(item, stack.getCount(), Integer::sum);
            if (stack.hasEnchantments() || item.equals("enchanted_book")) enchanted.merge(item, stack.getCount(), Integer::sum);
            named.merge(item + ":" + stack.getName().getString(), stack.getCount(), Integer::sum);
        }
        return new FocusedActivityProof.Inventory(Map.copyOf(counts), List.copyOf(slots), Map.copyOf(enchanted), Map.copyOf(named));
    }

    protected void collectProgress(Vibrato mod) {
        progress.inventory(inventory(mod));
        progress.screens.add(mod.getPlayer().currentScreenHandler.getClass().getSimpleName());
        progress.bobberSeen |= mod.getPlayer().fishHook != null;
        progress.placementChildSeen |= child.thisOrChildSatisfies(task -> task instanceof PlaceBlockTask);
        if (mod.getPlayer().getVehicle() instanceof BoatEntity boat) {
            progress.boatSeen = true;
            if (lastBoatPosition != null) progress.boatDistance += boat.getPos().distanceTo(lastBoatPosition);
            lastBoatPosition = boat.getPos();
        } else lastBoatPosition = null;
        if (travelTarget != null) progress.distanceToTravelTarget = mod.getPlayer().getPos().distanceTo(travelTarget.toCenterPos());
    }

    protected Map<String, Long> packetCounts() {
        return scopedPackets(PacketCoverage.snapshot(), context, getClass().getSimpleName());
    }
    static Map<String, Long> scopedPackets(PacketCoverage.Snapshot snapshot, String context, String root) {
        Map<String, Long> result = new TreeMap<>();
        for (PacketCoverage.Entry entry : snapshot.packets()) {
            if (entry.phase().equals("PLAY") && entry.scenario().equals(context)
                    && entry.rootTask().equals(root))
                result.merge(entry.side() + ":" + entry.id(), entry.count(), Long::sum);
        }
        return result;
    }
    private Map<String, Long> packetDeltas() {
        Map<String, Long> result = new TreeMap<>();
        packetCounts().forEach((key, count) -> { long delta = count - packetBaseline.getOrDefault(key, 0L); if (delta > 0) result.put(key, delta); });
        return result;
    }
    private static boolean knownFailed(Task task) {
        return task.failureReason() != null || task instanceof UnsupportedLifeTask
                || task instanceof EnchantItemTask t && t.failed()
                || task instanceof GrindstoneTask t && t.failed()
                || task instanceof AbstractVillagerTradeTask t && t.failed()
                || task instanceof SlayMobTask t && t.failed()
                || task instanceof BuildBlueprintTask t && t.isFinished(null) && !t.isReady();
    }
    private static boolean goalOracle(Task task) {
        return !knownFailed(task) && (!(task instanceof BuildBlueprintTask build) || build.isReady());
    }
    @Override protected boolean onChildFailure(Vibrato mod, Task failed, String reason) { reject(reason); return true; }
    @Override public void abandonCurrentStep(String reason) { reject("navigation abandoned focused goal: " + reason); }
    private void reject(String reason) {
        fail(reason);
        if (step != null) finishStep("failed", reason);
        report("failed", reason);
    }
    private void finishStep(String status, String reason) {
        outcomes.add(row(status, reason));
    }
    private Map<String, Object> row(String status, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("activity", step.activity().id()); row.put("params", step.params().asMap()); row.put("goal", isGoal());
        row.put("status", status); row.put("reason", reason); row.put("elapsedSeconds", Math.max(0, (nanos.getAsLong() - stepNanos) / 1e9));
        row.put("wall", System.currentTimeMillis() / 1000.0); row.put("clientSeconds", currentClientSeconds);
        return row;
    }
    private void report(String status, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("activity", goal); result.put("seed", seed);
        result.put("status", status); result.put("source", "focused_activity_task"); result.put("wall", System.currentTimeMillis() / 1000.0);
        result.put("startedWall", startedWall); result.put("startClientSeconds", startClientSeconds);
        result.put("goalStartedWall", goalStartedWall); result.put("goalFinishedWall", goalFinishedWall);
        result.put("goalStartClientSeconds", goalStartClientSeconds); result.put("goalEndClientSeconds", goalEndClientSeconds);
        result.put("elapsedSeconds", Math.max(0, (nanos.getAsLong() - startedNanos) / 1e9)); result.put("steps", outcomes);
        result.put("failureReason", reason);
        result.put("evidence", Map.of("goalTaskFinished", finishedNanos != 0, "actionObserved", complete,
                "mechanics", mechanic.isEmpty() ? List.of() : List.of(mechanic)));
        if (plan != null) result.put("plan", plan.stream().map(s -> Map.of("activity", s.activity().id(), "params", s.params().asMap(),
                "budgetSeconds", s.activity().budget(s.params()).toMillis() / 1000.0)).toList());
        try {
            Files.createDirectories(reportPath.toAbsolutePath().getParent());
            Path temp = reportPath.resolveSibling(reportPath.getFileName() + ".tmp");
            Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(result));
            Files.move(temp, reportPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) { fail("cannot write focused activity evidence: " + error); Debug.logWarning(error.toString()); }
    }
    @Override public void interrupt(Vibrato mod, Task interruptTask) {
        temporaryInterrupt = true;
        try { super.interrupt(mod, interruptTask); } finally { temporaryInterrupt = false; }
    }
    @Override protected void onStop(Vibrato mod, Task interruptTask) {
        PacketCoverage.endScenario();
        if (!temporaryInterrupt && !complete && failureReason() == null) reject("focused activity cancelled before confirmed completion");
    }
    @Override public boolean isFinished(Vibrato mod) { return complete && failureReason() == null; }
    @Override protected boolean isEqual(Task other) { return other instanceof FocusedActivityTask task && goal.equals(task.goal) && seed == task.seed; }
    @Override protected String toDebugString() { return "Focused activity " + goal + " (" + index + "/" + (plan == null ? "?" : plan.size()) + ")"; }
}
