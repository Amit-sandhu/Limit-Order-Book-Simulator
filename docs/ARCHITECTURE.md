# System Architecture

## Overview

The Limit Order Book (LOB) system is a modular, single-threaded matching engine designed for deterministic, high-performance order matching. The architecture follows a clean separation between domain models, matching logic, and infrastructure concerns.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        MatchingEngine                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │  OrderBook  │  │  Validator  │  │     Trade History       │  │
│  │             │  │             │  │                         │  │
│  │ TreeMap<    │  │ Validation  │  │  ArrayList<Trade>       │  │
│  │ Price,      │  │ Rules       │  │                         │  │
│  │ PriceLevel> │  │             │  │  AtomicLong generators  │  │
│  │             │  │  usedOrderIds│  │  (orderId, tradeId,    │  │
│  │ HashMap<    │  │             │  │   arrivalSeq, execSeq)  │  │
│  │ ID, Ref>    │  │             │  │                         │  │
│  └──────┬──────┘  └──────┬──────┘  └─────────────────────────┘  │
│         │                │                                      │
│         ▼                ▼                                      │
│  ┌─────────────────────────────────────────┐                   │
│  │           Order Lifecycle               │                   │
│  │  NEW → OPEN → PARTIALLY_FILLED → FILLED │                   │
│  │                    ↘ CANCELLED           │                   │
│  │                    ↘ REJECTED            │                   │
│  │                    ↘ EXPIRED             │                   │
│  └─────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        OrderBook                                │
│  ┌─────────────────────┐  ┌─────────────────────────────────┐  │
│  │   Buy Side          │  │   Sell Side                     │  │
│  │   TreeMap (DESC)    │  │   TreeMap (ASC)                 │  │
│  │   Price → PriceLevel│  │   Price → PriceLevel            │  │
│  └─────────┬───────────┘  └──────────────┬─────────────────┘  │
│            │                              │                    │
│            ▼                              ▼                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              PriceLevel (per price)                     │   │
│  │  ArrayDeque<Order>  +  Intrusive Doubly-Linked List    │   │
│  │  - FIFO queue for time priority                         │   │
│  │  - O(1) removal via OrderNode.prev/next                │   │
│  │  - totalQuantity cache for depth queries               │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. MatchingEngine (Central Coordinator)

**Responsibilities:**
- Order submission (limit & market)
- Order matching orchestration
- Order cancellation & modification
- Trade generation & history
- Market data queries
- Invariant validation

**Key Design Decisions:**
- Single-threaded for determinism (no locks needed)
- All validation delegated to OrderValidator
- All book operations delegated to OrderBook
- Generators use AtomicLong for potential future thread-safety

### 2. OrderBook (Price Level Management)

**Data Structures:**
```java
// Buy side: highest price first (reverse order)
private final TreeMap<Long, PriceLevel> buyLevels = new TreeMap<>(Comparator.reverseOrder());

// Sell side: lowest price first (natural order)
private final TreeMap<Long, PriceLevel> sellLevels = new TreeMap<>();

// O(1) order lookup by ID
private final HashMap<Long, OrderReference> orderLookup = new HashMap<>();
```

**Operations:**
- `addOrder(Order)` - Insert into appropriate price level
- `cancelOrder(id)` - O(1) removal via HashMap + intrusive DLL
- `takeFirstOrderAtBestPrice(side)` - Dequeue from best price level
- `reAddOrderToFront(order)` - Re-queue after partial fill (preserves priority)
- `getBestBid()/getBestAsk()` - O(1) via TreeMap.firstKey()/lastKey()
- `getMarketDepth(side, depth)` - Snapshot of top N price levels

### 3. PriceLevel (FIFO Queue with O(1) Removal)

**Structure:**
```java
class PriceLevel {
    private final ArrayDeque<Order> queue = new ArrayDeque<>();
    private long totalQuantity = 0;  // Cached sum for depth queries
    
    // Intrusive doubly-linked list for O(1) removal
    // Each Order has transient OrderNode with prev/next pointers
}
```

**Why ArrayDeque + Intrusive DLL?**
- ArrayDeque: O(1) add/remove at both ends, cache-friendly
- Intrusive DLL: O(1) removal from middle without searching
- Each Order carries its own `OrderNode` (transient, not serialized)

### 4. Order (Domain Model)

**Immutability Strategy:**
- Core fields final (id, side, type, originalQty, arrivalSeq, timeInForce)
- Mutable state: remainingQty, filledQty, status, price (for modifications)
- Modification creates new Order via `withPrice()`, `withQuantity()`, `withNewIdentity()`

**Lifecycle States:**
```
NEW → OPEN → PARTIALLY_FILLED → FILLED
              ↘ CANCELLED
              ↘ REJECTED
              ↘ EXPIRED
```

**Key Methods:**
- `fill(qty)` - Updates filled/remaining, transitions status
- `cancel()` - Marks CANCELLED
- `expire()` - Marks EXPIRED (IOC/FOK remainder)
- `reject()` - Marks REJECTED (validation failure)
- `open()` - Marks OPEN (ready for matching)
- `withPrice()/withQuantity()` - Returns new Order for modifications

### 5. OrderValidator (Pre-submission Checks)

