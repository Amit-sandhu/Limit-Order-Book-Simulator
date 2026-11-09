# Matching Rules Specification

## Price-Time Priority

The matching engine implements strict **price-time priority** (also known as **price-FIFO**):

### Price Priority
- **Buy Orders**: Higher price has priority (willing to pay more)
- **Sell Orders**: Lower price has priority (willing to sell cheaper)
- Implemented via `TreeMap` ordering:
  - Buy side: `Comparator.reverseOrder()` (descending)
  - Sell side: Natural order (ascending)

### Time Priority
- Orders at the **same price** are matched in **FIFO order** (earliest arrival first)
- Implemented via `ArrayDeque` per price level
- Arrival sequence assigned at submission (`AtomicLong` generator)

## Matching Algorithm

### Limit Order Matching (`matchLimitOrder`)

```
Incoming Limit Order (aggressive/taker)
    │
    ▼
While incoming.isActive() AND book.canMatchAtPrice(incoming.side, incoming.price):
    │
    ▼
Resting Order = book.takeFirstOrderAtBestPrice(oppositeSide)
    │
    ▼
Execute Trade(incoming, resting)
    │
    ├─► Trade Price = resting.price (maker price)
    │
    ├─► Trade Qty = min(incoming.remaining, resting.remaining)
    │
    ├─► incoming.fill(tradeQty)
    │
    ├─► resting.fill(tradeQty)
    │
    ├─► Record Trade in history
    │
    ▼
If resting.isActive():
    book.reAddOrderToFront(resting)  // Preserve time priority for remainder
```

### Market Order Matching (`matchMarketOrder`)

```
Incoming Market Order (aggressive/taker)
    │
    ▼
While incoming.isActive():
    │
    ▼
Best Price = book.getBestPrice(oppositeSide)
    │
    ├─► If null → break (no liquidity)
    │
    ▼
If !isMarketOrderPriceProtected(incoming, bestPrice):
    break  // Price protection triggered
    │
    ▼
Resting Order = book.takeFirstOrderAtBestPrice(oppositeSide)
    │
    ▼
Execute Trade(incoming, resting)  // Same as limit matching
    │
    ▼
If resting.isActive():
    book.reAddOrderToFront(resting)
```

### Execution Price (Maker-Taker Model)

**The resting (passive) order's price is always used as the execution price.**

| Scenario | Incoming Order | Resting Order | Execution Price |
|----------|---------------|---------------|-----------------|
| Buy Limit crosses Sell | BUY 101.00 | SELL 100.00 | **100.00** (sell price) |
| Sell Limit crosses Buy | SELL 99.00 | BUY 100.00 | **100.00** (buy price) |
| Market Buy | MARKET | SELL 100.00 | **100.00** (sell price) |
| Market Sell | MARKET | BUY 100.00 | **100.00** (buy price) |

**Rationale**: The resting order provides liquidity (maker), the incoming order takes liquidity (taker). Maker sets the price.

## Can Match Logic (`OrderBook.canMatchAtPrice`)

```java
// BUY order can match if: bestAsk != null AND buyPrice >= bestAsk
// SELL order can match if: bestBid != null AND sellPrice <= bestBid

public boolean canMatchAtPrice(OrderSide side, long price) {
    if (side == OrderSide.BUY) {
        Long bestAsk = getBestAsk();
        return bestAsk != null && price >= bestAsk;
    } else {
        Long bestBid = getBestBid();
        return bestBid != null && price <= bestBid;
    }
}
```

## Time-in-Force Behavior

### GTC (Good Till Cancelled) - Default
- **Matching**: Executes against available liquidity
- **Remainder**: Added to book at limit price
- **Cancellation**: Explicit cancelOrder() required
- **Expiration**: Never expires automatically

### IOC (Immediate Or Cancel)
- **Matching**: Executes against available liquidity immediately
- **Remainder**: **Expired** (not added to book)
- **Status**: PARTIALLY_FILLED → EXPIRED (if partial), FILLED (if full)
- **Use Case**: "Fill what you can now, cancel the rest"

### FOK (Fill Or Kill)
- **Matching**: Attempts to fill **entire quantity** immediately
- **Success**: Fully filled → FILLED
- **Failure**: **All trades rolled back**, order **CANCELLED**
- **Rollback**: Removes trades from history, restores counterparty orders
- **Use Case**: "All or nothing" execution

**FOK Rollback Details:**
```
1. Track all trades during matching attempt
2. If order not fully filled after exhausting liquidity:
   a. For each trade (reverse order):
      - Remove from tradeHistory
      - Restore incoming order qty/status
      - Restore resting order qty/status
      - Re-add resting order to book front
   b. incoming.cancel()
3. If fully filled: trades stand, order FILLED
```

**Current Implementation Note**: Rollback is simplified - trade history is cleared but full order state restoration would require more tracking in production.

## Order Modification Priority Rules

| Modification Type | Priority | New Arrival Sequence |
|-------------------|----------|---------------------|
| Price change only | **Lost** | New (current time) |
| Quantity increase | **Lost** | New (current time) |
| Quantity decrease | **Preserved** | Original |
| Price change + qty decrease | **Lost** (price) | New (current time) |
| Price change + qty increase | **Lost** | New (current time) |

