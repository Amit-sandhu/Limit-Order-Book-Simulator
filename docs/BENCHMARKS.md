# JMH Benchmarks Guide

## Overview

This project includes a comprehensive JMH (Java Microbenchmark Harness) benchmark suite to measure and track the performance of the Limit Order Book implementation.

## Benchmark Modules

### 1. OrderMatchingBenchmark
Tests the full matching engine pipeline:
- Order submission latency
- Matching throughput
- Market order execution
- Mixed workload simulation
- FOK order handling
- Market data query performance

### 2. OrderBookBenchmark
Isolates core data structure operations:
- Order insertion (empty vs populated book)
- Cancellation (front, middle, random)
- Best price queries
- Queue operations (take, re-add)
- Order lookup
- Market depth snapshots
- Full iteration

## Running Benchmarks

### Prerequisites
```bash
# Install Maven (if not present)
# Java 21 required
```

### Build & Run (Maven - Recommended)
```bash
cd benchmarks
mvn clean package
java -jar target/lob-benchmarks-1.0.0.jar
```

### Quick Development Run
```bash
cd benchmarks
mvn clean compile exec:java -Dexec.mainClass="com.limitorderbook.benchmarks.BenchmarkRunner" -Dexec.args="runQuick"
```

### Run Specific Benchmark
```bash
# Matching engine benchmarks only
java -jar target/lob-benchmarks-1.0.0.jar com.limitorderbook.benchmarks.OrderMatchingBenchmark

# Order book data structure benchmarks only
java -jar target/lob-benchmarks-1.0.0.jar com.limitorderbook.benchmarks.OrderBookBenchmark
```

### Custom Parameters
```bash
# Override order count parameter
java -jar target/lob-benchmarks-1.0.0.jar -p orderCount=100000

# More iterations for stable results
java -jar target/lob-benchmarks-1.0.0.jar -wi 5 -i 10 -f 2
```

## Benchmark Configuration

### Annotations Used

```java
@State(Scope.Benchmark)           // State shared across threads in same benchmark
@BenchmarkMode(Mode.AverageTime)  // Report average time per operation
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, warmups = 1)     // JVM forks for isolation
@Param({"100", "1000", "10000"})  // Parameterized benchmark sizes
```

### Key Parameters

| Parameter | Values | Description |
|-----------|--------|-------------|
| `orderCount` | 100, 1000, 10000 | Number of orders per benchmark iteration |
| Warmup iterations | 3 | JIT compilation warmup |
| Measurement iterations | 5 | Actual measurement runs |
| Forks | 1 | Separate JVM processes (increase for production) |

## Interpreting Results

### Output Format
```
Benchmark                                    Mode  Cnt   Score   Error  Units
OrderMatchingBenchmark.submitLimitOrdersNoMatch  avgt    5  123.45 ± 12.34  ns/op
OrderMatchingBenchmark.submitAndMatchLimitOrders  avgt    5  456.78 ± 45.67  ns/op
```

### Key Metrics

| Metric | Meaning | Target |
|--------|---------|--------|
| **Score** | Average time per operation | Lower is better |
| **Error** | 99.9% confidence interval | Should be < 10% of score |
| **Units** | Time unit (ns/op) | Nanoseconds per operation |

### Understanding Variance

**High variance causes:**
- GC pauses (mitigated by `@Fork` and `-XX:+UseG1GC`)
- JIT compilation during measurement
- OS scheduling / context switches
- CPU frequency scaling (disable turbo boost for consistency)

**Reducing variance:**
```bash
# Pin to specific CPU cores
taskset -c 0-3 java -jar benchmarks.jar

# Disable frequency scaling (Linux)
sudo cpupower frequency-set --governor performance

# Use larger heap to reduce GC
java -Xms4g -Xmx4g -jar benchmarks.jar
```

## Benchmark Scenarios Explained

### 1. submitLimitOrdersNoMatch
**Purpose**: Pure order book insertion performance
**Scenario**: Buy orders submitted to empty sell side
**Measures**: TreeMap insertion + HashMap put + PriceLevel queue add
**Expected**: ~100-300 ns/op (scales with log P)

### 2. submitAndMatchLimitOrders
**Purpose**: Full matching pipeline
**Scenario**: Pre-populated sell side, crossing buy orders
**Measures**: Matching loop + trade generation + re-add logic
**Expected**: ~500-2000 ns/op (depends on match count)

### 3. marketOrderMatching
**Purpose**: Market order execution against deep book
**Scenario**: Deep sell book, market buy orders
**Measures**: Price level traversal + multiple matches per order
**Expected**: ~1-5 µs/op (scales with liquidity depth)

