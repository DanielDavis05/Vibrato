# External real-client testing tool specification

Status: specification only. No campaign has been launched under this specification. This document replaces the proposed implementation inside Vibrato; it does not claim new gameplay validation.

## Purpose and boundary

Build a **separate testing tool**, in its own repository and installation, that runs Vibrato in real Minecraft clients and worlds. It must test whether each feature achieves its intended outcome, find navigation and task-progress failures, and produce reproducible evidence within a bounded campaign.

Vibrato remains the product being tested. The test scheduler, fixture generation, assertions, watchdogs, resource management, test-only commands, and reporting must not be bundled into Vibrato, its normal chat interface, or its production jar. They must not become build or runtime dependencies of the product. Do not change gameplay logic to make a test pass or add campaign-specific branches to production tasks.

Existing development launchers and historical reports are references, not the architecture for the new tool. If reused, move or adapt their testing responsibilities in the external project. This specification does not remove earlier development tools or invalidate their historical results.

The external project has three parts:

1. An orchestrator that creates isolated runs, schedules clients, records resources, and manages only processes it started.
2. An optional **separately packaged test-driver Fabric mod**, installed only in test clients. It prepares owned fixtures, invokes existing commands or activity APIs, and observes game state. It is never nested in the Vibrato jar or installed in normal player profiles.
3. A versioned case catalog, evidence store, analyzer, and report generator owned by the external project.

Prefer existing public commands, activity factories, task events, and normal Minecraft observations. If an activity needs direct invocation, the test-driver mod can adapt the existing API externally. Test its normal command or Director integration separately. Where observation is impossible without a new product interface, record the gap and propose a small general-purpose interface separately; do not quietly embed a testing framework.

## Coverage contract

The initial static inventory contains **146 activity IDs and 25 registered commands**, recorded in [the coverage catalog](testing/feature-coverage-spec.json). Each proposed goal needs review against the actual feature contract. The tool must discover the runtime registry and command registrations, compare them with the catalog, and report additions, removals, and unmapped features. A generated structure or advancement activity remains a coverage row; parameter variants do not inflate the feature count.

| Family | Initial activity IDs | Required coverage |
|---|---:|---|
| Core | 46 | Gathering, crafting, smelting, equipment, inventory, ordinary travel and resource prerequisites |
| Trade and enchantment | 11 | Real villager trades, enchanting, anvil rename/combine, grindstone and brewing |
| Life | 19 | Crops, food, cooking, breeding, shearing, milk, fishing, beds, sleep and XP |
| Travel | 22 | Exploration, vehicles, structures, dimension travel and return trips |
| Build | 15 | Placed structures, palettes, signs, storage and inventory organization |
| Social | 21 | Local messages, gestures, controlled peers and advancement pursuit |
| Combat | 12 | Actual target combat, drops, weapons and gear progression |

Also test cross-cutting behavior that has no registry ID: path following, tools, target selection, resource handoffs, interruptions, water safety, jumps, pillars, task switching, failure recovery, stop/resume, and command completion.

Every feature must have a row with a case, an independently observable goal, prerequisites, a bounded budget, and an outcome. Missing fixtures, unavailable peers, or unsupported features are explicit coverage gaps, never passes. Representative variants must include meaningful boundary cases: zero/some/sufficient materials, an existing partial inventory, protected items, hotbar versus main inventory, obstructed targets, and interrupted work.

## Real worlds and fixture integrity

Use real Minecraft clients with an integrated server, or controlled local multiplayer servers where peers are necessary. Unit tests and simulated task ticks supplement these runs; they cannot replace gameplay evidence.

Support two distinct modes:

- **Fresh survival:** fixed seed, normal difficulty, cheats off, empty inventory, ordinary death and hunger. Supply no items or teleports during the objective.
- **Isolated feature fixture:** a disposable world or copied template with documented terrain, entities, inventory and prerequisites. Setup may use commands or creative mode. Switch to survival and verify all preconditions before starting the feature. Tests that need natural terrain, structures, dimensions, time, or weather must specify those conditions.

Never load an original player save. Store all worlds under an external owned-run root. Templates must be copied per run, never modified in place. Reject paths outside that root and refuse to overwrite an existing run.

Fixture setup must finish before measurement:

