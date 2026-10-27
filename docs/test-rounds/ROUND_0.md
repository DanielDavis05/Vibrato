# Round 0: isolated campaign capacity

Run date: 2026-10-27 UTC. This is a short infrastructure and capacity check, not the seeded stage matrix or a gameplay pass-rate measurement.

The measured runtime was built from commit `e1cc727834278a8e30b54348fafc12c608d1f8a4`, whose gameplay code precedes the later integrated food and fluid fixes. Its packaged mod SHA-256 was `eb2febff2abd0dcb1b24a13400e28cfc1691a0c2439c996103e593e227956751`; each run recorded the same hash at launch and verified it unchanged at exit. Results do not validate the later integrated gameplay build. The baseline build passed 611 Java tests; eight Python harness tests passed.

Each client used a separate game directory and fresh seeded singleplayer world, with a low-resource profile, low process priority and a dedicated virtual display. The machine reported 96 logical CPUs and 121 GiB RAM, differing from the older 24-CPU, 62-GB planning estimate. Client CPU figures below include startup, world generation and software rendering; they do not include the virtual display process.

A one-client, 25-second smoke run confirmed that the packaged Fabric client entered a fresh world, ran the game-completion command, and emitted client and integrated-server tick samples. It timed out in the Overworld, with no observed death.

The capacity probes started four and six clients with no deliberate stagger and gave each a 60-second in-world budget. An independent half-second observer counted a client as active only while its process existed and its heartbeat was less than three seconds old. The longest continuous intervals during which every client met that condition were:

| Clients | First all-active observation, UTC | Last all-active observation, UTC | Continuous observed overlap | Observations |
|---:|---|---|---:|---:|
| 4 | 20:48:43.505 | 20:49:38.683 | 55.177 s | 111 |
| 6 | 20:50:48.473 | 20:51:39.215 | 50.742 s | 102 |

These are sampled bounds, not exact join or exit times. The observer's raw measurements and client logs are retained locally and are not included in the repository.

| Clients | Seeds | Outcomes | Mean client tick rate per run | Mean integrated-server tick rate per run | Peak sampled client RSS | Average client process CPU |
|---:|---|---|---|---|---|---|
| 4 | 424242, 1, 7, 19 | 4 timeouts; 0 observed deaths | 20.16–20.18 ticks/s | 19.99–20.01 ticks/s | 2.07–2.17 GiB | 786–865% of one core |
| 6 | 424242, 1, 7, 19, 42, 12345 | 6 timeouts; 0 observed deaths | 20.06–20.18 ticks/s | 19.99–20.08 ticks/s | 2.11–2.17 GiB | 719–853% of one core |

Each steady run had 56–58 client cadence samples and 55–57 server cadence samples. None was marked timing-invalid by the supervisor's ten-sample, below-18-ticks/s rule. The values in the table are per-run means across sampled in-world intervals, not rates calculated exclusively within the simultaneous overlap. RSS is the highest sampled resident memory of each client process; aggregate memory and the virtual display process were not measured here.

Every steady run exhausted the deliberately short budget before reaching the Nether and was classified as a timeout. The observations establish that the isolated launcher and telemetry ran at these concurrency levels for the recorded intervals; they do not establish 15-minute performance, stability in a longer soak, or any stage or completion pass rate. The fixed seed list has not yet been surveyed to confirm terrain categories. Churn detection is conservative and can miss rapid or ambiguous leaf transitions; restart counts represent repeated observed task descriptions and may include parent tasks. Redacted excerpts require manual review before sharing.
