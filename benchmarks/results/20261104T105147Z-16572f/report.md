# FlashScale — PostgreSQL Baseline Benchmark

## Environment

- Backend: Java 21 + Spring Boot (verify exact running version)
- Database: PostgreSQL 17; data access: JPA + JDBC
- Configured HikariCP maximum pool size: 10
- Deployment: local application + Docker PostgreSQL; k6 on same host
- OS: macOS-27.2-arm64-arm-64bit
- Git commit: 8cb2cec2219df2547f36d5b1a3b236e2b3a54631
- Working tree dirty: True
- k6: k6 v2.1.0 (commit/devel, go1.26.4, darwin/arm64)
- Duration: 60s/run; VUs: 100; repeats: 3
- CPU, RAM, Docker limits, Redis status: record manually

## Read Throughput

| Requested RPS | Repeat | Achieved RPS | Successful RPS | p95 ms | p99 ms | Error % | Dropped | Pass |
|---|---|---|---|---|---|---|---|---|
| 50 | 1 | 50.01 | 50.01 | 9.12 | 58.26 | 0.00 | 0 | True |
| 50 | 2 | 50.01 | 50.01 | 4.62 | 22.95 | 0.00 | 0 | True |
| 50 | 3 | 50.01 | 50.01 | 9.07 | 58.85 | 0.00 | 0 | True |
| 100 | 1 | 100.01 | 100.01 | 3.17 | 6.41 | 0.00 | 0 | True |
| 100 | 2 | 100.01 | 100.01 | 6.53 | 51.19 | 0.00 | 0 | True |
| 100 | 3 | 100.01 | 100.01 | 4.07 | 14.04 | 0.00 | 0 | True |
| 250 | 1 | 250.01 | 250.01 | 3.49 | 33.33 | 0.00 | 0 | True |
| 250 | 2 | 250.01 | 250.01 | 4.86 | 50.58 | 0.00 | 0 | True |
| 250 | 3 | 250.00 | 250.00 | 2.72 | 4.90 | 0.00 | 0 | True |
| 500 | 1 | 499.99 | 499.99 | 2.28 | 7.22 | 0.00 | 0 | True |
| 500 | 2 | 499.98 | 499.98 | 2.24 | 6.44 | 0.00 | 0 | True |
| 500 | 3 | 499.98 | 499.98 | 1.98 | 3.66 | 0.00 | 0 | True |

## Reservation Throughput

| Requested RPS | Repeat | Achieved RPS | Successful RPS | p95 ms | p99 ms | Error % | Dropped | Pass |
|---|---|---|---|---|---|---|---|---|
| 25 | 1 | 25.01 | 25.01 | 14.03 | 41.43 | 0.00 | 0 | True |
| 25 | 2 | 25.01 | 25.01 | 12.69 | 17.90 | 0.00 | 0 | True |
| 25 | 3 | 25.00 | 25.00 | 12.53 | 20.36 | 0.00 | 0 | True |
| 50 | 1 | 50.01 | 50.01 | 7.01 | 10.76 | 0.00 | 0 | True |
| 50 | 2 | 50.00 | 50.00 | 7.25 | 10.12 | 0.00 | 0 | True |
| 50 | 3 | 50.01 | 50.01 | 6.94 | 11.37 | 0.00 | 0 | True |
| 100 | 1 | 100.01 | 100.01 | 5.67 | 9.23 | 0.00 | 0 | True |
| 100 | 2 | 100.01 | 100.01 | 16.05 | 172.78 | 0.00 | 0 | True |
| 100 | 3 | 99.99 | 99.99 | 14.69 | 74.98 | 0.00 | 0 | True |
| 200 | 1 | 199.73 | 199.73 | 18.39 | 297.97 | 0.00 | 16 | False |
| 200 | 2 | 200.01 | 200.01 | 16.21 | 157.17 | 0.00 | 0 | True |
| 200 | 3 | 199.58 | 199.58 | 48.03 | 423.46 | 0.00 | 25 | False |

## Database Correctness

| Item | Initial stock | Reservations | Reserved units | Remaining | Oversold | Invariant verified |
|---|---|---|---|---|---|---|
| 5 | 50000 | 1501 | 1501 | 48499 | 0 | True |
| 6 | 50000 | 1501 | 1501 | 48499 | 0 | True |
| 7 | 50000 | 1500 | 1500 | 48500 | 0 | True |
| 8 | 50000 | 3001 | 3001 | 46999 | 0 | True |
| 9 | 50000 | 3000 | 3000 | 47000 | 0 | True |
| 10 | 50000 | 3001 | 3001 | 46999 | 0 | True |
| 11 | 50000 | 6001 | 6001 | 43999 | 0 | True |
| 12 | 50000 | 6001 | 6001 | 43999 | 0 | True |
| 13 | 50000 | 6000 | 6000 | 44000 | 0 | True |
| 14 | 50000 | 11984 | 11984 | 38016 | 0 | True |
| 15 | 50000 | 12001 | 12001 | 37999 | 0 | True |
| 16 | 50000 | 11976 | 11976 | 38024 | 0 | True |

## Bottleneck Investigation

- PostgreSQL most expensive query: unmeasured
- PostgreSQL lock waits: unmeasured
- Peak Hikari active/pending connections: unmeasured
- JVM CPU/GC observations: unmeasured

## Conclusion

- Highest passing tested product rate: 500 RPS
- Highest passing tested reserve rate: 100 RPS
- These are short local runs, not proof of long-term sustainable capacity.
- Primary observed bottleneck: undetermined pending telemetry.
- Next experiment: Redis caching and Lua reservation admission, using the same workload.
