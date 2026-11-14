# FlashScale capacity results

Hardware: Mac14,2, 8 CPUs, 8 GiB RAM. Application, k6, PostgreSQL, Redis and Kafka share this host. Product ID 1, local HTTP port 8081. No production capacity or reservation-throughput claim.

| Trial | Duration | Load model | Achieved RPS | HTTP failures | Dropped arrivals | p95 ms | p99 ms | Pass |
|---|---|---|---|---|---|---|---|---|
| Initial | 60s | 10k/sec, 1000 VUs | 9810.55 | 21 | 11348 | 16.833 | 75.191 | False |
| Repeat | 120s | 10k/sec, 200 VUs | 9888.20 | 0 | 13395 | 0.949 | 11.345 | False |
| Final throughput | 60s | 50 continuous clients | 57382.72 | 0 | 0 | 2.146 | 4.265 | True |
| Final arrivals | 120s | 10k/sec, 200 VUs | 9937.23 | 0 | 7414 | 0.227 | 6.298 | False |

The final continuous-load test passed: cached product-read throughput exceeds 10,000/sec. Fixed-arrival tests failed the zero-dropped-iterations requirement; the source of dropped arrivals is unmeasured. Do not treat continuous-load throughput as proof that every request arriving at 10k/sec will be handled.

Acceptance criteria: >99.9% expected HTTP responses, <0.1% HTTP failures, >=99% of requested throughput, p95 <100 ms, p99 <250 ms; fixed-arrival runs additionally require zero dropped iterations.

The cache bounds memory to 10,000 entries per instance, coalesces concurrent loads for each key, and expires local entries after five seconds. Combined with Redis TTL, product metadata may be stale for roughly 65 seconds. Inventory is not locally cached.

Final build: 22 tests passed including database/Redis integration and cache/publisher regression tests. Publisher regression uses mocked acknowledgements; end-to-end Kafka throughput, reservation capacity, multi-instance scaling, varied product IDs, and long-duration operation remain unmeasured.

The initial trials precede minor publisher changes; the final two trials use the verified final packaged build. Raw summaries, logs, source diff and final JAR checksum are retained alongside this report.
