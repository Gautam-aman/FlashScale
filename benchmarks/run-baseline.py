#!/usr/bin/env python3
"""Run from any directory; requires a running app, Docker Compose database, and k6."""
import argparse
import datetime
import json
from pathlib import Path
import platform
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def command(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, text=True, check=True, **kwargs)


def sql(statement=None, stock=None):
    args = ['docker', 'compose', 'exec', '-T', 'db', 'psql', '-qAt',
            '-U', 'flashscale', '-d', 'flashscale', '-v', 'ON_ERROR_STOP=1']
    if stock is not None:
        args += ['-v', f'stock={stock}']
        statement = (ROOT / 'benchmarks/create-test-item.sql').read_text()
    return command(args, input=statement, capture_output=True).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8080')
    parser.add_argument('--duration', type=int, default=60, help='seconds per measured run')
    parser.add_argument('--vus', type=int, default=100)
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--label', choices=['postgres', 'redis'], default='postgres')
    parser.add_argument('--compare-with', type=Path, help='saved PostgreSQL result directory')
    args = parser.parse_args()
    if args.compare_with:
        baseline_environment = json.loads((args.compare_with / 'environment.json').read_text())
        for argument, field in [('duration', 'duration_seconds'), ('vus', 'vus'), ('repeats', 'repeats')]:
            if getattr(args, argument) != baseline_environment[field]:
                parser.error(f'{argument} must match saved baseline ({baseline_environment[field]})')
    if min(args.duration, args.vus, args.repeats) <= 0:
        parser.error('duration, vus, and repeats must be positive')
    run_id = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6]
    output = ROOT / 'benchmarks/results' / run_id
    output.mkdir(parents=True)
    metadata = {
        'timestamp': run_id, 'label': args.label, 'platform': platform.platform(),
        'processor': platform.processor(), 'base_url': args.base_url,
        'duration_seconds': args.duration, 'vus': args.vus, 'repeats': args.repeats,
        'git_commit': command(['git', 'rev-parse', 'HEAD'], capture_output=True).stdout.strip(),
        'git_status': command(['git', 'status', '--short'], capture_output=True).stdout,
        'k6': command(['k6', 'version'], capture_output=True).stdout.strip(),
    }
    (output / 'environment.json').write_text(json.dumps(metadata, indent=2))

    def load(name, mode, rate, duration, extra=()):
        summary = output / f'{name}.json'
        cmd = ['k6', 'run', '-e', f'BASE_URL={args.base_url}', '-e', f'MODE={mode}',
               '-e', f'RATE={rate}', '-e', f'VUS={args.vus}', '-e', f'DURATION={duration}s',
               *extra, '--summary-export', str(summary), 'load-tests/baseline.js']
        print(f'Running {name}', flush=True)
        with (output / f'{name}.log').open('w') as log:
            result = subprocess.run(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode not in (0, 99) or not summary.exists():
            raise RuntimeError(f'{name} failed to execute; see {output / (name + ".log")}')
        return json.loads(summary.read_text())['metrics'], result.returncode

    # Warm up the read path without mixing its measurements into the report.
    warmup, code = load('warmup', 'product', 20, 20)
    if code:
        raise RuntimeError('Warmup failed; fix the application before benchmarking')
    records = []
    for mode, rates in [('product', [50, 100, 250, 500]), ('reserve', [25, 50, 100, 200])]:
        for rate in rates:
            for repeat in range(1, args.repeats + 1):
                name = f'{mode}-{rate}-r{repeat}'
                extra = []
                item = None
                if mode == 'reserve':
                    stock = max(50000, rate * args.duration * 2)
                    item = int(sql(stock=stock))
                    extra = ['-e', f'ITEM_ID={item}', '-e', f'RUN_ID={run_id}-{name}']
                metrics, code = load(name, mode, rate, args.duration, extra)
                success = metrics['business_success']['value']
                throughput = metrics['http_reqs']['rate']
                record = dict(mode=mode, rate=rate, repeat=repeat, achieved=throughput,
                              successful=throughput * success, p95=metrics['http_req_duration']['p(95)'],
                              p99=metrics['http_req_duration']['p(99)'],
                              error=100 * metrics['http_req_failed']['value'],
                              dropped=metrics.get('dropped_iterations', {}).get('count', 0), passed=code == 0)
                if item is not None:
                    values = sql(f'''SELECT i.total_stock, i.available_stock,
                        count(r.id), COALESCE(sum(r.quantity), 0)
                        FROM flash_sale_items i LEFT JOIN reservations r
                        ON r.flash_sale_item_id = i.id WHERE i.id = {item}
                        GROUP BY i.id;''').split('|')
                    initial, remaining, count, units = map(int, values)
                    invariant = initial == remaining + units and remaining >= 0
                    record['inventory'] = dict(item_id=item, initial=initial, remaining=remaining,
                                               reservations=count, units=units,
                                               oversold=max(0, units - initial), invariant=invariant)
                    record['passed'] &= invariant
                records.append(record)
                (output / 'runs.json').write_text(json.dumps(records, indent=2))

    lines = [f'# FlashScale — {args.label.title()} Benchmark', '', '## Environment', '',
             '- Backend: Java 21 + Spring Boot (verify exact running version)',
             '- Database: PostgreSQL 17; data access: JPA + JDBC',
             '- Configured HikariCP maximum pool size: 10',
             '- Deployment: local application + Docker PostgreSQL; k6 on same host',
             f'- OS: {metadata["platform"]}', f'- Git commit: {metadata["git_commit"]}',
             f'- Working tree dirty: {bool(metadata["git_status"])}',
             f'- k6: {metadata["k6"]}',
             f'- Duration: {args.duration}s/run; VUs: {args.vus}; repeats: {args.repeats}',
             '- CPU, RAM, Docker limits, Redis status: record manually', '']
    for mode, title in [('product', 'Read Throughput'), ('reserve', 'Reservation Throughput')]:
        lines += [f'## {title}', '',
                  '| Requested RPS | Repeat | Achieved RPS | Successful RPS | p95 ms | p99 ms | Error % | Dropped | Pass |',
                  '|---|---|---|---|---|---|---|---|---|']
        for r in records:
            if r['mode'] == mode:
                lines.append(f'| {r["rate"]} | {r["repeat"]} | {r["achieved"]:.2f} | {r["successful"]:.2f} | {r["p95"]:.2f} | {r["p99"]:.2f} | {r["error"]:.2f} | {r["dropped"]} | {r["passed"]} |')
        lines.append('')
    lines += ['## Database Correctness', '',
              '| Item | Initial stock | Reservations | Reserved units | Remaining | Oversold | Invariant verified |',
              '|---|---|---|---|---|---|---|']
    for r in records:
        if 'inventory' in r:
            i = r['inventory']
            lines.append(f'| {i["item_id"]} | {i["initial"]} | {i["reservations"]} | {i["units"]} | {i["remaining"]} | {i["oversold"]} | {i["invariant"]} |')
    lines += ['', '## Bottleneck Investigation', '',
              '- PostgreSQL most expensive query: unmeasured', '- PostgreSQL lock waits: unmeasured',
              '- Peak Hikari active/pending connections: unmeasured',
              '- JVM CPU/GC observations: unmeasured', '', '## Conclusion', '']
    for mode in ['product', 'reserve']:
        passed_rates = [rate for rate in {r['rate'] for r in records if r['mode'] == mode}
                        if all(r['passed'] for r in records if r['mode'] == mode and r['rate'] == rate)]
        lines.append(f'- Highest passing tested {mode} rate: {max(passed_rates) if passed_rates else "none"} RPS')
    lines += ['- These are short local runs, not proof of long-term sustainable capacity.',
              '- Primary observed bottleneck: undetermined pending telemetry.',
              '- Next experiment: Redis caching and Lua reservation admission, using the same workload.', '']
    (output / 'report.md').write_text('\n'.join(lines))
    print(f'Report: {output / "report.md"}')
    if args.compare_with:
        command(['python3', 'benchmarks/compare-results.py', str(args.compare_with.resolve()), str(output)])


if __name__ == '__main__':
    main()