- Load the intended chunks and wait for their server-side availability before filling blocks or spawning entities.
- Establish safe footing before switching to survival; an accidental setup fall is an invalid fixture.
- Verify initial position, dimension, mode, health, hunger, inventory, entities and relevant block state.
- Confirm the goal is initially unmet. A pre-held output must not masquerade as successful crafting or collection.
- Exclude unrelated nearby drops, entities or environmental effects when they could satisfy the goal. Keep intentional obstacles and hazards.
- Record every setup action and server rejection. Unknown commands, malformed NBT, missing chunks, or failed preconditions invalidate the fixture.

No post-start setup action may supply the requested output, move the player to the goal, kill the target, or award the advancement. Deliberate fault injection, such as a controlled fall or interruption, must be declared, timestamped, and distinguished from assistance. It cannot replace an ordinary navigation test.

Multiplayer cases use owned synthetic peers in an isolated local environment. Verify the peer actually receives or performs the intended action. Never send test chat or commands to public servers or real players.

## Case contract

Each case defines the following fields; this is a specification, not an implemented schema:

| Field | Meaning |
|---|---|
| Identity | Stable case ID, covered feature IDs, parameter variants, catalog version |
| Environment | Minecraft/Fabric versions, world/profile seeds, world mode, required peers and dimension |
| Preconditions | Exact setup, negative goal check, relevant inventory/equipment and expected world state |
| Action | Existing command, Director integration, or direct activity invocation; record which interface is exercised |
| Goal | Independent state predicates and required changes from the pre-start snapshot |
| Completion | Required task outcome, milestone, or deliberately persistent behavior |
| Timing | Setup limit, active objective limit, no-progress limit, observation period and campaign priority |
| Constraints | Allowed interruptions, declared fault injection, forbidden assistance, protected items/terrain |
| Evidence | State samples, events, assertions, clips, resource measurements and terminal reason |

For example, a food case starts with one raw mutton and suitable cooking prerequisites, requests 24 food units, and requires additional meat acquisition followed by at least four cooked mutton. An already-satisfied one-item subtask or an idle client does not meet that goal.

Keep the catalog declarative. Fixtures, goals and budgets must be reviewable without reading scheduler implementation. Do not encode case-specific success rules in Vibrato.

## Independent success checks

A case passes only when setup is valid, the intended action actually ran, and the declared goal is proven without disallowed intervention, death, crash, timeout or unresolved failure.

Task completion and idle state are supporting evidence. They are insufficient on their own: a task can finish through a fallback, unsupported path or incorrect predicate. Conversely, a persistent task can reach a valid stage milestone before its overall task ends.

| Feature | Required proof examples |
|---|---|
| Collection/crafting/smelting | Requested item and quantity; compare initial, acquired, consumed and final counts |
| Equipment | Correct item in the actual equipment slot, not merely held in inventory |
| Storage | Correct contents in the selected container, corresponding inventory change and no unintended loss |
| Building | Required block states, positions, orientation and dimensions in the server world |
| Travel | Intended dimension and destination tolerance, settled/grounded state where required, survival and correct route constraints |
| Farming/breeding | Actual harvest or newly bred offspring, not pre-existing produce or animals |
| Combat | Intended target was alive initially, bot-caused combat progress, target outcome and required drops |
| Trade/enchant/brew | Actual exchange, enchantment, custom name, repair/disenchantment or potion contents and resource consumption |
| Advancement | Relevant advancement initially absent, then actually awarded; acquiring a prerequisite item alone is insufficient |
| Social/commands | Observable response or intended peer effect through the declared interface |
| Stop/control | Work stops, queued continuations do not fire, and unwanted autonomous restart stays suppressed for the observation period |

Check counts, not just item presence. A shield is not proof of deflecting an arrow. Sunlight, despawning or a setup command must not be credited as a combat kill. Cursor-held outputs and container slots must be distinguished from usable inventory. Track death cumulatively across respawn and player-object replacement; restored health cannot erase a death.

A Nether-entry stage of a game-completion command may be recorded as `pass:milestone` with the actual dimension and active command/task identity. It is never a pass for completing the whole game.

## Required navigation and task-progress cases

Each row below needs a standalone fixture plus integration coverage where relevant.

