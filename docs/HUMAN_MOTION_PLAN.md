# Human motion plan

Branch `human-motion`. Goal: bots that move like a person. The camera (`HumanLook`) is already humanized; the
movement keys and path geometry are not (Baritone emits perfect keys along the planner's centerline).

Approach: record a little real human play, fit parameters, then add a human layer between Baritone's intent and the
real key presses. Parametric first. No neural net unless the discriminator (W4) says the parametric model fails.
Everything must stay small: pure Java, no new dependencies, profile data in the tens of KB.

Rules for all workstreams:
* Match the surrounding code style and comment density. Pure logic lives in plain classes with no Minecraft types so
  it is unit-testable (see `HumanLook`, `HumanInput`, `HumanLookTest`).
* Do not edit files outside your own list. Do not commit; the lead commits.
* Do not touch `../Baritone`.
* Unit tests use JUnit as in `src/test/java/dev/vibrato/human/`. Run with
  `./gradlew test --tests '<Class>' --offline --console=plain -q`.

## Trace format (shared by W1, W2, W4)

JSON Lines, one object per line, UTF-8. First line is a header, then events in time order.

```
{"t":"header","v":1,"source":"human"|"bot","mcVersion":"1.21.1","guiScale":2,"sensitivity":0.5,"fps":144,"note":""}
{"t":"mouse","ms":123456,"dx":3.0,"dy":-1.0}          raw mouse delta in pixels, frame rate, ms = monotonic millis
{"t":"key","ms":123460,"key":"forward","down":true}   key: forward back left right jump sprint sneak attack use
{"t":"tick","n":1042,"ms":123500,"x":0.0,"y":64.0,"z":0.0,"vx":0.0,"vy":0.0,"vz":0.0,
 "yaw":10.0,"pitch":5.0,"ground":true,"sprint":false,"nextX":1.5,"nextY":64.0,"nextZ":0.5}
```

`next*` is the next path node (Baritone) when pathing, otherwise absent. Bot traces are written by the same recorder;
for a bot, `mouse` events are the applied camera deltas converted to pixels.

## Workstreams (disjoint files)

### W1 Recorder core (pure) and W2 capture glue
W1 files: `src/main/java/dev/vibrato/human/record/TraceEvent.java` (sealed interface + records),
`TraceWriter.java` (buffered JSONL writer, one flush per second, closeable), `TraceReader.java` (parses back, for
tests and tools), `src/test/java/dev/vibrato/human/record/TraceRoundTripTest.java`.
W2 files: `src/main/java/dev/vibrato/human/record/MotionRecorder.java` (starts/stops a `TraceWriter`, samples ticks via
`ClientTickEvent`, key transitions by diffing `KeyBinding` state each tick), a mixin for raw mouse deltas at frame rate
(`src/main/java/dev/vibrato/mixins/MouseRecorderMixin.java` + register in the existing mixin json), and
`src/main/java/dev/vibrato/commands/RecordCommand.java` (`@record start <name> [human|bot]`, `@record stop`),
registered where the other commands are. Output goes to `run/traces/<name>-<source>.jsonl`. W2 depends on W1's
interfaces; W2 may start by reading W1's files once they exist. W2 only has to compile (`./gradlew compileJava
--offline`); it cannot be run in game here.

### W3 Pure models (the human layer)
Files under `src/main/java/dev/vibrato/human/motion/` and matching tests:
* `HumanProfile` record: per-bot sampled traits (see below) plus `HumanProfile.sample(RandomGenerator, Population)`.
  `Population` holds the mean and spread per trait, loaded from JSON (`/human_profile.json` resource, defaults baked in).
* `KeyShaper`: pure state machine. Each tick: input = Baritone's intended key set plus context (onGround, speed,
  distance to next gap/ledge, sprinting), output = key set to actually press. Behaviour: jump press early or late by
  a few ticks (clamped so it never causes a fall that Baritone did not plan, i.e. it only shifts within a tolerance
  window the caller provides), small miss chance, brief W release before gaps, strafe taps with log-normal hold times,
  occasional hesitation (all keys up 2-6 ticks) at low rate. Deterministic for a given seed.
* `PathOffset`: Ornstein-Uhlenbeck lateral offset perpendicular to travel, clamped to a corridor half-width supplied by
  the caller (0 when the caller says the corridor is 0, e.g. bridging or one-wide tunnels), plus `lookAhead(...)`
  returning an aim point several blocks along the path instead of the next node.
* `Submovements`: given an angular error and the profile, returns a list of (duration ticks, amplitude) steps: a
  ballistic primary move covering 90-95% of the distance, then 0-3 corrective moves, with overshoot probability scaled
  by amplitude. Used later by `HumanLook`; do not edit `HumanLook` in this workstream.
* `SensitivityGrid`: quantizes a yaw/pitch delta to the mouse-sensitivity grid
  (`step = (sens*0.6+0.2)^3 * 8 * 0.15` degrees per pixel in vanilla).
Traits in `HumanProfile`: reactionMedianTicks, jumpTimingBiasTicks, jumpTimingSigmaTicks, jumpMissChance,
strafeTapRate, keyHoldMedianTicks, keyHoldSigma, hesitationRate, lateralSigma, lateralReversion, lookAheadBlocks,
primaryFraction, correctionCount mean, overshootScale, turnPeakSpeedScale.

### W4 Fitting and discriminator (Python, stdlib plus numpy only if already available, else stdlib)
Files under `scripts/human/`: `features.py` (reads a trace, computes: turn peak speed vs amplitude, submovement count
per turn, overshoot ratio, key hold-time stats, jump timing relative to ledge, sprint-jump cadence, lateral deviation
from the line to the next node, mouse delta quantization), `fit.py` (trace(s) -> `human_profile.json` for `Population`),
`discriminate.py` (human traces vs bot traces -> logistic regression or similar tiny model, prints accuracy and top
features), `synth.py` (generates fake "human" and "bot" traces in the format above so everything is testable without
real data), `README.md` (how to record, fit, evaluate), and a smoke test `test_pipeline.sh`.

## Lead tasks (not delegated)
* Wire `KeyShaper`/`PathOffset` into the tick (after Baritone sets inputs), `Human.install` loads the profile.
* Move `HumanLook` to `Submovements` and `SensitivityGrid`; turn wind on.
* Run the discriminator on real recordings, iterate.
* Merge `master` into `human-motion` regularly; the other agent works on another machine.

## Integration (W5, W6) - delegated

Interface contract between W5 and W6 (both must follow it exactly):
* W6 adds to `HumanLook`: constructor `HumanLook(long seed, HumanLookConfig cfg, HumanProfile profile)` (the existing
  `HumanLook(long, HumanLookConfig)` stays and behaves as `HumanProfile.typical(Population.DEFAULT)`), and
  `void setSensitivitySource(java.util.function.DoubleSupplier mouseSensitivity)` (null = no quantization).
* W5 owns `Human.java` and is the only one who calls these. W6 never edits `Human.java`.

### W5 Key layer wiring (owns: new package `dev.vibrato.human.wire`, `Human.java`, one new mixin + its json entry, tests)
Goal: Baritone's movement keys go through `KeyShaper` before the player sees them.
1. Research first (report the findings in the class javadoc): how Baritone's `InputOverrideHandler` /
   `PlayerMovementInput` mixin feeds `ClientPlayerEntity` movement, and the cleanest Vibrato-side hook that runs AFTER
   it (candidate: a mixin on `net.minecraft.client.input.KeyboardInput#tick` at TAIL with a low priority number so it
   applies last; verify mixin ordering against Baritone's own mixin; do not patch `../Baritone`).
2. Pure, tested classes in `dev.vibrato.human.wire`: `KeyAdapter` (movement flags forward/back/left/right/jump/sprint/
   sneak <-> `Set<motion.Key>`), `PathView` (plain record: current movement kind, remaining path nodes as coordinates,
   player position/velocity/onGround) and `ContextBuilder.build(PathView) -> KeyShaper.Context` computing
   `gapAheadBlocks`, `ticksUntilJump`, tolerances. Conservative defaults: when unsure, `ticksUntilJump = NO_JUMP`,
   `gapAheadBlocks` = small if any drop or horizontal gap is within 3 nodes, tolerances early 0 / late 1 for ascend and
   traverse jumps and 0/0 for parkour (no timing shift for parkour). Never invent a jump.
3. Bypass shaping entirely (pass Baritone's keys through unchanged) when: player in water/lava/climbing/elevytra/
   riding, sneaking, Baritone is placing or breaking (attack/use pressed), `NavSafetyChain`, `MobDefenseChain` or
   `MLGBucketFallChain` is active, the player is not controlled by Baritone this tick, or the kill switch is set.
   Only movement keys and jump are shaped; sneak, attack, use, hotbar pass through always.
4. `Human.install` samples one `HumanProfile` per bot from the seed and `Population.load()`, logs it, creates the
   `KeyShaper`, builds `HumanLook` with the new constructor and `setSensitivitySource(() -> options mouse
   sensitivity)`. Kill switches: existing `-Dvibrato.human=off` disables everything; new `-Dvibrato.humanKeys=off`
   disables only this layer. `-Dvibrato.debugKeys=true` logs intended vs shaped keys when they differ (rate limited).
5. Do NOT implement lateral path offset (`PathOffset`) in this workstream. Leave a TODO in the javadoc noting it is
   the next step and why (it needs a Baritone aim-target hook).
6. Acceptance: `compileJava` and the new unit tests pass, existing tests still pass, no existing behaviour changes when
   the kill switches are off. Honestly state that nothing was run in game.

### W6 Camera model upgrade (owns: `HumanLook.java`, `HumanLookConfig.java`, `HumanLookTest.java`, `AimSimulation.java`)
Goal: camera turns look like a person: a ballistic primary move, then 0-3 corrections, overshoot at all amplitudes,
wind on, output quantized to the mouse sensitivity grid.
1. Keep the `RotationSmoother` contract: `smooth` is pure and may be called several times per tick; state advances
   once per tick in `onTick`. `fork()` keeps working with simulation copies.
2. When a new target appears (target moved by more than a small threshold from the last planned target) plan the turn
   with `motion.Submovements.plan(error, profile, rng)` per axis and follow it through the existing acceleration-limited
   axis model (bell-shaped speed per submovement). Replace the 60-degree overshoot rule, `OVERSHOOT_TURN_DEG` and the
   `overshootChance` / `overshootMaxDeg` config fields with the profile traits; keep `HumanLookConfig` speed caps.
3. Turn wind on at a small default strength; pick the value so that the extra jitter is under about 0.3 degrees RMS
   while tracking a stationary target, and prove it in a test.
4. Quantize each tick's applied yaw and pitch delta with `motion.SensitivityGrid` when a sensitivity source is set,
   carrying the sub-step remainder to the next tick so the average speed is unchanged.
5. Tests: existing `HumanLookTest` still passes or is updated with a reason; new tests for: 20 degree turns overshoot
   sometimes, deterministic per seed, ends within `arrivalToleranceDeg`, speed caps never exceeded, quantized deltas
   are multiples of the step, `fork()` does not consume the live random stream.
6. Do not edit `Human.java`. Report honestly what was only unit-tested.
