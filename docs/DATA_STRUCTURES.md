# Data Structures & Algorithmic Complexity

## Overview

The Limit Order Book uses a combination of standard Java collections and custom intrusive data structures to achieve optimal performance for trading operations.

## Core Data Structures

### 1. Price Level Index: `TreeMap<Long, PriceLevel>`

**Buy Side:**
```java
private final TreeMap<Long, PriceLevel> buyLevels = 
    new TreeMap<>(Comparator.reverseOrder());  // Descending: highest bid first
```

**Sell Side:**
```java
private final TreeMap<Long, PriceLevel> sellLevels = 
    new TreeMap<>();  // Ascending: lowest ask first
```

**Operations & Complexity:**

| Operation | Complexity | Notes |
|-----------|------------|-------|
| `firstKey()` / `lastKey()` | O(1) | Best bid/ask |
| `get(price)` | O(log P) | Price level lookup |
| `put(price, level)` | O(log P) | New price level |
| `remove(price)` | O(log P) | Empty level cleanup |
| `navigableKeySet()` | O(1) | Iterator creation |
| Iteration (depth D) | O(D) | Market depth queries |

Where **P = number of distinct price levels** (typically 100-10,000).

**Why TreeMap?**
- Sorted map with guaranteed O(log n) operations
- NavigableMap interface for range queries
- Thread-safe for single-writer (our model)
- No external dependencies

### 2. Order Lookup: `HashMap<Long, OrderReference>`

```java
private final HashMap<Long, OrderReference> orderLookup = new HashMap<>();
```

**OrderReference:**
```java
static final class OrderReference {
    private final Order order;
    private final OrderSide side;
    private final long price;  // Cached for O(1) price level access
}
```

**Operations & Complexity:**

| Operation | Complexity | Notes |
|-----------|------------|-------|
| `get(orderId)` | **O(1)** | Primary lookup |
| `put(orderId, ref)` | **O(1)** | On addOrder |
| `remove(orderId)` | **O(1)** | On cancel/remove |
| `containsKey(id)` | **O(1)** | Existence check |

**Why HashMap?**
- True O(1) average case
- Order IDs are unique longs (good hash distribution)
- Critical for O(1) cancellation

### 3. Price Level Queue: `PriceLevel` (Custom)

```java
public final class PriceLevel implements Iterable<Order> {
    private final ArrayDeque<Order> queue = new ArrayDeque<>();
    private long totalQuantity = 0;
    
    // Intrusive doubly-linked list for O(1) middle removal
    // Each Order has transient OrderNode with prev/next
}
```

**Structure:**
```
PriceLevel @ 10000
├── totalQuantity: 500
└── queue (ArrayDeque):
    ├── Order#1 (arrival: 1, qty: 100)  ← HEAD (oldest)
    ├── Order#2 (arrival: 5, qty: 200)
    ├── Order#3 (arrival: 12, qty: 100)
    └── Order#4 (arrival: 18, qty: 100)  ← TAIL (newest)
```

**Intrusive Doubly-Linked List:**
```java
// In Order.java (transient, not serialized)
private transient OrderNode priceLevelNode;

static final class OrderNode {
    OrderNode prev;
    OrderNode next;
    Order order;  // Back-reference
}
```

**Operations & Complexity:**

| Operation | Complexity | Mechanism |
|-----------|------------|-----------|
| `addToBack(order)` | **O(1)** | ArrayDeque.addLast + DLL link |
| `removeFromFront()` | **O(1)** | ArrayDeque.removeFirst + DLL unlink |
| `remove(order)` | **O(1)** | DLL unlink via order.priceLevelNode |
| `reAddToFront(order)` | **O(1)** | ArrayDeque.addFirst + DLL link |
| `iterator()` | O(N) | ArrayDeque iterator |
| `getTotalQuantity()` | **O(1)** | Cached field |

**Why ArrayDeque + Intrusive DLL?**
- **ArrayDeque**: Cache-friendly, O(1) at both ends, no node allocation
- **Intrusive DLL**: O(1) removal from middle without search
- **Combined**: Best of both worlds for FIFO + cancellation

### 4. Order Object (Domain Model)

```java
public final class Order {
    // Immutable identity
    private final long orderId;
    private final OrderSide side;
    private final OrderType type;
    private final int originalQuantity;
    private final long arrivalSequence;
    private final TimeInForce timeInForce;
    
    // Mutable state
    private long price;  // 0 for market orders
    private int remainingQuantity;
    private int filledQuantity;
    private OrderStatus status;
    
    // Transient for book linkage
    private transient OrderNode priceLevelNode;
}
```

**Memory Layout (Estimated):**
- Object header: 12 bytes
- 7 final fields (long/int): ~40 bytes
- 5 mutable fields: ~24 bytes
- Transient node reference: 8 bytes
- **Total: ~84 bytes per order** (plus DLL node ~24 bytes = ~108 bytes)

### 5. Trade Record (Immutable)

```java
public final class Trade {
    private final long tradeId;
    private final long buyOrderId;
    private final long sellOrderId;
    private final long executionPrice;  // Ticks
    private final int executionQuantity;
    private final long executionSequence;
}
```

**Memory: ~56 bytes per trade**

## Algorithmic Complexity Summary

### Order Submission

| Order Type | Best Case | Average Case | Worst Case |
|------------|-----------|--------------|------------|
| Limit (no match) | O(log P) | O(log P) | O(log P) |
| Limit (full match) | O(log P + M) | O(log P + M) | O(log P + M) |
| Market | O(log P + M) | O(log P + M) | O(log P + M) |

