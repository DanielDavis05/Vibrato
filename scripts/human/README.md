# Human motion tooling (W4)

Python tools that turn recorded human play into `human_profile.json` (the `HumanProfile` traits in
`docs/HUMAN_MOTION_PLAN.md`), and check whether a bot's traces can be told apart from a human's. Standard library
only. Trace format: the JSON Lines schema in the plan.

| File | Purpose |
| --- | --- |
| `features.py` | Reads traces, splits them into windows, computes the per-window features. Shared by the other tools. |
| `fit.py` | Human traces to `human_profile.json` (mean and sigma per trait). |
| `discriminate.py` | Human windows vs bot windows: logistic regression, held-out accuracy, top features. |
| `synth.py` | Fake human and bot traces, for testing the pipeline without recordings. |
| `test_pipeline.sh` | Runs synth, fit and discriminate in a temp dir. Fails if accuracy is below 0.8. |

Every tool runs as `python3 -I -B scripts/human/<tool>.py`. The `-B` flag keeps `__pycache__` out of the repo.

## Workflow

1. Record. In game, `@record start <name> human`, play for a few minutes, `@record stop`. The trace is written to
   `run/traces/<name>-human.jsonl`. Do the same with `bot` for comparison traces.
2. Fit the profile from human traces:
   ```
   python3 -I -B scripts/human/fit.py run/traces/*-human.jsonl -o human_profile.json
   ```
   Each trait is estimated per 10 s window and reported as the mean over windows, with the sample standard
   deviation as sigma. A trait with no usable data is left out, and `Population` falls back to its default.
3. Check that the discriminator separates the two sources:
   ```
   python3 -I -B scripts/human/discriminate.py --human run/traces/*-human.jsonl --bot run/traces/*-bot.jsonl
   ```
   The split is by file (30% of each class held out). Needs at least 2 traces per class. Add
   `--min-accuracy 0.9` to make it exit 1 when accuracy is low.
4. Smoke test, with no recordings needed:
   ```
   bash scripts/human/test_pipeline.sh
   ```
   Generating synthetic traces by hand:
   ```
   python3 -I -B scripts/human/synth.py OUT_DIR --humans 8 --bots 8 --seconds 90 --seed 1
   ```
   Write `OUT_DIR` outside the repo.

## Notes and limits

* Several features are proxies, not direct measurements. `reaction_median_ticks` is the time from a path-node
  change to the next camera or key activity. `look_ahead_blocks` is the median distance to the next node while the
  camera is steady. Both are defined in `features.py`; change them there, not in the fit.
* The lateral features use the middle 70% of each segment. `lateral_sd` is an rms about zero, not a per-segment
  sd, because segments are about one correlation time long and per-segment statistics are strongly biased.
* Ledge and jump features are per window. A ledge whose jump falls in the next window counts as a miss, which
  inflates `jumpMissChance` slightly. Keep the window length as the default (10 s) unless there is a reason.
* Bot mouse deltas must be whole pixels, as the game receives them. If bot traces carry fractional `dx`, the
  `dx_nonint_frac` feature separates them trivially.
* On synthetic data the discriminator reaches near-perfect accuracy and its top features mostly reflect how
  `synth.py` is written. That run checks the pipeline, not the realism of the synthetic traces. Real recordings
  decide whether the parametric model holds (plan: the discriminator is the trigger for any neural approach).