**Implementation:**
```java
boolean priceChange = newPrice > 0 && newPrice != originalPrice;
boolean qtyIncrease = newQty > 0 && newQty > originalQty;
boolean losesPriority = priceChange || qtyIncrease;

long newArrivalSeq = losesPriority ? arrivalSeqGenerator.getAndIncrement() 
                                   : originalOrder.getArrivalSequence();
```

## Price Representation

### Integer Ticks
- **1 tick = 0.01 currency units** (100 ticks = 1.00)
- **All prices stored as `long` (ticks)**
- **No floating-point arithmetic** - eliminates rounding errors

### Conversion
```java
// Ticks → Display
double price = ticks / 100.0;  // 10000 → 100.00

// Display → Ticks
long ticks = Math.round(price * 100);  // 100.00 → 10000
```

### Benefits
- Exact arithmetic for financial calculations
- Deterministic comparisons
- No precision loss in aggregation
- Natural ordering in TreeMap

## Market Order Price Protection

**Current**: Simplified - all market orders allowed (returns `true`)

**Production Implementation:**
```java
private boolean isMarketOrderPriceProtected(Order marketOrder, long oppositePrice) {
    if (maxMarketOrderPriceDeviationTicks <= 0) return true;
    
    long referencePrice = getReferencePrice(marketOrder.getSide());
    long deviation = marketOrder.getSide() == OrderSide.BUY
        ? oppositePrice - referencePrice
        : referencePrice - oppositePrice;
    
    return deviation <= maxMarketOrderPriceDeviationTicks;
}
```

**Configuration**: `engine.setMaxMarketOrderPriceDeviation(ticks)` (default: 10000 = 100.00)

## Partial Fill Handling

### Incoming Order Partially Filled
- Status: `PARTIALLY_FILLED`
- Remaining quantity stays active (GTC) or expires (IOC)
- Arrival sequence preserved for GTC remainder

### Resting Order Partially Filled
- Status: `PARTIALLY_FILLED`
- **Re-added to FRONT of same price level queue**
- Preserves time priority for remaining quantity
- Total quantity at price level updated

### Trade Generation
- One `Trade` record per matching pair
- Multiple trades possible per incoming order
- Each trade has unique `tradeId` and `executionSequence`

## Edge Cases

### Crossed Spread on Submission
- Limit order submitted with price that crosses spread
- Immediately matches against best opposite orders
- Executes at resting order prices (not incoming price)

### Empty Book
- Market orders: Expire immediately (IOC) or cancel (FOK)
- Limit orders: Added to book (GTC) or expire (IOC/FOK)

### Self-Match Prevention
- Not implemented (would require account/client ID on orders)
- In production: Check `incoming.clientId != resting.clientId`

### Large Order Matching
- Single incoming order can generate multiple trades
- Each trade matches against one resting order (or partial)
- Execution sequence ensures global ordering

## Determinism Guarantees

1. **Same inputs → Same outputs** (given same seed for OrderGenerator)
2. **No randomness** in matching logic
3. **Sequential processing** - no race conditions
4. **Arrival sequence** breaks all ties
5. **Reproducible benchmarks** - fixed iteration counts

## Validation Rules Summary

| Operation | Validation |
|-----------|------------|
| New Limit Order | price > 0, qty > 0, unique ID, valid TIF |
| New Market Order | price == 0, qty > 0, unique ID, TIF != GTC/FOK |
| Cancel Order | Order exists in book, not terminal |
| Modify Order | Order exists, active, newQty ≥ filledQty, changes specified |
| FOK Limit | If not fully fillable → rollback + cancel |

## Examples

### Example 1: Basic Cross
```
Book: SELL 100@100.00 (ID:1)
Incoming: BUY 100@100.00 (ID:2)
Result: Trade 100@100.00, both FILLED
```

### Example 2: Partial Fill with Remainder
```
Book: SELL 200@100.00 (ID:1)
Incoming: BUY 100@100.00 (ID:2, GTC)
Result: Trade 100@100.00
  - Order 1: PARTIALLY_FILLED (100 remaining), re-added to front
  - Order 2: FILLED
```

### Example 3: Price Improvement
```
Book: BUY 100@99.00 (ID:1), BUY 100@100.00 (ID:2)
Incoming: SELL 50@98.00 (ID:3)
Result: Trade 50@100.00 (matches ID:2 - best bid)
  - Order 2: PARTIALLY_FILLED (50 remaining), re-added to front
  - Order 3: PARTIALLY_FILLED (0 remaining? No, 50 filled of 50 = FILLED)
```

### Example 4: FOK Failure
```
Book: SELL 100@100.00 (ID:1)
Incoming: BUY 200@100.00 (ID:2, FOK)
Process:
  1. Match 100@100.00 with ID:1
  2. Book empty, incoming has 100 remaining
  3. FOK: Not fully filled → ROLLBACK
  4. Trade removed, ID:1 restored to book, ID:2 CANCELLED
Result: No trades, ID:1 OPEN, ID:2 CANCELLED
```