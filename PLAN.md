# Vibrato — Implementation Plan

Companion to [FEATURES.md](FEATURES.md). This plan is the contract for parallel work: every workstream owns a
set of files, plugs into a shared interface, and is verifiable on its own.

## 0. Findings that shape the plan

| Finding | Consequence |
|---|---|
| Repo is a 33k-line AltoClef fork (Fabric, 1.21.1, yarn). It already has a task system, chains (food, mob defense, MLG, death), trackers, ~160 tasks, a `BeatMinecraft2Task` speedrun, a command system, and a `Butler` (whisper commands). | Keep and extend; do not rewrite. New behavior goes into new packages that plug into the existing `Task` / `TaskRunner` / chains. |
| The build needs a *patched* Baritone (`baritone.altoclef.AltoClefSettings`, `RayTraceUtils.fluidHandling`) from `../baritone/dist`. That patch is not available upstream (verified: not in `cabaletta/baritone` 1.21.1 nor `gaucho-matrero/baritone` master/1.19.4). The build is currently broken. | Vendor `cabaletta/baritone` branch `1.21.1` as a **git submodule** (`baritone/`), keep it pristine, and carry our hooks as `patches/baritone/*.patch` applied by a Gradle task. We author the hooks (`baritone.vibrato.VibratoSettings`), shaped by how Vibrato calls it. |
| Upstream Baritone `1.21.1` is a deprecated branch (builds against MC 1.21, `mod_version` 1.11.3). 1.21 and 1.21.1 are wire/mapping compatible. | Acceptable. Pin the submodule to a commit; document it. |
| UnionClef (3ndetz) is large (224k LOC, GPL-3.0) and abandoned Baritone for its own physics pathfinder (`tungsten`). It has useful, documented ideas: WindMouse camera, pixel-quantized rotation, yaw-rate cap, no-edge-jump margin, night shelter, bucket portal, arrow dodging, MCP/Py4J control. | Borrow **ideas and algorithms** (WindMouse is a public algorithm), re-implement clean-room against Baritone. Do **not** copy GPL-3 source into this MIT/LGPL tree without an explicit relicensing decision (see Open decisions). |
| Baritone has `randomLooking` (jitter) and `smoothLook` already. | Phase 1 sets them (jitter 0, smooth on) as a baseline; Phase 3 replaces the smoothing with a real human-camera model via a Baritone hook. |
| Running ~10 bots = ~10 Minecraft client processes (the mod is client-side). | A **fleet launcher** (separate instances, offline accounts, per-instance seed/persona/options) plus a low-resource performance profile. |
| No Xvfb, no baritone checkout, `DISPLAY=:0` present on the dev box. | Automated verification = compile + unit tests of pure logic + a deterministic simulation of the planner. In-game smoke tests are run only with the user's go-ahead because they open game windows on their display. |

## 1. Architecture

Root package `dev.vibrato` (renamed from `adris.altoclef`). New code lives in new sub-packages:

```
dev.vibrato
  Vibrato                      main client entrypoint (was AltoClef)
  director/                    NEW  random task composition + execution   (workstream D)
    FeatureArea, Fact, ParamSpec, ParamValues
    Activity, ActivityRegistry, BuiltinActivities (+ one XxxActivities per domain)
    PlanSampler, Plan, PlanSpace (Knuth estimator), CoverageTracker, Persona
    DirectorTask               executes a plan, one watchdogged step at a time
  nav/                         NEW  never-stuck navigation                (workstream N)
    StuckDetector, UnstuckLadder, ProgressWatchdog, WasteGuard
    WaterNavigator, EdgeGuard, WaypointMemory
  human/                       NEW  human-like control                    (workstream H)
    HumanLook (WindMouse + minimum-jerk, pixel-quantized), HumanInput, IdleBehavior
  tasks/<domain>/              NEW tasks per game feature                 (workstreams T1..T6)
  fleet/                       NEW  multi-bot identity + launcher         (workstream F)
    FleetConfig, scripts/fleet/launch.py
  ...existing: tasksystem, chains, trackers, tasks, util, commands, butler...
baritone/                      submodule (cabaletta/baritone @1.21.1)
patches/baritone/              our hooks (VibratoSettings, smoothing hook, ray fluid handling)
```

