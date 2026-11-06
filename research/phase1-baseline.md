# Phase 1 exploratory reducer baseline

Measured on 2026-11-05 from `a5b29ca` plus the JMH harness commit. This is **not** an end-to-end execution result, a design target, or a latency/throughput claim for the OMS. It excludes SQLite commits, queue admission, broker delivery, risk, and network time. The one-thread benchmark repeatedly applies a 100-share unique fill to the same prebuilt 5,000-share child projection. The small sample and high variation make it unsuitable for comparisons.

- Command: `JAVA_HOME=<Java 21 home> ./gradlew :execution-core:jmhPhase1 --no-daemon`
- Host: Apple M1 Pro, 16 GiB RAM, macOS Darwin 25.6.0, arm64.
- Runtime: Temurin/OpenJDK 21.0.13+9, JMH 1.37, one thread, one fork, 2 × 1 s warmup, 3 × 1 s measurement, compiler blackhole.
- Measured throughput iterations: 3,572,116; 3,627,271; 2,019,894 operations/s.
- JMH mean: 3,073,094 operations/s; observed range: 2,019,894–3,627,271 operations/s. The 99.9% interval printed by JMH was uninformative with only three samples and is intentionally not interpreted.

The benchmark checks only an isolated reducer operation. A future performance gate needs repeated longer runs, allocation/GC profiling, p50/p95/p99/p99.9 distributions with adequate samples, journal append cost, and end-to-end order processing including the simulated broker. No claim about native Linux x86-64 or hardware-specific low latency follows from this macOS run.
