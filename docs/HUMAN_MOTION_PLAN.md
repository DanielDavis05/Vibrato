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