### Director contract (all task workstreams code against this)

```java
enum FeatureArea { GATHER, CRAFT, SMELT, BUILD, EXPLORE, SLEEP, TRADE, ENCHANT, GEAR, LEVEL,
                   ADVANCEMENT, COMBAT, TRANSPORT, INVENTORY, CHEST, FARM, SOCIAL, COMMAND, CHAT }

record Fact(String key) {}                       // symbolic world knowledge, e.g. "has:iron_pickaxe", "dim:nether"

interface Activity {
    String id();                                  // unique, stable, snake_case
    FeatureArea area();
    Set<Fact> requires();                         // symbolic preconditions
    Set<Fact> provides();                         // symbolic effects
    List<ParamSpec> params();                     // sampled by the planner; product of domain sizes = variants
    double weight(Persona p);                     // base selection weight
    Task create(Vibrato mod, ParamValues v);      // the real in-game task
    Duration budget(ParamValues v);               // hard wall-clock cap for the watchdog
}
```

* `ActivityRegistry` is filled by `BuiltinActivities.registerAll()`, which calls one
  `XxxActivities.register(registry)` per domain. Workstreams only edit **their own** `XxxActivities` file.
* `PlanSampler.sample(seed, length, persona, startFacts)`: weighted random choice among activities whose
  `requires` are satisfied; unmet requirements pull in provider activities (depth-limited). Weights are boosted
  for under-covered `FeatureArea`s (per-bot `CoverageTracker`, persisted) so every feature gets hit.
* `PlanSpace.estimateLog10(registry, length)`: Knuth's unbiased tree-size estimator over sampled walks
  (branching factor × parameter variants per step). Unit-tested to be ≥ 10^8 (target ≫ 10^20).
* `DirectorTask`: runs steps in order; each step is wrapped in a progress watchdog + budget. On failure or
  timeout it records the failure, applies a per-activity cooldown, and resamples instead of retrying forever.
* `Persona` = per-bot weight vector (builder, miner, explorer, trader, fighter, farmer, socialite, generalist)
  so ~10 bots with different seeds *and* different biases diverge from each other.

## 2. Workstreams

| ID | Scope | Owner | Depends on |
|---|---|---|---|
| P0a | Rename to Vibrato (package, ids, resources, strings, docs) | Haiku | — |
| P0b | Baritone submodule + hooks patch + Gradle wiring; build green | Lead | P0a |
| P0c | Scaffold director/nav/human/fleet interfaces + stubs + JUnit setup | Lead | P0b |
| D | Director: registry, sampler, Knuth estimator, coverage, personas, `DirectorTask`, unit tests | Haiku (spec by Lead) | P0c |
| N | Nav robustness: stuck detector (pure + tested), unstuck ladder, water navigator, edge guard, waste guard, waypoint memory | Lead + Haiku | P0c |
| H | Human control: `HumanLook`, input timing, idle glances, Baritone smoothing hook, settings baseline | Lead (hook) + Haiku (math + tests) | P0b |
| T1 | Trade + enchant + anvil/grindstone + brewing | Haiku | P0c |
| T2 | Sleep/bed, farming, breeding, fishing, food, XP leveling | Haiku | P0c |
| T3 | Transport (boat, minecart, horse, elytra, portals) + exploration (biomes/structures/villages) | Haiku | P0c |
| T4 | Building (house/tower/bridge/farm blueprints) + chest/inventory organization | Haiku | P0c |
| T5 | Social/chat/commands (whitelisted vanilla + Butler), bot↔bot interaction, advancement tracker + goals | Haiku | P0c |
| T6 | Combat + mobs (shield, bow, mob-specific tactics, gear upgrades) | Haiku | P0c |
| F | Fleet: `FleetConfig` (system props), per-instance `options.txt` perf profile, `scripts/fleet/launch.py` | Haiku | P0c |
| I | Integration: merge, `./gradlew build`, full test run, docs, coverage report | Lead | all |

