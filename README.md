# Limit Order Book (LOB) Trading Simulation System

A complete, modular, well-tested, and benchmarked Limit Order Book implementation in Java 21, designed for high-frequency trading simulations and fintech portfolio demonstrations.

## 🎯 Features

- **Price-Time Priority Matching**: Full FIFO matching with price priority
- **Multiple Order Types**: Limit and Market orders
- **Time-in-Force**: GTC (Good Till Cancelled), IOC (Immediate Or Cancel), FOK (Fill Or Kill)
- **Integer Price Ticks**: 100 ticks = 1 currency unit for precision arithmetic
- **O(1) Order Cancellation**: Intrusive doubly-linked list for instant removal
- **Order Modification**: With priority loss semantics (price/qty increase loses priority)
- **Market Data Queries**: Best bid/ask, market depth, trade history
- **12 Invariants Validated**: Comprehensive correctness guarantees
- **JMH Benchmarks**: Performance measurement suite
- **75 Unit Tests**: Full coverage across 8 test categories

## 🏗️ Architecture

```
src/main/java/com/limitorderbook/
├── model/                 # Domain models (immutable where possible)
│   ├── Order.java         # Core order with lifecycle management
│   ├── OrderSide.java     # BUY/SELL enum
│   ├── OrderType.java     # LIMIT/MARKET enum
│   ├── OrderStatus.java   # NEW/OPEN/PARTIALLY_FILLED/FILLED/CANCELLED/REJECTED/EXPIRED
│   ├── TimeInForce.java   # GTC/IOC/FOK enum
│   └── Trade.java         # Immutable trade record
├── engine/                # Matching engine core
│   ├── MatchingEngine.java    # Central matching logic
│   ├── OrderBook.java         # TreeMap-based price levels + HashMap lookup
│   ├── PriceLevel.java        # FIFO queue with O(1) cancellation
│   └── OrderValidator.java    # Pre-submission validation
├── utils/                 # Utilities
│   ├── OrderBookPrinter.java  # Formatted output
│   └── OrderGenerator.java    # Reproducible random orders
└── Main.java              # Interactive CLI demo
```

## 📊 Data Structures & Complexity

| Operation | Complexity | Data Structure |
|-----------|------------|----------------|
| Add Order | O(log P) | TreeMap<Price, PriceLevel> |
| Cancel Order | **O(1)** | HashMap<ID, OrderReference> + Intrusive DLL |
| Modify Order | O(log P) | Cancel + Re-add |
| Best Bid/Ask | **O(1)** | TreeMap.firstKey()/lastKey() |
| Market Depth | O(D) | TreeMap traversal |
| Order Lookup | **O(1)** | HashMap |

Where P = number of price levels, D = depth requested.

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+ (or use included JARs)

### Build & Test
```bash
# Compile main classes
javac -d target/classes src/main/java/com/limitorderbook/**/*.java

# Compile tests (with JUnit JARs in lib/)
javac -cp "lib/*:target/classes" -d target/test-classes src/test/java/com/limitorderbook/*.java

# Run all 75 tests
java -jar lib/junit-platform-console-standalone-1.11.0.jar execute \
  --class-path target/classes:target/test-classes --scan-class-path
```

### Run Interactive Demo
```bash
java -cp target/classes com.limitorderbook.Main
```

### Run Benchmarks
```bash
# Using Maven (recommended)
cd benchmarks && mvn clean package && java -jar target/lob-benchmarks-1.0.0.jar

# Quick benchmark
java -cp benchmarks/target/lob-benchmarks-1.0.0.jar \
  com.limitorderbook.benchmarks.BenchmarkRunner runQuick
```

## 📖 Documentation

- [Architecture](docs/ARCHITECTURE.md) - System design and component interactions
- [Matching Rules](docs/MATCHING_RULES.md) - Price-time priority, execution price, TIF behavior
- [Data Structures](docs/DATA_STRUCTURES.md) - TreeMap, PriceLevel, HashMap, intrusive DLL
- [Benchmarks](docs/BENCHMARKS.md) - JMH methodology, results, interpretation

## ✅ Test Coverage (75 Tests)

| Test Class | Tests | Coverage |
|------------|-------|----------|
| MatchingEngineTest | 8 | Basic matching, price priority, FIFO |
| OrderCancellationTest | 8 | Front/middle/last, partial fill, unknown |
| OrderModificationTest | 8 | Price/qty changes, priority loss, FOK/IOC |
| PartialFillTest | 5 | Multi-level, multiple counterparties |
| OrderValidationTest | 18 | New/cancel/modify validation, edge cases |
| MarketOrderTest | 8 | Market matching, price protection, TIF |
| TimeInForceTest | 11 | GTC/IOC/FOK behavior, expiration |
| InvariantValidationTest | 9 | All 12 invariants verification |

## 🔧 Configuration

Key configurable parameters in `MatchingEngine`:
- `maxMarketOrderPriceDeviationTicks` - Market order price protection (default: 10000 ticks)

## 📈 Performance Targets

| Metric | Target |
|--------|--------|
| Order Submission Latency | < 1 µs (p50) |
| Matching Throughput | > 1M ops/sec |
| Memory Allocation | < 100 bytes/order |
| 99th Percentile Latency | < 10 µs |

See [Benchmarks](docs/BENCHMARKS.md) for actual results.

## 🎓 Learning Outcomes Demonstrated

- Order book architecture & matching algorithms
- Price-time priority implementation
- Financial data representation (integer ticks)
- Data structures: TreeMap, HashMap, intrusive doubly-linked list
- Algorithmic complexity analysis
- Order lifecycle management
- Partial execution handling
- Trading system correctness & invariant validation
- Performance measurement with JMH
- Java backend engineering (Java 21, JUnit 5, SLF4J)

## 📝 License

MIT License - Feel free to use for learning and portfolio purposes.

## 👤 Author

Built as a technical portfolio project for fintech startup applications (Mochatrade).