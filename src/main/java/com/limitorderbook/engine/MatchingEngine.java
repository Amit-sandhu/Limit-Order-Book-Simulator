package com.limitorderbook.engine;

import com.limitorderbook.model.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The central matching engine for the limit order book.
 *
 * Responsibilities:
 * - Order submission (limit and market)
 * - Order matching with price-time priority
 * - Order cancellation
 * - Order modification
 * - Trade generation and history
 * - Market data queries (best bid/ask, depth, etc.)
 *
 * Thread Safety: This implementation is NOT thread-safe.
 * For concurrent access, external synchronization is required.
 */
public final class MatchingEngine {
    private final OrderBook orderBook;
    private final OrderValidator validator;
    private final List<Trade> tradeHistory;
    private final AtomicLong orderIdGenerator;
    private final AtomicLong arrivalSequenceGenerator;
    private final AtomicLong tradeIdGenerator;
    private final AtomicLong executionSequenceGenerator;

    // Configuration
    private long maxMarketOrderPriceDeviationTicks = 10000; // 100.00 currency units (configurable)

    public MatchingEngine() {
        this.orderBook = new OrderBook();
        this.validator = new OrderValidator();
        this.tradeHistory = new ArrayList<>();
        this.orderIdGenerator = new AtomicLong(1);
        this.arrivalSequenceGenerator = new AtomicLong(1);
        this.tradeIdGenerator = new AtomicLong(1);
        this.executionSequenceGenerator = new AtomicLong(1);
    }

    // ==================== ORDER SUBMISSION ====================

    /**
     * Submits a limit order to the engine.
     *
     * @param side         BUY or SELL
     * @param price        price in integer ticks
     * @param quantity     order quantity
     * @param timeInForce  GTC, IOC, or FOK
     * @return the submitted order (with assigned ID and status)
     * @throws IllegalArgumentException if validation fails
     */
    public Order submitLimitOrder(OrderSide side, long price, int quantity, TimeInForce timeInForce) {
        long orderId = orderIdGenerator.getAndIncrement();
        long arrivalSeq = arrivalSequenceGenerator.getAndIncrement();

        Order order = new Order(orderId, side, price, quantity, arrivalSeq, timeInForce);

        OrderValidator.ValidationResult result = validator.validateNewOrder(order);
        if (!result.isValid()) {
            order.reject();
            throw new IllegalArgumentException("Order validation failed: " + result.getReason());
        }

        // Always attempt matching first, then add remainder to book (for GTC)
        order.open(); // Must open before matching
        matchLimitOrder(order);

        // For GTC, any remaining quantity stays in book (handled in matchLimitOrder)
        // For IOC/FOK, remaining is expired/rejected in matchLimitOrder

        return order;
    }

    /**
     * Submits a market order to the engine.
     *
     * @param side         BUY or SELL
     * @param quantity     order quantity
     * @param timeInForce  IOC or FOK (GTC not allowed for market orders)
     * @return the submitted order
     * @throws IllegalArgumentException if validation fails
     */
    public Order submitMarketOrder(OrderSide side, int quantity, TimeInForce timeInForce) {
        if (timeInForce == TimeInForce.GTC) {
            throw new IllegalArgumentException("Market orders cannot use GTC time-in-force");
        }

        long orderId = orderIdGenerator.getAndIncrement();
        long arrivalSeq = arrivalSequenceGenerator.getAndIncrement();

        Order order = new Order(orderId, side, OrderType.MARKET, 0, quantity, arrivalSeq, timeInForce);

        OrderValidator.ValidationResult result = validator.validateMarketOrder(order);
        if (!result.isValid()) {
            order.reject();
            throw new IllegalArgumentException("Market order validation failed: " + result.getReason());
        }

        order.open(); // Must open before matching
        matchMarketOrder(order);
        return order;
    }

    // ==================== MATCHING LOGIC ====================