Each Haiku workstream gets: exact file ownership, the interface it implements, tests it must write, the
commands to verify (`./gradlew build test`), and an instruction **not** to edit files it does not own.
Parallel agents run in separate git worktrees (own build dir); the Lead merges and resolves conflicts.

## 3. Robustness design (requirement 3)

Layered, cheapest first:

1. **Per-task progress watchdog** — extends `util/progresscheck`. No measurable progress (position delta,
   inventory delta, XP, block-break count) in *N* seconds → task aborted with a reason.
2. **StuckDetector** (pure logic, unit-tested). Sliding window over position/yaw/input; classifies
   `STATIONARY_WITH_INTENT`, `OSCILLATING` (A-B-A-B), `SPINNING`, `EDGE_JAM` (horizontal collision while
   pressing forward), `WATER_DRIFT` (in water, low progress or low air).
3. **UnstuckLadder** — escalating, each rung tried once: jump → strafe sidestep → back-off + re-path with the
   bad node penalized → break/place block → dig up/down → abandon task.
4. **WaterNavigator** — swim with sprint-swim, surface for air before it is critical, prefer boat/bridge over
   long swims, climb out at banks (the "freeze at a 1-block bank" bug class), avoid drowning.
5. **EdgeGuard** — no sprint-jump from block edges (min margin ~0.15), re-center on corners before jumping,
   sneak at drops it does not intend to take.
6. **WaypointMemory** — persisted home/bed/chests/death/portal coordinates so "lost" has a recovery action.
7. **WasteGuard** — activity wall-clock budget + diminishing-returns detection → skip and move on.

## 4. Human-like control (requirements 3 and 4)

* `HumanLook`: WindMouse (gravity + wind) with a minimum-jerk speed profile, per-tick angular speed cap,
  pixel-quantized deltas (matches real mouse sensitivity steps), small overshoot-and-correct on large turns,
  zero idle jitter.
* Baritone hook: a `RotationSmoother` interface in the patched `LookBehavior`; Vibrato supplies `HumanLook`.
* `HumanInput`: reaction-time delays before first input, no frame-perfect key toggling, occasional look-around.
* `IdleBehavior`: glances, brief pauses while waiting on slow tasks instead of statues.

## 5. Performance (requirement 4)

* `PerformanceProfile` applied at startup: low render/simulation distance, capped FPS, minimal particles,
  muted audio, fast graphics. Mandatory in fleet mode.
* Tracker work is budgeted per tick (`BlockTracker`/`EntityTracker` scans amortized); pathing already runs off
  the main thread in Baritone.
* Tick-time telemetry (`PerfBudget`) logs any bot spending > 20 ms/tick in Vibrato code.

## 6. Verification strategy

1. `./gradlew build test` must pass at every merge (compiles against the patched Baritone jar).
2. Pure-logic unit tests: planner determinism and constraints, Knuth estimate ≥ 10^8, coverage boosting,
   StuckDetector classification on scripted traces, HumanLook angular-speed and quantization properties.
3. A simulated "coverage run": sample N plans for many seeds/personas and assert every `FeatureArea` appears.
4. In-game smoke tests (single bot, then fleet) against a local 1.21.1 dedicated server, run with the user's
   approval. Anything not exercised in-game is reported as **unverified in game**.

## 7. Git workflow

Small commits per logical step, pushed to `origin/master` after each phase (Co-Authored-By trailer). Worktree
branches for parallel workstreams are merged by the Lead.

## 8. Open decisions (defaults chosen; change if you disagree)