| Case | Goal and failure evidence |
|---|---|
| Flat and diagonal travel | Reach exact targets; measure overshoot, unnecessary reversal and repeated target visits |
| Corners and obstacles | Route around offset walls; preserve legitimate strafing, jumping and headroom |
| Narrow bridge/ledge | Reach the far side alive; no safety adjustment that breaks precise movement |
| Pillar and stairs | Build a multi-block ascent, settle at the requested height, then complete a return trip |
| Vertical mining | Reach and mine a known vein below the player; identify wrong-block breaking and repeated jump/dig cycles |
| Exposed versus buried resources | Select and collect valid targets, use the required tool and retain safe footing |
| Surface and underground logs | Acquire logs with appropriate tools; escape or change an unreachable approach without re-entering the same trunk loop |
| Water crossing and exit | Cross, swim and leave the water with adequate air; cover deep water, a wall, bobbing and dry-bank selection |
| Drops and inventory handoff | Collect reachable alternatives when one drop is unreachable; finish the requested quantity rather than merely abandoning targets |
| Cast materials | Complete casts with no, partial and sufficient scaffold material; preserve protected blocks and acquire only the shortfall |
| Fall safety | Controlled dry-floor bucket clutch and itemless existing-water landing; distinguish attempted input from observed placement and survival |
| Target changes and interruption | Respect a changed destination, resume a valid objective after combat/food/MLG interruptions, avoid stale targets |
| Long prerequisites | Gather/craft/smelt in sequence, carry output into the next step, and complete the actual request |
| Fresh survival objectives | Short travel/resources and longer Nether/game milestones across multiple natural-world seeds |

Do not prescribe a dangerous movement solely to match a script. A safe alternative route that achieves the declared goal can be valid. If exact shaft excavation is required, define its allowed volume and safety constraints explicitly.

A loop detector must distinguish productive repeated actions from repeated failure. Path recomputation, changed debug text, arbitrary item shuffling, walking a circuit, or breaking unrelated blocks are not proof of objective progress. Record best approach to the target, relevant inventory gains, target damage, useful excavation and verified placement. Preserve time for legitimate mining, cooking, crafting, airborne actions and protected handoffs.

Record stationary recalculation even when the current task never restarts. Detect repeated class/objective starts separately. Flag pass-by-and-return patterns for review, accounting for deliberate return trips and changed commands. A single necessary backtrack is not a failure by itself.

## Observation and evidence

The external observer should sample at least once per second and capture event details at the game's tick rate when needed. Record:

- Immutable run identity: product source revision, exact jar hash, embedded engine hash, test-tool/driver revisions, mod hashes and effective configuration.
- World/profile seeds, case version, setup mode, parameters, task start and active objective identity.
- Position, velocity, aim, effective input, grounded/water/air state, health, hunger and cumulative deaths.
- Inventory by item, cursor/container/equipment state, durability and XP where relevant.
- Task transitions, completion/cancellation/failure, pathing/calculation state, breaking target/progress and actual block/entity changes.
- Both client and integrated-server tick cadence, per-process CPU/RSS, aggregate resource pressure and active-client overlap.
- Terminal assertion results, first causal error, last task history and a bounded trace window around failure.

Capture short video or screenshots for selected movement failures and representative successes. Use them to assess rigid turns, unnecessary spins and task-switching clunkiness alongside functional outcomes. Do not claim human-like movement from destination success alone.

A test-driver completion marker must be authenticated by actual observed state. Matching text in a script, command dispatch, or echoed setup command is not assertion evidence. Missing telemetry stays unknown and cannot turn into a pass.

## Parallel execution and efficiency

The host last measured on 2026-10-27 had 96 logical CPUs and 121 GiB RAM. Historical four- and six-client measurements are in [round zero](test-rounds/ROUND_0.md); these are short capacity observations, not guarantees for heavier or longer cases.

Start with four clients, then measure eight and higher levels while the workload permits. Choose concurrency from measured CPU, memory and tick cadence rather than memory alone. Reserve resources for the user's session and the operating system. Reduce worker count and replay timing-invalid cases if either client or server cadence drops below 18 ticks/s over a sustained ten-second window.

Use a low-resource baseline, separate game/natives/config/log/save directories, low process priority, and immutable per-run mod snapshots. Do not rebuild or replace the measured jar during a batch. Shared read-only downloads are acceptable; writable saves, native extraction and configuration must be isolated.