### 4. mixedWorkload
**Purpose**: Realistic trading pattern
**Scenario**: 60% limit, 20% market, 10% cancel, 10% modify
**Measures**: Combined workload throughput
**Expected**: ~500K-1M ops/sec aggregate

### 5. highFrequencySubmission
**Purpose**: Maximum throughput test
**Scenario**: 1000 tiny IOC orders per invocation
**Measures**: Sustained submission rate
**Expected**: > 5M orders/sec

### 6. fokOrderHandling
**Purpose**: FOK rollback performance
**Scenario**: FOK orders that fail (insufficient liquidity)
**Measures**: Matching attempt + rollback logic
**Expected**: ~2-10 µs/op (higher due to rollback)

### 7. marketDepthQuery
**Purpose**: Market data dissemination
**Scenario**: Deep book, repeated depth snapshots
**Measures**: TreeMap traversal + snapshot creation
**Expected**: ~1-5 µs/op for depth=10

### 8. tradeHistoryQuery
**Purpose**: Trade reporting performance
**Scenario**: Many trades, recent history queries
**Measures**: ArrayList subList + unmodifiableList
**Expected**: ~100-500 ns/op

## OrderBookBenchmark Scenarios

### Data Structure Microbenchmarks

| Benchmark | Operation | Expected Complexity |
|-----------|-----------|---------------------|
| addOrdersEmptyBook | TreeMap.put + HashMap.put + DLL add | O(log P) |
| addOrdersPopulatedBook | TreeMap.get + DLL add | O(log P) |
| cancelFrontOrder | HashMap.get + DLL remove front | O(1) |
| cancelMiddleOrder | HashMap.get + DLL remove middle | **O(1)** |
| getBestPrices | TreeMap.firstKey/lastKey | **O(1)** |
| takeFirstOrder | DLL remove front + HashMap remove | O(1) |
| reAddOrderToFront | DLL add front | O(1) |
| getOrderReference | HashMap.get | **O(1)** |
| getMarketDepth | TreeMap traversal (D levels) | O(D) |
| iterateAllOrders | HashMap.values iteration | O(N) |
| clearOrderBook | TreeMap.clear + HashMap.clear | O(1) amortized |

## Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| Limit order submission (no match) | < 500 ns | submitLimitOrdersNoMatch @ 10K |
| Limit order match (single level) | < 2 µs | submitAndMatchLimitOrders @ 1K |
| Market order (5 levels) | < 10 µs | marketOrderMatching @ 1K |
| Order cancellation | < 300 ns | cancelMiddleOrder @ 10K |
| Best bid/ask query | < 100 ns | getBestPrices @ 10K |
| Market depth (10 levels) | < 5 µs | getMarketDepth @ 10K |
| Mixed workload throughput | > 500K ops/s | mixedWorkload @ 10K |

## Regression Detection

### Baseline Comparison
```bash
# Save baseline
java -jar benchmarks.jar -rf json -rff baseline.json

# Compare after changes
java -jar benchmarks.jar -rf json -rff current.json
# Use JMH visualizer or custom script to compare
```

### CI Integration
```yaml
# GitHub Actions example
- name: Run Benchmarks
  run: |
    cd benchmarks
    mvn clean package
    java -jar target/lob-benchmarks-1.0.0.jar -rf json -rff results.json
    
- name: Check Regression
  run: |
    python scripts/check_benchmark_regression.py baseline.json results.json --threshold 0.15
```

## Profiling Integration

### Async Profiler
```bash
# CPU profiling
java -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
  -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=profile.html \
  -jar benchmarks.jar OrderMatchingBenchmark.submitAndMatchLimitOrders

# Allocation profiling
java -agentpath:/path/to/libasyncProfiler.so=start,event=alloc,file=alloc.html \
  -jar benchmarks.jar OrderMatchingBenchmark.submitLimitOrdersNoMatch
```

### JFR (Java Flight Recorder)
```bash
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
  -jar benchmarks.jar
```

### GC Logging
```bash
java -Xlog:gc*:file=gc.log:time,uptime,level,tags \
  -jar benchmarks.jar
```

## Common Pitfalls

### 1. Dead Code Elimination
```java
// WRONG - JIT may eliminate
@Benchmark
public void badBenchmark() {
    engine.submitLimitOrder(...);
}

// CORRECT - consume result
@Benchmark
public void goodBenchmark(Blackhole bh) {
    Order order = engine.submitLimitOrder(...);
    bh.consume(order);
}
```

