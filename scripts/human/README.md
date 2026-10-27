# Human motion tooling (W4)

Python tools that turn recorded human play into `human_profile.json` (the `HumanProfile` traits in
`docs/HUMAN_MOTION_PLAN.md`), and check whether a bot's traces can be told apart from a human's. Standard library
only. Trace format: the JSON Lines schema in the plan.

| File | Purpose |
| --- | --- |
| `features.py` | Reads traces, splits them into windows, computes the per-window features. Shared by the other tools. |
| `fit.py` | Human traces to `human_profile.json` (mean and sigma per trait). Uses the mouse channel. |
| `discriminate.py` | Human windows vs bot windows on the tick channel: logistic regression, held-out accuracy, top features, usable window counts. |
| `synth.py` | Fake human and bot traces, for testing the pipeline without recordings. |
| `test_pipeline.sh` | Runs synth, fit and discriminate in a temp dir. Checks the tick clock (tick features do not change with the sampler jitter). Fails if accuracy is below 0.8. |

Every tool runs as `python3 -I -B scripts/human/<tool>.py`. The `-B` flag keeps `__pycache__` out of the repo.

## Workflow

1. Record. In game, `@record start <name> human`, play for a few minutes, `@record stop`. The trace is written to
   `run/traces/<name>-human.jsonl`. Do the same with `bot` for comparison traces.
2. Fit the profile from human traces:
   ```
   python3 -I -B scripts/human/fit.py run/traces/*-human.jsonl -o human_profile.json
   ```
   Each trait is estimated per 10 s window (200 ticks on the tick clock, see below) and reported as the mean over windows, with the sample standard
   deviation as sigma. A trait with no usable data is left out, and `Population` falls back to its default.
3. Check that the discriminator separates the two sources:
   ```
   python3 -I -B scripts/human/discriminate.py --human run/traces/*-human.jsonl --bot run/traces/*-bot.jsonl
   ```
   The split is by file (30% of each class held out). A class with one trace is split by time within that trace
   instead, and the report says so, because that accuracy is optimistic. Windows with under 3 s of movement are
   dropped, and the report prints the dropped counts and the usable window count per class. Below 20 usable windows
   in a class, it warns loudly. Add `--min-accuracy 0.9` to make it exit 1 when accuracy is low.
4. Smoke test, with no recordings needed:
   ```
   bash scripts/human/test_pipeline.sh
   ```
   Generating synthetic traces by hand:
   ```
   python3 -I -B scripts/human/synth.py OUT_DIR --humans 8 --bots 8 --seconds 90 --seed 1
   ```
   Write `OUT_DIR` outside the repo.

## Trace fields the tools read

* `mouse`: deltas are cleaned on read (`features.clean_mouse`). A delta over 200 px in either axis is dropped
  (cursor recentre or screen switch). After a silence of more than 500 ms with no mouse events, deltas over 50 px in
  the first 100 ms are dropped (a huge first sample after a gap).
* `ikey` (optional in version 1): one bot input snapshot per input tick. `n` is the next sampled tick, and
  `intended` and `shaped` are masks with bits 0 through 6 for forward, back, left, right, jump, sprint and sneak.
  `ms` is the monotonic capture timestamp. Missing snapshots mean attribution is unavailable, not that all keys
  were up. Existing traces without this event remain valid.
* The discriminator prints input attribution separately from its classifier: forward releases and sprint starts for
  intended, shaped and effective inputs, plus stationary path ticks where forward was intended, and the subset where
  shaped/effective forward was still held. Effective sprint is sampled player sprint state; intended and shaped
  sprint are requested inputs. Transitions across missing snapshots are excluded. These diagnostic fields never
  enter the classifier, since intent is not available for human or older traces.
* `tick.n`: the tick count. The tick channel times everything from it (see Tick clock). Its `ms` is the sampler's
  timestamp and is bursty.
* `tick.yaw`, `tick.vx/vz`, `tick.ground`: what a server sees. The tick channel uses only these.
* `tick.edge` (optional, blocks): distance along the movement heading to the first gap or drop within 6 blocks. Absent
  when there is none. Edge features use only edge ticks: onGround, horizontal speed above 0.05 blocks per tick, and an
  edge present. When any tick in a window has an edge, jump timing and jump misses are computed from edges, not path
  nodes.
* `tick.nextX/nextY/nextZ` (optional): the next Baritone path node. Human traces normally have none. Only the mouse
  channel reads them.

### Channels

`features.window_features(..., channel=...)` has two channels:

* `tick` (default, used by `discriminate.py`): only what a server sees. Turn and camera features come from per-tick yaw
  deltas, the same signal the server gets. Mouse events and path nodes are ignored. Features that need either are NaN
  (`mouse_rate_hz`, `dx_nonint_frac`, `reaction_median_ticks`, `hesitation_per_min`, `look_ahead_blocks`).
* `mouse` (used by `fit.py`): the physical mouse deltas for turns and reaction, and path nodes where present. This
  is the richer data, valid for the human-only fit.

Why discriminate does not use the mouse channel: the bot's camera is driven by code, so its recording has almost no
mouse events. Any feature that reads mouse events separates the two classes by the recording, not by behaviour.
`discriminate.py` refuses mouse-only features, and `test_pipeline.sh` checks that removing every mouse event from a
trace leaves its tick-channel features unchanged.

### Tick clock (why tick features do not use `ms`)