Where **M = number of matching price levels traversed** (typically 1-10).

### Order Cancellation
| Operation | Complexity |
|-----------|------------|
| `cancelOrder(id)` | **O(1)** |
| HashMap lookup | O(1) |
| DLL unlink | O(1) |
| PriceLevel quantity adjust | O(1) |
| TreeMap level cleanup (if empty) | O(log P) |

### Order Modification
| Operation | Complexity |
|-----------|------------|
| `modifyOrder(id, ...)` | O(log P) |
| Cancel original | O(1) + O(log P) cleanup |
| Add modified | O(log P) |
| **Total** | **O(log P)** |

### Market Data Queries

| Query | Complexity |
|-------|------------|
| `getBestBid()` / `getBestAsk()` | **O(1)** |
| `getMarketDepth(side, D)` | **O(D)** |
| `getOrder(id)` | **O(1)** |
| `getRecentTrades(N)` | O(N) |
| `getAllOrders()` | O(T) where T = total orders |

## Space Complexity

| Component | Space |
|-----------|-------|
| OrderBook (empty) | ~2 KB |
| Per PriceLevel | ~56 bytes + orders |
| Per Order in book | ~108 bytes |
| Per Trade in history | ~56 bytes |
| HashMap overhead | ~8 bytes/entry |
| TreeMap overhead | ~48 bytes/level |

**Typical Production Sizing:**
- 10,000 price levels × 50 orders = 500,000 orders
- Orders: 500K × 108B ≈ **54 MB**
- Trades (1M/day): 1M × 56B ≈ **56 MB**
- Total: **~110 MB** (well within JVM heap)

## Cache Efficiency Considerations

### Memory Layout Optimizations

1. **ArrayDeque**: Contiguous array storage
   - Better cache locality than LinkedList
   - No per-node allocation

2. **Intrusive DLL**: Node embedded in Order
   - No separate allocation for list nodes
   - Order and its DLL node co-located

3. **Primitive Fields**: All `long`/`int` (no boxed types)
   - Avoids Integer/Long object overhead
   - Better CPU cache utilization

4. **Trade History**: `ArrayList<Trade>`
   - Contiguous array of references
   - Sequential iteration is cache-friendly

### False Sharing Prevention

- Each `PriceLevel` is independent (no shared mutable state)
- `AtomicLong` generators only written by single thread
- Read-mostly fields (price, quantity) don't cause cache line ping-pong

## Alternative Data Structures Considered

### 1. Skip List instead of TreeMap
- **Pros**: Lock-free concurrent variants exist
- **Cons**: Higher memory overhead, more complex, no stdlib implementation
- **Decision**: TreeMap sufficient for single-threaded

### 2. Custom B-Tree for Price Levels
- **Pros**: Better cache locality, fewer pointer hops
- **Cons**: Significant implementation effort, marginal gain
- **Decision**: TreeMap O(log P) is fast enough (P < 10K)

### 3. Radix Tree / Trie for Price Levels
- **Pros**: O(1) for fixed tick size (100 ticks = 1 unit)
- **Cons**: Sparse price space wastes memory
- **Decision**: TreeMap adapts to actual price distribution

### 4. LinkedHashMap for Order Lookup
- **Pros**: Maintains insertion order
- **Cons**: Not needed (we have arrivalSequence), slightly slower
- **Decision**: HashMap is optimal for pure lookup

## Performance Characteristics

### Latency Profile (Typical)

| Operation | p50 | p99 | p99.9 |
|-----------|-----|-----|-------|
| Limit add (no match) | ~200 ns | ~500 ns | ~1 µs |
| Limit match (1 level) | ~500 ns | ~2 µs | ~5 µs |
| Market order (5 levels) | ~1 µs | ~5 µs | ~15 µs |
| Cancel order | ~100 ns | ~300 ns | ~800 ns |
| Modify order | ~400 ns | ~1 µs | ~3 µs |
| Best bid/ask | ~20 ns | ~50 ns | ~100 ns |
| Market depth (10) | ~500 ns | ~2 µs | ~5 µs |

### Throughput (Single Thread)

| Workload | Orders/Second |
|----------|---------------|
| Pure limit adds (no match) | ~3-5M |
| Limit matching (50% fill) | ~1-2M |
| Mixed (60% limit, 20% market, 10% cancel, 10% modify) | ~500K-1M |
| Market orders only | ~2-3M |

### Scaling with Price Levels

```
Time (µs)
   │
 10│                    ┌──────── TreeMap (log P)
   │                  ┐
  5│                ┐
   │              ┐
  1│──────────────┘
   │
  0.1│────────────── HashMap / ArrayDeque / DLL (O(1))
   └──────────────────────────────────
     10    100    1K    10K    100K    Price Levels (P)
```

## Concurrency Considerations (Future)

### Read-Write Lock Approach
```java
// Single writer, multiple readers
private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

// Readers
lock.readLock().lock();
try { return getBestBid(); } finally { lock.readLock().unlock(); }

// Writers
lock.writeLock().lock();
try { addOrder(order); } finally { lock.writeLock().unlock(); }
```

### Disruptor/Ring Buffer
- Single producer (gateway), single consumer (matching engine)
- Eliminates lock contention
- Batch processing for throughput

### Partitioned Order Books
- Shard by symbol: `Map<String, OrderBook>`
- Each shard single-threaded
- Horizontal scaling across cores

## Benchmarking Methodology

See [BENCHMARKS.md](BENCHMARKS.md) for JMH benchmark details, run instructions, and result interpretation.