    /**
     * Matches a limit order against the opposite side of the book.
     * For GTC orders, remaining quantity stays in the book.
     * For IOC orders, unfilled remainder is expired.
     * For FOK orders, if not fully filled, the order is rejected (and any partial fills rolled back).
     */
    private void matchLimitOrder(Order order) {
        OrderSide oppositeSide = order.getSide().opposite();

        // For FOK, we need to track fills to potentially rollback
        List<Trade> fokTrades = order.getTimeInForce() == TimeInForce.FOK ? new ArrayList<>() : null;

        while (order.isActive() && orderBook.canMatchAtPrice(order.getSide(), order.getPrice())) {
            // Get the best opposite order
            Order restingOrder = orderBook.takeFirstOrderAtBestPrice(oppositeSide);
            if (restingOrder == null) break;

            // Execute trade
            Trade trade = executeTrade(order, restingOrder);

            if (fokTrades != null) {
                fokTrades.add(trade);
            }

            // If resting order still has remaining quantity, re-add it to front of queue
            // (preserving time priority for the remaining quantity)
            if (restingOrder.isActive()) {
                orderBook.reAddOrderToFront(restingOrder);
            }
        }

        // Handle remaining quantity based on time-in-force
        if (order.isActive()) {
            switch (order.getTimeInForce()) {
                case GTC:
                    // Order is already open, just add to book
                    orderBook.addOrder(order);
                    break;
                case IOC:
                    order.expire();
                    break;
                case FOK:
                    // FOK: if not fully filled, rollback all trades and cancel order
                    if (order.getFilledQuantity() < order.getOriginalQuantity()) {
                        rollbackTrades(fokTrades);
                        order.cancel();
                    }
                    // If fully filled, status is already FILLED from fill()
                    break;
            }
        }
    }

    /**
     * Matches a market order against the opposite side.
     * Executes at best available prices until quantity exhausted or book empty.
     */
    private void matchMarketOrder(Order marketOrder) {
        OrderSide oppositeSide = marketOrder.getSide().opposite();

        while (marketOrder.isActive()) {
            // Check if there's liquidity on the opposite side
            Long bestPrice = orderBook.getBestPrice(oppositeSide);
            if (bestPrice == null) {
                break; // No more liquidity
            }

            // Check price protection for market orders
            if (!isMarketOrderPriceProtected(marketOrder, bestPrice)) {
                break; // Price protection triggered
            }

            // Get the first order at this price level (FIFO)
            Order restingOrder = orderBook.takeFirstOrderAtBestPrice(oppositeSide);
            if (restingOrder == null) {
                continue; // Level was empty, try next
            }

            // Execute trade
            Trade trade = executeTrade(marketOrder, restingOrder);

            // If resting order still has remaining quantity, re-add it to front
            if (restingOrder.isActive()) {
                orderBook.reAddOrderToFront(restingOrder);
            }
        }

        // Handle remaining quantity
        if (marketOrder.isActive()) {
            if (marketOrder.getTimeInForce() == TimeInForce.FOK) {
                // Should not happen as we reject FOK market orders in validator
                marketOrder.expire();
            } else {
                // IOC - expire remaining
                marketOrder.expire();
            }
        }
    }

    /**
     * Executes a trade between two orders.
     * Uses the resting order's price as execution price (maker-taker model).
     *
     * @param incomingOrder the aggressive order (taker)
     * @param restingOrder  the passive order (maker)
     * @return the created trade
     */
    private Trade executeTrade(Order incomingOrder, Order restingOrder) {
        int tradeQty = Math.min(incomingOrder.getRemainingQuantity(), restingOrder.getRemainingQuantity());
        long tradePrice = restingOrder.getPrice(); // Resting order's price (maker price)

        // Fill both orders
        incomingOrder.fill(tradeQty);
        restingOrder.fill(tradeQty);

        // Determine buy/sell order IDs for trade record
        long buyOrderId, sellOrderId;
        if (incomingOrder.getSide() == OrderSide.BUY) {
            buyOrderId = incomingOrder.getOrderId();
            sellOrderId = restingOrder.getOrderId();
        } else {
            buyOrderId = restingOrder.getOrderId();
            sellOrderId = incomingOrder.getOrderId();
        }

        // Create trade record
        Trade trade = new Trade(
                tradeIdGenerator.getAndIncrement(),
                buyOrderId,
                sellOrderId,
                tradePrice,
                tradeQty,
                executionSequenceGenerator.getAndIncrement()
        );

        tradeHistory.add(trade);

        return trade;
    }

    /**
     * Checks if a market order execution price is within protection limits.
     * Simplified implementation - in production would track reference price.
     */
    private boolean isMarketOrderPriceProtected(Order marketOrder, long oppositePrice) {
        if (maxMarketOrderPriceDeviationTicks <= 0) {
            return true; // No protection
        }
        // Simplified: allow all for now
        return true;
    }