The game advances exactly one tick, 50 ms, per tick event, and the tick event's `n` counts those ticks. Its `ms` is
the wall-clock time the client sampler took the sample. The sampler runs when the render thread catches up, so tick
`ms` gaps are bursty: in `walk1-human.jsonl` the median gap is 34 ms and the mean is exactly 50 ms. Converting those gaps
to time would make every tick-channel feature depend on sampler bursts, not on the game.

So the tick channel runs on the tick clock:

* `read_trace()` sets `tk` on every event (`features.tick_clock`). A tick or ikey snapshot has `tk = n * 50` ms exactly.
* A key or mouse event has only `ms`. Its `tk` is its fractional tick position, by linear interpolation between the
  neighbouring tick events' `(ms, n)` pairs. Outside the first and last tick it extrapolates at 50 ms per tick.
* Windows are sliced on `tk`. A 10 s window is 200 ticks (`features.window_ticks`).
* Hold times, jump press timing and strafe taps are measured on `tk`, then in ticks. Turn timing, the yaw burst gaps
  and the submove merge use tick counts (`TICK_BURST_GAP_TICKS`, `TICK_MERGE_TICKS`).
* Mouse deltas keep `ms`. The mouse channel's turn and burst timing are real input timing between mouse events, and
  `clean_mouse` works on them too. The mouse channel's reaction time uses the tick clock, because it measures from a
  tick to a key or mouse event.

Key rates are still per window second. A key's tick position inherits the sampler's bursts through the interpolation.
Its error is at most 0.5 ms per end of the bracket, which is 0.5 ticks at a 1 ms sampler gap. A 20 ms gap gives 0.025
ticks per end, so the uncertainty is small only when the sampler is not bunched up.

`synth.py` reproduces this: tick `ms` is game time plus a random sampler jitter (`apply_sampler_clock`), kept strictly
increasing with at least 20 ms between samples. Key and mouse `ms` go through the same clock. `--clock-seed` changes only
that jitter; `test_pipeline.sh` regenerates a trace with another clock seed and checks that tick-channel features agree
within the rounding bound (0.05 ticks, and one hold's share for `short_hold_frac`).

## Trait mapping for human traces

| Trait | Source on human traces without a path | Status |
| --- | --- | --- |
| `jumpTimingBiasTicks` | PROXY: mean of (ticks-to-edge minus a reference), ticks-to-edge = `edge` / horizontal speed at the nearest preceding grounded tick, for each jump press | needs `edge` |
| `jumpTimingSigmaTicks` | PROXY: sd of the same values | needs `edge` |
| `jumpMissChance` | PROXY: share of edge approaches (edge under 1.5 blocks, on an edge tick: grounded, moving) with a drop over 1 block within 20 ticks and no jump press in that span | needs `edge` |
| `lateralSigma` | PROXY: rms of the heading error times horizontal speed, in blocks per tick | pathless: heading wander |
| `lateralReversion` | PROXY: 1 minus the lag-1 autocorrelation of the heading error, per tick (discrete OU) | pathless: heading wander |
| `reactionMedianTicks` | none | stays no data (needs path nodes) |
| `lookAheadBlocks` | none | stays no data (needs path nodes) |

Jump timing reference: a sprint jump's best case is not known without a path, so the reference is the median of all
ticks-to-edge values in the human traces (`fit.py` prints it). The bias is then the mean offset from that median, and
the sigma is the sd of the offsets. This centres the bias near zero by construction. Read it as spread, not a bias.

Lateral units match `HumanProfile` (per tick, blocks). Heading wander: each moving tick gets a heading error. On the mouse
channel a moving tick has forward held and speed over 0.1 blocks per tick. On the tick channel it has speed over 0.05
blocks per tick, with no forward gate, because the bot's recording has no forward key events, the angle between its velocity and a centred 1 s moving average (10 ticks
each side, excluding the tick). The rms of that error times the speed gives `lateralSigma`. The reversion is from the
lag-1 autocorrelation of the errors, `k = 1 - rho`. Path-node traces use the same units now: the rms offset is
converted to a per-tick sigma as `rms * sqrt(2k - k^2)`. Earlier fits wrote a per-second rate there, which did not
match `HumanProfile`.

## Notes and limits

* Several features are proxies, not direct measurements. `reaction_median_ticks` is the time from a path-node
  change to the next camera or key activity. `look_ahead_blocks` is the median distance to the next node while the
  camera is steady. Both are defined in `features.py`; change them there, not in the fit.
* The lateral features use the middle 70% of each segment. `lateral_sd` is an rms about zero, not a per-segment
  sd, because segments are about one correlation time long and per-segment statistics are strongly biased.
* Ledge and jump features are per window. A ledge whose jump falls in the next window counts as a miss, which
  inflates `jumpMissChance` slightly. Keep the window length as the default (10 s, 200 ticks) unless there is a reason.
* Bot mouse deltas must be whole pixels, as the game receives them. Fractional `dx` would separate them trivially,
  which is why `dx_nonint_frac` is mouse-channel only and `discriminate.py` refuses it.
* `synth.py` puts `edge` on both classes' ticks, as the real recorder does. Otherwise edge presence alone separates
  them.
* The real bot trace has no `forward` or `sprint` key events, and it moves on little of its recording. Its turn and
  edge features come from the tick channel, but its key features may be empty for that reason. Read a discriminator
  result on that trace with care.
* On synthetic data the discriminator reaches near-perfect accuracy and its top features mostly reflect how
  `synth.py` is written. That run checks the pipeline, not the realism of the synthetic traces. Real recordings
  decide whether the parametric model holds (plan: the discriminator is the trigger for any neural approach).
