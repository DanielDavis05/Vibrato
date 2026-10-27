# Protocol 767 coverage

The catalog at `src/main/resources/vibrato/protocol-767.json` contains all 224 packet sections from the supplied Minecraft Wiki/wiki.vg Protocol page for Java Edition 1.21.1 (protocol 767), including the legacy server-list ping. The catalog records the source and CC BY-SA 3.0 attribution; the 223 modern packet IDs and Yarn classes were reconciled with the local 1.21.1 build.3 registrations. A packet is identified by connection phase, direction, and ID: common packets can share an identifier across phases.

Coverage is opt-in (`-Dvibrato.packetCoverage=true`). The observer records successful client-side packet handling and successful outbound channel writes, with scenario, root task, and director-step attribution. A successful write does **not** prove that the server accepted or acted on the packet. A catalog entry or declared fixture target alone does not count as an observation. A report row is covered only when an observation is attributed to a task; observations without a root task remain visible but do not satisfy the gate. Legacy ping and bundle delimiters use separate wire evidence because normal packet callbacks do not represent those wire forms. Unknown packet types and unmatched wire evidence remain in the report.

The fixture uses ordinary game activity and controlled native Minecraft packets on a dedicated local server. Some scenarios intentionally exercise negative cases: unknown message signatures, a player-session key that is not trusted by the server, login queries, and an unavailable resource pack. An unsigned player-chat scenario is distinct from a chat message authenticated with an account key. These cases do not demonstrate successful account authentication or server acceptance. Fixture commands can alter the test world, change game mode, kill the test player, disconnect, or transfer the connection. Normal autonomous play does not automatically run fixture scenarios.

The interactive runner requires both a loopback address and the dedicated fixture's server brand; ordinary singleplayer worlds are rejected. The fixture preserves inventory during its death/respawn test so later workstation and item scenarios retain their materials. Native sprint transitions and the ground-only movement packet also have explicit protocol scenarios; these diagnostics do not alter normal navigation controls.

## Run the fixture suite

Use Java 21, Python 3, and an available display for the Minecraft client, such as an already-running Xvfb display. The fixture binds to loopback ports 25576 and, for transfer cases, 25566. The runner creates or reuses disposable worlds under `run-protocol-fixture` and `run-protocol-transfer`, writes offline-mode server settings, and writes `eula=true`; run it only if you accept the Minecraft EULA. These directories persist between runs, so remove or replace them when a fresh world is needed.

```sh
python3 scripts/protocol-coverage.py --display :99
```

The script starts and stops the local servers and clients for its configured lifecycle modes. Captures go to `run/vibrato/captures/`, reports to `run/vibrato/reports/`, and logs to `run/vibrato/protocol-logs/`. Use `--modes normal` for the play suite alone, or for example `--modes normal --steps fixture:ui,client:chat` to run selected steps. See `--help` and the script's mode list for the available options. A scenario may finish without its target packets being observed; inspect the report and logs.

## Interactive commands and gate

On an isolated loopback fixture, launch the client with packet coverage enabled. `@protocol list` lists client scenarios; `@protocol run <scenario>` runs a client scenario, `@protocol fixture <scenario>` triggers a server fixture scenario, and `@protocol suite` runs the suite. `@protocol report`, `@protocol missing`, and `@protocol verify` write and inspect the current report; `@protocol reset` clears the current capture. The fixture's operator command `/vprotocol list` shows server scenarios and intended packet IDs. These commands are explicit diagnostics and are not enrolled in default autoplay.

To merge separate captures and enforce the complete inventory, pass comma-separated capture paths to the Gradle gate:

```sh
./gradlew packetCoverageReport \
  -PpacketCaptures=run/vibrato/captures/login_query.json,run/vibrato/captures/normal.json
```

Add every distinct capture needed for the run. The default merged report is `build/reports/protocol/coverage.json`; override it with `-PpacketReport=<path>`. The gate exits unsuccessfully if any of the 224 rows lacks task-attributed evidence or if unknown packet or wire evidence remains. It rejects duplicate or overlapping capture snapshots. A partial run is expected to leave rows uncovered; inspect the merged report rather than treating a completed fixture command as proof.