Queue short cases alongside a limited number of long objectives so one slow prerequisite chain does not occupy every worker. Refill slots as runs finish. Reuse validated copied templates and cached downloads to reduce startup cost. Lower rendering cost only after verifying that the chosen profile preserves movement behavior; do not optimize by skipping game ticks or weakening assertions.

Measure genuine simultaneous in-world activity, not overlapping process startup. Capacity probes need at least a minute of observation with a useful all-clients-active window. Preserve raw tick samples and mark missing or insufficient measurements invalid.

Only terminate owned process groups. On cancellation or deadline, save available evidence, stop owned clients and any owned display/server, and leave other Java processes and player sessions untouched.

## Budgets and classification

Default initial campaign budget: **90 minutes wall time**, with a hard maximum of two hours for a deliberately extended campaign. Do not silently turn an initial sweep into days of testing. Per-case limits remain separate from the overall campaign limit.

| Case type | Initial active budget |
|---|---:|
| Simple command, crafting, inventory or short travel | 30–120 s |
| Mining, building, storage, water/obstacle routes | 120–240 s |
| Fishing, breeding or other inherently delayed work | Explicitly justified 5–10 min |
| Fresh survival Nether/game milestone | Up to 15 min |

Allow separate bounded setup/startup and telemetry warmup. A very short valid action needs a short follow-up observation so timing measurements exist. Stop launching work that cannot reasonably finish within the remaining campaign budget. Report unstarted or interrupted-by-campaign-deadline cases explicitly.

Terminal outcomes must distinguish:

- `pass` and `pass:milestone`, with proof and scope;
- runtime failure: goal not reached, death, crash, task failure, timeout, stall, repeated churn or wrong resulting state;
- invalid run: failed setup, malformed case, missing observation, unsuitable environment or demonstrated overload;
- not run, including unavailable prerequisites and exhausted campaign budget.

Keep secondary flags: a crash or death must not disappear behind a generic overload label. Setup failure is not a gameplay failure; failure after validated setup is not automatically excused as a fixture problem. Never count an incomplete or invalid run as a pass.

## Rounds, fixes and reporting

1. Reconcile the full feature inventory and validate fixture preconditions.
2. Freeze a product/test-tool revision and jar snapshot; build the product with its ordinary checks.
3. Run the broad timeboxed baseline, including short and long objectives.
4. Group failures by cause, retaining seed, prerequisites, trace and objective proof. Distinguish product defects, fixture errors, hardware limits and unknowns.
5. Make product fixes in ordinary isolated worktrees; keep test code in the external project. Protect sensitive movement, jump, pillar and inventory behavior.
6. Replay the same failing case, then related regression cases and relevant fresh-world controls. A loop fix must either achieve the objective or produce the declared clear failure; merely cycling through new abandonment strategies is insufficient.
7. Publish a coverage table with counts, timings, failures, invalid runs and not-run rows. Include reproducible run recipes and redacted causal excerpts; retain raw logs outside the product repository.

For disputed movement failures, repeat the same scenario with human motion disabled and with navigation safety disabled, one control at a time. Controls diagnose the cause; success under a control does not certify the default configuration.

The longer reliability targets in [the earlier campaign plan](GAMER_TEST_PLAN.md) remain later acceptance goals: at least 95% over 20 valid stage runs and 90% Nether entry over 20 fresh-survival runs. Do not claim those thresholds from a single sweep or short capacity probes. Report fractions such as 17/20 and preserve invalid-run counts.

## Deliverables and implementation order

The external implementation is ready for a first broad campaign when it provides:

1. A separate repository, pinned client/driver environment and safe owned-process lifecycle.
2. One feature case that completes end to end with independently proven state, plus negative cases that correctly fail classification.
3. Runtime inventory reconciliation, a declarative case schema and reusable fixture/assertion primitives.
4. Validated parallel scheduling and measured resource limits.
5. A reviewed case for every catalog row, or a specific recorded gap; no placeholder goals presented as coverage.
6. A timeboxed baseline report, minimal reproductions, and before/after replays of fixes.

The specification and coverage catalog are the only new deliverables in Vibrato. Implementation, generated fixtures, campaign manifests, worlds, telemetry databases, raw logs and test-driver binaries belong to the separate testing project.