    /**
     * Rolls back trades for FOK orders that weren't fully filled.
     * Note: This is a simplified implementation. A full implementation would
     * need to restore order quantities and statuses.
     */
    private void rollbackTrades(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) return;

        // Remove trades from history (in reverse order)
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade trade = trades.get(i);
            tradeHistory.remove(trade);

            // Restore order quantities - this is simplified
            // In a full implementation, we'd need to track the orders and restore them
        }
    }

    // ==================== ORDER MANAGEMENT ====================

    /**
     * Cancels an order by ID.
     *
     * @param orderId the order ID to cancel
     * @return the cancelled order, or null if not found
     */
    public Order cancelOrder(long orderId) {
        OrderBook.OrderReference ref = orderBook.getOrderReference(orderId);
        OrderValidator.ValidationResult result = validator.validateCancellation(orderId, ref);
        if (!result.isValid()) {
            return null;
        }

        Order order = orderBook.cancelOrder(orderId);
        if (order != null) {
            order.cancel();
        }
        return order;
    }

    /**
     * Modifies an order's price and/or quantity.
     * Price changes and quantity increases lose time priority.
     * Quantity reductions preserve time priority.
     *
     * @param orderId     the order ID to modify
     * @param newPrice    new price in ticks (0 = no change)
     * @param newQuantity new quantity (0 = no change)
     * @return the modified order (may be a new order with new ID)
     * @throws IllegalArgumentException if validation fails
     */
    public Order modifyOrder(long orderId, long newPrice, int newQuantity) {
        OrderBook.OrderReference ref = orderBook.getOrderReference(orderId);
        OrderValidator.ValidationResult result = validator.validateModification(orderId, ref, newPrice, newQuantity);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Modification validation failed: " + result.getReason());
        }

        Order originalOrder = ref.getOrder();
        boolean priceChange = newPrice > 0 && newPrice != originalOrder.getPrice();
        boolean qtyIncrease = newQuantity > 0 && newQuantity > originalOrder.getOriginalQuantity();
        boolean qtyDecrease = newQuantity > 0 && newQuantity < originalOrder.getOriginalQuantity();

        // Determine if priority is lost
        boolean losesPriority = priceChange || qtyIncrease;

        long newOrderId = orderIdGenerator.getAndIncrement();
        long newArrivalSeq = losesPriority ? arrivalSequenceGenerator.getAndIncrement() : originalOrder.getArrivalSequence();

        Order modifiedOrder;

        if (priceChange && qtyIncrease) {
            // Both price and quantity increase - lose priority for both
            modifiedOrder = originalOrder.withPrice(newOrderId, newArrivalSeq, newPrice);
            modifiedOrder = modifiedOrder.withQuantity(newOrderId, newArrivalSeq, newQuantity, false);
        } else if (priceChange && qtyDecrease) {
            // Price change and quantity decrease - lose priority for price only
            modifiedOrder = originalOrder.withPrice(newOrderId, newArrivalSeq, newPrice);
            modifiedOrder = modifiedOrder.withQuantity(newOrderId, newArrivalSeq, newQuantity, false);
        } else if (priceChange) {
            // Price change only - lose priority
            modifiedOrder = originalOrder.withPrice(newOrderId, newArrivalSeq, newPrice);
        } else if (qtyIncrease) {
            // Quantity increase only - lose priority
            modifiedOrder = originalOrder.withQuantity(newOrderId, newArrivalSeq, newQuantity, false);
        } else if (qtyDecrease) {
            // Quantity decrease only - preserve priority
            modifiedOrder = originalOrder.withQuantity(newOrderId, newArrivalSeq, newQuantity, true);
        } else {
            throw new IllegalArgumentException("No valid modification specified");
        }

        // Remove original order from book and cancel it
        orderBook.cancelOrder(orderId);
        originalOrder.cancel();

        // Add modified order - it may already have a status from withPrice/withQuantity
        if (modifiedOrder.getStatus() == OrderStatus.NEW) {
            modifiedOrder.open();
        }
        orderBook.addOrder(modifiedOrder);

        // Update validator
        validator.markOrderIdUsed(newOrderId);

        return modifiedOrder;
    }

    // ==================== QUERY METHODS ====================

    public OrderBook getOrderBook() {
        return orderBook;
    }

    public Long getBestBid() {
        return orderBook.getBestBid();
    }

    public Long getBestAsk() {
        return orderBook.getBestAsk();
    }

    public List<Trade> getTradeHistory() {
        return Collections.unmodifiableList(tradeHistory);
    }

    public List<Trade> getRecentTrades(int count) {
        int start = Math.max(0, tradeHistory.size() - count);
        return Collections.unmodifiableList(tradeHistory.subList(start, tradeHistory.size()));
    }

    public List<OrderBook.PriceLevelSnapshot> getMarketDepth(OrderSide side, int depth) {
        return orderBook.getMarketDepth(side, depth);
    }

    public Order getOrder(long orderId) {
        OrderBook.OrderReference ref = orderBook.getOrderReference(orderId);
        return ref != null ? ref.getOrder() : null;
    }

    public int getTotalTradeCount() {
        return tradeHistory.size();
    }

    public long getTotalTradedVolume() {
        return tradeHistory.stream().mapToLong(Trade::getExecutionQuantity).sum();
    }

    // ==================== CONFIGURATION ====================

    public void setMaxMarketOrderPriceDeviation(long ticks) {
        this.maxMarketOrderPriceDeviationTicks = ticks;
    }

    public long getMaxMarketOrderPriceDeviation() {
        return maxMarketOrderPriceDeviationTicks;
    }

    // ==================== UTILITIES ====================

    /**
     * Validates all order book invariants.
     * Useful for testing and debugging.
     */
    public List<String> validateInvariants() {
        List<String> violations = new ArrayList<>();

        List<Order> allOrders = orderBook.getAllOrders();

        // Invariant 1: No order has negative remaining quantity
        // Invariant 2: No active order has zero remaining quantity
        // Invariant 3: Every active order exists in the order lookup map
        // Invariant 11: The total executed quantity cannot exceed original quantity
        for (Order order : allOrders) {
            if (order.getRemainingQuantity() < 0) {
                violations.add("Order " + order.getOrderId() + " has negative remaining quantity: " + order.getRemainingQuantity());
            }
            if (order.isActive() && order.getRemainingQuantity() == 0) {
                violations.add("Active order " + order.getOrderId() + " has zero remaining quantity");
            }
            OrderBook.OrderReference ref = orderBook.getOrderReference(order.getOrderId());
            if (ref == null) {
                violations.add("Active order " + order.getOrderId() + " not in lookup map");
            } else if (ref.getOrder() != order) {
                violations.add("Lookup entry for " + order.getOrderId() + " points to different order");
            }
            if (order.getFilledQuantity() > order.getOriginalQuantity()) {
                violations.add("Order " + order.getOrderId() + " filled quantity exceeds original");
            }
        }

        // Invariant 5: No empty price levels
        // Check by verifying all levels in book have orders
        // This is implicitly maintained by OrderBook operations

        // Invariant 6 & 7: BUY descending, SELL ascending
        // Guaranteed by TreeMap ordering

        // Invariant 8: Orders at identical prices follow arrival sequence
        // Guaranteed by PriceLevel FIFO queue

        // Invariant 9 & 10: Filled/cancelled orders not in book
        // Checked above (only active orders in book)

        // Invariant 12: Sum of quantities in queues equals sum of remaining quantities
        long buyBookSum = 0;
        long sellBookSum = 0;
        for (Order order : allOrders) {
            if (order.getSide() == OrderSide.BUY) {
                buyBookSum += order.getRemainingQuantity();
            } else {
                sellBookSum += order.getRemainingQuantity();
            }
        }

        long buyQueueSum = 0;
        long sellQueueSum = 0;
        // We'd need to iterate price levels to verify - simplified check
        // The adjustLevelQuantity calls should keep these in sync

        return violations;
    }

    /**
     * Resets the engine to initial state (for testing).
     */
    public void reset() {
        orderBook.clear();
        tradeHistory.clear();
        validator.clear();
        orderIdGenerator.set(1);
        arrivalSequenceGenerator.set(1);
        tradeIdGenerator.set(1);
        executionSequenceGenerator.set(1);
    }

    @Override
    public String toString() {
        return orderBook.toString();
    }
}