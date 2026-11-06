#!/usr/bin/env python3
"""Compare two complete run-baseline.py result directories without modifying either."""
import argparse
import json
from pathlib import Path
from statistics import median


def compare(baseline, candidate):
    environments = [json.loads((p / 'environment.json').read_text()) for p in (baseline, candidate)]
    for field in ('duration_seconds', 'vus', 'repeats', 'k6', 'platform'):
        if environments[0][field] != environments[1][field]:
            raise ValueError(f'Incompatible environment field: {field}')
    datasets = [json.loads((p / 'runs.json').read_text()) for p in (baseline, candidate)]
    expected = {(mode, rate, repeat) for mode, rates in [('product', [50, 100, 250, 500]), ('reserve', [25, 50, 100, 200])]
                for rate in rates for repeat in range(1, environments[0]['repeats'] + 1)}
    for records in datasets:
        keys = [(r['mode'], r['rate'], r['repeat']) for r in records]
        if set(keys) != expected or len(keys) != len(expected):
            raise ValueError('Incomplete or duplicate run matrix; comparison requires every repetition')
    lines = ['# Redis + PostgreSQL vs PostgreSQL', '',
             f'PostgreSQL source: `{baseline}`', f'Redis source: `{candidate}`', '',
             'Values are medians across runs, not pooled latency percentiles. Negative latency change means lower Redis latency.', '',
             '| Workload | Target RPS | PG success RPS | Redis success RPS | PG p95 ms | Redis p95 ms | p95 change | PG p99 ms | Redis p99 ms | PG/Redis error % | PG/Redis drops (sum) | PG/Redis passing runs |',
             '|---|---|---|---|---|---|---|---|---|---|---|---|']
    for mode, rate in sorted({(r['mode'], r['rate']) for r in datasets[0]}):
        groups = [[r for r in records if r['mode'] == mode and r['rate'] == rate] for records in datasets]
        def values(field):
            return [median(r[field] for r in group) for group in groups]
        throughput, p95, p99, error = [values(f) for f in ('successful', 'p95', 'p99', 'error')]
        delta = f'{(p95[1] / p95[0] - 1) * 100:+.1f}%' if p95[0] else 'N/A'
        drops = [sum(r['dropped'] for r in g) for g in groups]
        passes = [sum(bool(r['passed']) for r in g) for g in groups]
        lines.append(f'| {mode} | {rate} | {throughput[0]:.2f} | {throughput[1]:.2f} | {p95[0]:.2f} | {p95[1]:.2f} | {delta} | {p99[0]:.2f} | {p99[1]:.2f} | {error[0]:.2f}/{error[1]:.2f} | {drops[0]}/{drops[1]} | {passes[0]}/{passes[1]} |')
    lines += ['', '## Interpretation', '',
              '- Reads compare the same product endpoint with cache-aside Redis enabled in the candidate. Cache TTL remains 60 seconds; runs can include expiry misses.',
              '- Writes compare PostgreSQL reservations with and without Redis admission. HTTP 429 rejections remain failures; they do not count as successful reservations.',
              '- Each iteration uses a unique user; this does not exercise repeated-user rate limiting.',
              '- No throughput improvement can be claimed when both variants already meet the requested arrival rate. Compare latency and errors at that rate instead.',
              '- Historical baseline and candidate were measured at different times and database sizes. Same-machine noise, JVM warmup, cache state, and dirty working trees limit causal conclusions.',
              '- Highest passing tested rates are not maximum sustainable capacity. Establish a latency SLO and extend the load range before making capacity claims.', '']
    for label, records in zip(('PostgreSQL', 'Redis + PostgreSQL'), datasets):
        for mode in ('product', 'reserve'):
            rates = {r['rate'] for r in records if r['mode'] == mode}
            passing = [rate for rate in rates if all(r['passed'] for r in records if r['mode'] == mode and r['rate'] == rate)]
            lines.append(f'- {label}, {mode}: highest rate passing every repetition = {max(passing) if passing else "none"} RPS.')
        inventory = [r['inventory'] for r in records if 'inventory' in r]
        lines.append(f'- {label}: inventory invariant passed {sum(i["invariant"] for i in inventory)}/{len(inventory)} runs; oversold units = {sum(i["oversold"] for i in inventory)}.')
    target = candidate / 'comparison.md'
    target.write_text('\n'.join(lines) + '\n')
    print(f'Comparison: {target}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('candidate', type=Path)
    args = parser.parse_args()
    compare(args.baseline, args.candidate)