### 2. Constant Folding
```java
// WRONG - constant parameters
@Benchmark
public void badBenchmark() {
    engine.submitLimitOrder(BUY, 10000, 100, GTC);
}

// CORRECT - use @Param or State fields
@Param({"10000", "10100", "10200"})
private long price;

@Benchmark
public void goodBenchmark() {
    engine.submitLimitOrder(BUY, price, 100, GTC);
}
```

### 3. Shared Mutable State
```java
// WRONG - shared engine across threads
@State(Scope.Thread)
public class BadBench {
    MatchingEngine engine = new MatchingEngine();  // Each thread gets own
}

// CORRECT - use Scope.Benchmark with proper setup
@State(Scope.Benchmark)
public class GoodBench {
    MatchingEngine engine;
    
    @Setup(Level.Iteration)
    public void setup() { engine = new MatchingEngine(); }
}
```

### 4. Insufficient Warmup
```java
// Minimum for reliable results
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, warmups = 1)  // Multiple JVMs
```

## Result Recording

### Results Directory Structure
```
results/
├── baseline/
│   ├── 2024-01-15_orderMatching.json
│   └── 2024-01-15_orderBook.json
├── current/
│   ├── 2024-01-20_orderMatching.json
│   └── 2024-01-20_orderBook.json
└── comparison/
    └── regression_report_2024-01-20.md
```

### Recording Results
```bash
# Save with timestamp
DATE=$(date +%Y-%m-%d_%H-%M-%S)
java -jar benchmarks.jar -rf json -rff results/current/${DATE}_matching.json
java -jar benchmarks.jar -rf json -rff results/current/${DATE}_book.json -p orderCount=10000
```

## Expected Results (Reference)

### Machine Specs (Reference)
- CPU: AMD Ryzen 9 7950X / Intel i9-13900K
- RAM: 64GB DDR5-5600
- Java: 21.0.2 (GraalVM or Temurin)
- OS: Ubuntu 22.04 / Linux 6.x

### Sample Results (OrderCount=10000)

| Benchmark | Score (ns/op) | Throughput (ops/s) |
|-----------|---------------|---------------------|
| submitLimitOrdersNoMatch | ~180 | ~5.5M |
| submitAndMatchLimitOrders | ~850 | ~1.2M |
| marketOrderMatching | ~3,200 | ~300K |
| mixedWorkload | ~1,500 | ~650K |
| highFrequencySubmission | ~45 | ~22M |
| fokOrderHandling | ~8,500 | ~120K |
| marketDepthQuery | ~2,100 | ~475K |
| tradeHistoryQuery | ~320 | ~3.1M |

| OrderBook Benchmark | Score (ns/op) |
|---------------------|---------------|
| addOrdersEmptyBook | ~120 |
| addOrdersPopulatedBook | ~150 |
| cancelFrontOrder | ~80 |
| cancelMiddleOrder | ~90 |
| getBestPrices | ~25 |
| takeFirstOrder | ~70 |
| reAddOrderToFront | ~60 |
| getOrderReference | ~15 |
| getMarketDepth | ~1,800 |
| iterateAllOrders | ~45/element |
| clearOrderBook | ~500 |

**Note**: Actual results vary significantly by hardware, JVM, and system load. Use as relative comparison only.

## Continuous Benchmarking

### Automated Tracking
1. Run benchmarks on every PR (CI)
2. Store results as artifacts
3. Compare against main branch baseline
4. Fail PR if regression > 15%
5. Track trends over time (Grafana/dashboards)

### Key Dashboards
- Latency percentiles (p50, p99, p99.9) over time
- Throughput over time
- Memory allocation rate
- GC pause impact

## Troubleshooting

### Benchmark Won't Run
```
Error: Could not find or load main class
```
→ Ensure `mvn package` completed, check `target/lob-benchmarks-1.0.0.jar` exists

### High Variance
```
Score: 1000 ± 500 ns/op
```
→ Increase warmup, pin CPU, disable turbo boost, use larger heap

### OutOfMemoryError
```
java.lang.OutOfMemoryError: Java heap space
```
→ Reduce `@Param` values, increase heap: `-Xmx4g`

### JIT Compilation Issues
```
Warmup iterations completed but code not compiled
```
→ Increase warmup time, add `-XX:CompileThreshold=1000`

## Further Reading

- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [JMH Samples](https://hg.openjdk.org/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)
- [Avoiding Benchmarking Pitfalls](https://shipilev.net/blog/2014/nanotrusting-nanotime/)
- [Java Performance Tuning](https://www.oracle.com/java/technologies/javase-performance.html)