**Validation Categories:**
1. **New Order**: ID uniqueness, field constraints, TIF compatibility
2. **Cancellation**: Order exists, not terminal
3. **Modification**: Order exists, active, valid changes, qty ≥ filled
4. **Market Order**: No price, valid TIF (IOC only, no FOK/GTC)

**Used Order ID Tracking:**
- HashSet of all submitted order IDs
- Prevents duplicate IDs across engine lifetime
- Cleared on engine.reset()

### 6. Trade (Execution Record)

**Immutable Fields:**
- tradeId, buyOrderId, sellOrderId
- executionPrice (resting order's price - maker-taker model)
- executionQuantity
- executionSequence (global ordering)

## Data Flow

### Limit Order Submission
```
submitLimitOrder(side, price, qty, TIF)
    │
    ├─► Generate IDs (orderId, arrivalSeq)
    │
    ├─► Create Order(NEW)
    │
    ├─► validator.validateNewOrder(order)
    │       └─► Reject if invalid → order.reject() → throw
    │
    ├─► order.open() → OPEN
    │
    ├─► matchLimitOrder(order)
    │       │
    │       ├─► While canMatch & order.active:
    │       │       ├─► takeFirstOrderAtBestPrice(oppositeSide)
    │       │       ├─► executeTrade(incoming, resting)
    │       │       │       ├─► tradePrice = resting.price
    │       │       │       ├─► incoming.fill(qty)
    │       │       │       ├─► resting.fill(qty)
    │       │       │       ├─► Create Trade
    │       │       │       └─► Add to history
    │       │       └─► If resting.active → reAddOrderToFront(resting)
    │       │
    │       └─► Post-match TIF handling:
    │               GTC → addOrder(remaining)
    │               IOC → expire()
    │               FOK → if not full: rollback trades, cancel()
    │
    └─► Return order
```

### Market Order Submission
```
submitMarketOrder(side, qty, TIF)
    │
    ├─► Validate TIF != GTC
    │
    ├─► Create Order(MARKET, price=0)
    │
    ├─► validator.validateMarketOrder(order)
    │
    ├─► order.open()
    │
    ├─► matchMarketOrder(order)
    │       │
    │       └─► While order.active:
    │               ├─► bestPrice = getBestPrice(oppositeSide)
    │               ├─► Check price protection
    │               ├─► takeFirstOrderAtBestPrice(oppositeSide)
    │               ├─► executeTrade(market, resting)
    │               └─► If resting.active → reAddOrderToFront(resting)
    │
    └─► Post-match: expire remainder (IOC)
```

### Order Modification
```
modifyOrder(id, newPrice, newQty)
    │
    ├─► Get OrderReference from book
    │
    ├─► validator.validateModification(id, ref, newPrice, newQty)
    │
    ├─► Determine priority loss:
    │       priceChange = newPrice > 0 && newPrice != order.price
    │       qtyIncrease = newQty > 0 && newQty > order.originalQty
    │       losesPriority = priceChange || qtyIncrease
    │
    ├─► Create modified order:
    │       newId = nextId()
    │       newSeq = losesPriority ? nextSeq() : originalSeq
    │       Apply changes via withPrice()/withQuantity()
    │
    ├─► Remove original: cancelOrder(id), original.cancel()
    │
    ├─► Add modified: if NEW → open(), addOrder()
    │
    ├─► validator.markOrderIdUsed(newId)
    │
    └─► Return modified order
```

## Concurrency Model

**Current: Single-Threaded**
- All operations sequential
- Deterministic execution order
- No locks, no CAS loops
- Simple reasoning about invariants

**Future: Multi-Threaded Options**
1. **Single Writer, Multiple Readers**: RWLock on OrderBook
2. **Disruptor Pattern**: Ring buffer for order submission
3. **Partitioned Books**: Shard by symbol/instrument
4. **Lock-Free**: Compare-and-swap on order nodes (complex)

## Error Handling

**Validation Errors**: `IllegalArgumentException` with descriptive message
- Thrown before any state mutation
- Order status set to REJECTED

**Runtime Errors**: `IllegalStateException` for invariant violations
- Should never occur in correct implementation
- Caught by `validateInvariants()` in tests

## Extensibility Points

1. **New Order Types**: Add to OrderType enum, extend validator/matcher
2. **New TIF Values**: Add to TimeInForce, implement in match logic
3. **Price Protection**: Implement `isMarketOrderPriceProtected()`
4. **Fees/Rebates**: Add to Trade, calculate in executeTrade()
5. **Multiple Instruments**: Wrap OrderBook per symbol
6. **Persistence**: Add event sourcing on tradeHistory mutations

## Invariants (Validated by `validateInvariants()`)

1. No negative remaining quantity
2. No active order with zero remaining
3. Every active order in lookup map
4. Lookup map points to correct order
5. No empty price levels in TreeMap
6. Buy levels sorted descending
7. Sell levels sorted ascending
8. FIFO ordering within price level
9. Filled orders not in book
10. Cancelled orders not in book
11. Filled qty ≤ original qty
12. Sum of queue quantities = sum of remaining quantities

## Testing Strategy

- **Unit Tests**: Each component in isolation (75 tests)
- **Integration Tests**: Full engine workflows
- **Property Tests**: Invariant validation after random operations
- **Benchmarks**: JMH for performance regression detection