* **Package/mod id**: `dev.vibrato` / `vibrato`.
* **Pathfinder**: Baritone primary (your call). Tungsten-style simulation pathfinding is deferred.
* **Baritone patches**: carried as `patches/baritone/*.patch` over a pristine submodule rather than a fork
  (so no new GitHub repo is needed). If you prefer a fork, point `.gitmodules` at it and drop the patch step.
* **Licensing**: Vibrato stays MIT; UnionClef (GPL-3.0) is idea-only. Copying its code would make the combined
  work GPL-3.0 — say so if you want that.
* **Fleet** = N game clients, not N bots in one JVM (the mod is bound to the client singleton).

## 9. Status (updated 2026-10-27)

`./gradlew build` is green on `master` with 99 unit tests. **Nothing has been run in the game yet**: every
runtime behavior below is compile-checked and unit-tested for its pure logic only.

| Workstream | State | Notes |
|---|---|---|
| P0 rename, Baritone submodule + patch, Director contract | done | |
| D Director (sampler, Knuth estimator, coverage, personas, `DirectorTask`, 34 core activities) | done | follow-up in progress: `FleetBootstrap` (autostart on join, auto-connect) |
| N nav safety (stuck detector, unstuck ladder, waste guard, waypoints, `NavSafetyChain`) | done | thresholds untuned; chain interplay with `PlayerInteractionFixChain`/`WorldSurvivalChain` unverified |
| H human camera (`HumanLook`, input timing, idle glances) | done | `LookHelper.lookAt` is smoothed; use `lookAtInstant` only for same-tick aim-and-act (MLG clutch) |
| F fleet (`FleetConfig`, `PerformanceProfile`, `scripts/fleet/launch.py`) | done | launcher install/up paths untested; unfocused 20 FPS cap not implemented |
| T1 trade / enchant / anvil / brew | in progress | helpers + tests written, tasks and activities pending |
| T2 sleep / shelter / farm / breed / fish / xp | in progress | crop/breeding/xp helpers, sleep + night shelter written |
| T5 chat / commands / advancements | paused (only `TypingDelay`) | see findings below |
| T6 combat / mob tactics / gear | paused (no code) | see findings below |
| T4 building / chests / inventory | paused (no code; design settled) | blueprint coordinates: dy=0 ground, doors dy=1..2, palette is item-id strings only |
| T3 transport / exploration | paused (`WaterCrossingPlanner`, `CompassDirection`, `DiscoveryLog` written) | |

Resume order once slots free up (max 2-3 agents at a time): T5, T6, T4, T3.

Findings carried over from the paused agents:
* Advancement progress: `MinecraftClient.getNetworkHandler().getAdvancementHandler()`, map field
  `ClientAdvancementManager.advancementProgresses` (needs one accessor mixin). Only about 11 overworld advancements
  map onto existing `get` commands; the rest need new tasks (sleep, trade, breed, kill, plant), so the advancement
  table must not fake specs. `Activity.provides()` cannot depend on params, so each advancement is its own activity.
* Entities in `EntityTracker` are keyed by exact class; `EntityType` lookups must iterate `getWorld().getEntities()`.
* `ArmorRequirement` is deprecated; combat gear upgrades use a tier enum with catalogue names, one activity per tier.
* The brief's "SCP/ShootArrow" trajectory code does not exist; use `ProjectileHelper.calculateAnglesForSimpleProjectileMotion`.
* Boat/minecart/elytra/horse behavior is the least certain area; the planner decides swim vs boat vs bridge.

Open items for the lead: run a single-bot smoke test in the game (needs the user's go-ahead because it opens a
window); decide whether to build a headless test stand (Docker, software GL, offline server) so agents can verify
in-game behavior without touching the user's display.

## 10. Final pass: make it one system (planned 2026-10-27)

Starts when T4, T6 and the first smoke test report; T3 (boats and exploration) is the last feature workstream.
At most 3 agents at a time. Each item is an agent assignment; the lead reviews, merges and verifies.

### Integration workstreams (I)

