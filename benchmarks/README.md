# PostgreSQL baseline

Start PostgreSQL and the application with Redis disabled. Restart the application
after code changes. Keep its configuration, Docker resource limits, and other
machine workloads consistent throughout the experiment.

From the repository root:

```bash
python3 benchmarks/run-baseline.py
```

This runs a 20-second product warmup, then three 60-second repetitions at each
read rate (50, 100, 250, 500) and reservation rate (25, 50, 100, 200).
Allow roughly 25 minutes. Each reservation run creates a fresh active sale item
with enough stock, and uses a unique run ID. This creates real reservations in
the local database; it does not reset or delete existing data.

Results go into a unique `benchmarks/results/<timestamp>-<id>/` directory:

- `report.md`: throughput tables, inventory checks, and highest passing tested rates.
- `environment.json`: commit, working tree status, OS, k6 version, and settings.
- `runs.json`: collected metrics and inventory checks.
- Individual k6 JSON summaries and logs, including failed threshold runs.

Pass criteria are the existing script thresholds: business success >99%, HTTP
failure <1%, and zero dropped iterations, plus the inventory invariant for writes.
Latency percentiles include all HTTP responses. Successful RPS is achieved HTTP
RPS multiplied by the fraction of responses with the expected status (201 for
new reservations). Stock correctness is checked per fresh item using committed
reservation counts and quantities. Run without concurrent tests against these items.

Before calling a rate sustainable, define a latency SLO and perform a longer
confirmation run. A passing highest tested rate does not locate maximum capacity.
Read tests measure product ID 1; benchmark sale-item reads separately.

For a quick execution check rather than a publishable baseline:

```bash
python3 benchmarks/run-baseline.py --repeats 1 --duration 5
```

## Bottleneck evidence

Record CPU model, physical RAM, Docker CPU/memory limits, exact running Java and
Spring Boot versions, and Redis status in the generated report.

During each measured run, sample the following rather than inferring bottlenecks
from latency alone:

- Actuator `hikaricp.connections.active` and `hikaricp.connections.pending`:
  poll `/actuator/metrics/<metric>` and retain timestamps and maximum values.
- PostgreSQL `pg_stat_activity`: record `wait_event_type`, `wait_event`, and
  `pg_blocking_pids(pid)` for active client sessions to identify observed lock waits.
- PostgreSQL `pg_stat_statements`, if already enabled: compare before/after
  calls and total execution time to rank query cost for the run. Lifetime totals
  alone do not identify the most expensive query in this benchmark.
- JVM: capture Java Flight Recorder or profiler data during measured runs for
  CPU and GC observations. Record the collection method and overhead.

Keep uncollected fields marked unmeasured. Run with the same telemetry enabled
when comparing the PostgreSQL baseline against Redis experiments.
