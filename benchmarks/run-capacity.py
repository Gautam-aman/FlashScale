#!/usr/bin/env python3
"""Measure product-read capacity. Requires a running app and k6; preserves failed runs."""
import argparse
import datetime
import json
import platform
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8081')
    parser.add_argument('--rate', type=int, default=10000)
    parser.add_argument('--duration', type=int, default=120)
    parser.add_argument('--vus', type=int, default=1000)
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--product-id', type=int, default=1)
    args = parser.parse_args()
    if min(args.rate, args.duration, args.vus, args.repeats, args.product_id) < 1:
        parser.error('numeric options must be positive')
    output = ROOT / 'benchmarks/results' / ('capacity-' + datetime.datetime.now(
        datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid.uuid4().hex[:6])
    output.mkdir(parents=True)
    metadata = dict(vars(args), platform=platform.platform(),
                    timestamp=datetime.datetime.now(datetime.timezone.utc).isoformat())
    for name, command in [('commit', ['git', 'rev-parse', 'HEAD']),
                          ('diff', ['git', 'diff']), ('k6', ['k6', 'version'])]:
        metadata[name] = subprocess.check_output(command, cwd=ROOT, text=True).strip()
    (output / 'environment.json').write_text(json.dumps(metadata, indent=2))

    def run(name, rate, duration):
        command = ['k6', 'run', '-e', f'BASE_URL={args.base_url}', '-e', 'MODE=product',
                   '-e', f'PRODUCT_ID={args.product_id}', '-e', f'RATE={rate}',
                   '-e', f'DURATION={duration}s', '-e', f'VUS={args.vus}',
                   '--summary-export', str(output / f'{name}.json'), 'load-tests/capacity.js']
        print(f'Running {name}: {output}', flush=True)
        with (output / f'{name}.log').open('w') as log:
            status = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT).returncode
        summary = output / f'{name}.json'
        if status not in (0, 99) or not summary.exists():
            raise RuntimeError(f'k6 execution failed; see {output / (name + ".log")}')
        return status, json.loads(summary.read_text())['metrics']

    # Warmup is retained, but never substituted for a measured run.
    _, warmup = run('warmup', min(args.rate, 1000), 20)
    if warmup['business_success']['value'] <= 0.999:
        raise RuntimeError('Warmup business requests failed; fix the application before measuring')
    rows = []
    for repeat in range(1, args.repeats + 1):
        status, metrics = run(f'product-{args.rate}-r{repeat}', args.rate, args.duration)
        rows.append(dict(repeat=repeat, passed=status == 0,
                         achieved_rps=metrics['http_reqs']['rate'],
                         success=metrics['business_success']['value'],
                         dropped=metrics.get('dropped_iterations', {}).get('count', 0),
                         p95_ms=metrics['http_req_duration']['p(95)'],
                         p99_ms=metrics['http_req_duration']['p(99)']))
        (output / 'runs.json').write_text(json.dumps(rows, indent=2))
    lines = ['# Product read capacity', '',
             'Workload: one cached product, open-loop fixed arrival rate. Reservation capacity is unmeasured.', '',
             '| Repeat | Achieved RPS | Success | Dropped | p95 ms | p99 ms | Pass |',
             '|---|---|---|---|---|---|---|']
    for row in rows:
        lines.append(f'| {row["repeat"]} | {row["achieved_rps"]:.2f} | {row["success"]:.6f} | '
                     f'{row["dropped"]} | {row["p95_ms"]:.2f} | {row["p99_ms"]:.2f} | {row["passed"]} |')
    (output / 'report.md').write_text('\n'.join(lines) + '\n')
    print(output / 'report.md')
    return 0 if all(row['passed'] for row in rows) else 1


if __name__ == '__main__':
    raise SystemExit(main())