| ID | What | Why it matters |
|---|---|---|
| I1 | **Step-scoped abandon.** `NavSafetyChain` handles ABANDON_TASK with `getUserTaskChain().cancel()`, and `WasteGuard` has a 30 min per-user-task budget; the Director *is* the user task, so either would kill the whole Director for the rest of the session. Add a `StepAbandonable` interface (implemented by `DirectorTask.abandonCurrentStep(reason)`), make NavSafety and WasteGuard act on the current step when it exists, and let `FleetBootstrap` restart a dead Director | Found in review; confirmed in code |
| I2 | **Registry integrity tests on the full registry**: every domain contributes, unique ids, every `requires` fact has a provider or is a start fact (e.g. trade needs `village:found`, which only T3 provides), every activity is reachable in sampled plans, a coverage simulation over personas x seeds hits every FeatureArea, the real `PlanSpace` log10 is reported and asserted >= 8, budgets and params sane, no activity can be sampled whose factory returns null | Proves the connection between planner and the ~120 activities |
| I3 | **Chain priority and input-conflict audit**: list every chain and priority; check NavSafety does not hijack during eating, fighting, MLG clutch, death screen, container screens; check key-hold conflicts with `PlayerInteractionFixChain` and `WorldSurvivalChain`; fix by gating, add tests for the pure gating | Prevents odd behavior and fights between systems |
| I4 | **One camera, one input path**: audit all rotation writes (`InputControls.forceLook`, `LookHelper`, direct `setYaw`), all sprint/jump/sneak toggles; route through the human model unless same-tick aim is required and justified | "Human" must be true everywhere, not just in Baritone |
| I5 | **Operator surface**: commands `@auto`, `@plan`, `@coverage`, `@persona`, `@fleet` (start/stop/inspect the Director), update `@help`, `usage.md`, overlay shows current plan step, consistent `[Vibrato]` log prefix; move tunables (chat rate, stuck thresholds, human look) into `Settings`/`vibrato_settings.json` | One coherent product, controllable by hand |
| I6 | **Performance**: `PerfBudget` telemetry (log any bot spending > 20 ms/tick in Vibrato code), audit per-tick allocations and scans in new chains and trackers | PLAN section 5; ten bots |
| I7 | **Docs and cleanup**: README, FEATURES.md vs reality, dead code, `Playground`, stale references | |

### I8 - Scenario harness (added 2026-10-27 after the first live runs)

The first live runs showed that "it works" cannot be judged from a long random run: failures are specific
(false unstuck while mining, nerd poling / pillar jumps, corners and edges, water). Add `-Dvibrato.scenario=<name>`
to the test launch: an auto-created cheats-on world, scripted setup (`/give`, `/tp`, `/time`, `/gamemode`), one
scripted goal, a 60 s limit, and a logged `SCENARIO <name> PASS|FAIL reason metrics` line, then exit. Scenarios:
`mine_no_cancel` (pickaxe, mine 5 stone; assert no cancels), `pillar_up` (64 blocks, go up 12), `edge_jump` (1-3 block
gaps), `corner_walk` (L-shaped corridor, diagonal corners), `water_cross` (swim 30 blocks with bank climb-out),
`underground_escape` (no pickaxe, enclosed, must reach open sky), `human_ab` (same route with `-Dvibrato.human=off`
vs on, compare "FAR AWAY FROM PATH" and time). Owner: the smoke-test agent (resume it), after the nav fixes land.

### Variation workstreams (V) - see docs/VARIATION.md

V1 macro scenarios + persona traits + needs/events layer; V2 technique parameters; V3 interaction sets A, B, C.

### Order
1. T3 finishes the feature set (needed by I2: it provides `village:found`).
2. I1, I2 first (correctness of the core loop), then I3 + I4, then I5 + I6 + I7.
3. V1, then V2/V3 in the order of docs/VARIATION.md.
4. Second in-game smoke test on full `master`, then a ten-minute soak run with the fleet profile.
