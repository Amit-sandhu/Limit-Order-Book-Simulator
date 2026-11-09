package com.limitorderbook.model;

import java.util.Objects;

/**
 * Represents an individual order in the limit order book.
 *
 * Uses integer price ticks for precision (1 tick = 0.01 currency unit).
 * All quantities are in base units (e.g., shares).
 *
 * Immutable fields after construction: orderId, side, type, price, originalQuantity, arrivalSequence.
 * Mutable fields managed by the matching engine: remainingQuantity, status.
 */
public final class Order {
    private final long orderId;
    private final OrderSide side;
    private final OrderType type;
    private final long price; // Price in integer ticks (e.g., 10000 = 100.00)
    private final int originalQuantity;
    private int remainingQuantity;
    private final long arrivalSequence;
    private OrderStatus status;
    private final TimeInForce timeInForce;

    /**
     * Creates a new limit order.
     *
     * @param orderId           unique order identifier
     * @param side              BUY or SELL
     * @param price             price in integer ticks (must be > 0 for limit orders)
     * @param quantity          order quantity (must be > 0)
     * @param arrivalSequence   deterministic arrival sequence for time priority
     * @param timeInForce       time-in-force policy
     * @throws IllegalArgumentException if validation fails
     */
    public Order(long orderId, OrderSide side, long price, int quantity,
                 long arrivalSequence, TimeInForce timeInForce) {
        this(orderId, side, OrderType.LIMIT, price, quantity, arrivalSequence, timeInForce);
    }

    /**
     * Creates a new order (limit or market).
     *
     * @param orderId           unique order identifier
     * @param side              BUY or SELL
     * @param type              LIMIT or MARKET
     * @param price             price in integer ticks (must be > 0 for limit orders, ignored for market)
     * @param quantity          order quantity (must be > 0)
     * @param arrivalSequence   deterministic arrival sequence for time priority
     * @param timeInForce       time-in-force policy
     * @throws IllegalArgumentException if validation fails
     */
    public Order(long orderId, OrderSide side, OrderType type, long price, int quantity,
                 long arrivalSequence, TimeInForce timeInForce) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Order type cannot be null");
        }
        if (type == OrderType.LIMIT && price <= 0) {
            throw new IllegalArgumentException("Limit order price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive");
        }
        if (timeInForce == null) {
            throw new IllegalArgumentException("Time in force cannot be null");
        }
        // Market orders with FOK don't make sense (no price to match against)
        if (type == OrderType.MARKET && timeInForce == TimeInForce.FOK) {
            throw new IllegalArgumentException("Market orders cannot use FOK time-in-force");
        }

        this.orderId = orderId;
        this.side = side;
        this.type = type;
        this.price = type == OrderType.LIMIT ? price : 0;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.arrivalSequence = arrivalSequence;
        this.status = OrderStatus.NEW;
        this.timeInForce = timeInForce;
    }

    /**
     * Gets the unique order identifier.
     */
    public long getOrderId() {
        return orderId;
    }

    /**
     * Gets the order side (BUY/SELL).
     */
    public OrderSide getSide() {
        return side;
    }

    /**
     * Gets the order type (LIMIT/MARKET).
     */
    public OrderType getType() {
        return type;
    }

    /**
     * Gets the order price in integer ticks.
     * For market orders, returns 0.
     */
    public long getPrice() {
        return price;
    }

    /**
     * Gets the original order quantity.
     */
    public int getOriginalQuantity() {
        return originalQuantity;
    }

    /**
     * Gets the remaining (unfilled) quantity.
     */
    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    /**
     * Gets the filled quantity.
     */
    public int getFilledQuantity() {
        return originalQuantity - remainingQuantity;
    }

    /**
     * Gets the deterministic arrival sequence for time priority.
     */
    public long getArrivalSequence() {
        return arrivalSequence;
    }

    /**
     * Gets the current order status.
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Gets the time-in-force policy.
     */
    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    /**
     * Checks if this is a market order.
     */
    public boolean isMarketOrder() {
        return type == OrderType.MARKET;
    }

    /**
     * Checks if this is a limit order.
     */
    public boolean isLimitOrder() {
        return type == OrderType.LIMIT;
    }

    /**
     * Checks if the order is active (can be matched).
     */
    public boolean isActive() {
        return status.isActive();
    }

    /**
     * Checks if the order is in a terminal state.
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * Updates the remaining quantity after a fill.
     * Called by the matching engine.
     *
     * @param filledQuantity the quantity that was filled (must be > 0 and <= remainingQuantity)
     * @throws IllegalArgumentException if filledQuantity is invalid
     */
    public void fill(int filledQuantity) {
        if (filledQuantity <= 0) {
            throw new IllegalArgumentException("Filled quantity must be positive");
        }
        if (filledQuantity > remainingQuantity) {
            throw new IllegalArgumentException("Filled quantity cannot exceed remaining quantity");
        }
        if (!isActive()) {
            throw new IllegalStateException("Cannot fill order in terminal state: " + status);
        }

        this.remainingQuantity -= filledQuantity;
        if (this.remainingQuantity == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * Marks the order as open in the book.
     * Called by the matching engine when order is added to the book.
     */
    public void open() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Can only open NEW orders, current status: " + status);
        }
        this.status = OrderStatus.OPEN;
    }

    /**
     * Cancels the order.
     * Called by the matching engine on cancellation request.
     */
    public void cancel() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot cancel order in state: " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * Marks the order as expired (IOC/FOK unfilled remainder).
     * Called by the matching engine.
     */
    public void expire() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot expire order in state: " + status);
        }
        this.status = OrderStatus.EXPIRED;
    }

    /**
     * Marks the order as rejected.
     * Called by the validator/engine on validation failure.
     */
    public void reject() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Can only reject NEW orders, current status: " + status);
        }
        this.status = OrderStatus.REJECTED;
    }

    /**
     * Creates a copy of this order with a new order ID and arrival sequence.
     * Used for order modifications that lose time priority.
     */
    public Order withNewIdentity(long newOrderId, long newArrivalSequence) {
        Order newOrder = new Order(newOrderId, side, type, price, originalQuantity,
                                   newArrivalSequence, timeInForce);
        // Preserve filled quantity - directly set remaining to avoid status checks
        int filled = originalQuantity - remainingQuantity;
        if (filled > 0) {
            newOrder.remainingQuantity = newOrder.originalQuantity - filled;
            if (newOrder.remainingQuantity == 0) {
                newOrder.status = OrderStatus.FILLED;
            } else {
                newOrder.status = OrderStatus.PARTIALLY_FILLED;
            }
        }
        return newOrder;
    }

    /**
     * Creates a copy of this order with modified quantity.
     * Quantity reductions preserve time priority; increases lose it.
     */
    public Order withQuantity(long newOrderId, long newArrivalSequence, int newQuantity, boolean preservePriority) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("New quantity must be positive");
        }
        if (newQuantity < getFilledQuantity()) {
            throw new IllegalArgumentException("New quantity cannot be less than filled quantity");
        }

        Order newOrder = new Order(newOrderId, side, type, price, newQuantity,
                                   preservePriority ? arrivalSequence : newArrivalSequence,
                                   timeInForce);
        // Preserve filled quantity - directly set remaining
        int filled = getFilledQuantity();
        if (filled > 0) {
            newOrder.remainingQuantity = newOrder.originalQuantity - filled;
            if (newOrder.remainingQuantity == 0) {
                newOrder.status = OrderStatus.FILLED;
            } else {
                newOrder.status = OrderStatus.PARTIALLY_FILLED;
            }
        }
        return newOrder;
    }

    /**
     * Creates a copy of this order with modified price.
     * Price changes always lose time priority.
     */
    public Order withPrice(long newOrderId, long newArrivalSequence, long newPrice) {
        if (type == OrderType.MARKET) {
            throw new IllegalArgumentException("Cannot change price of market order");
        }
        if (newPrice <= 0) {
            throw new IllegalArgumentException("New price must be positive");
        }

        Order newOrder = new Order(newOrderId, side, type, newPrice, originalQuantity,
                                   newArrivalSequence, timeInForce);
        int filled = getFilledQuantity();
        if (filled > 0) {
            newOrder.remainingQuantity = newOrder.originalQuantity - filled;
            if (newOrder.remainingQuantity == 0) {
                newOrder.status = OrderStatus.FILLED;
            } else {
                newOrder.status = OrderStatus.PARTIALLY_FILLED;
            }
        }
        return newOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return orderId == order.orderId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, side=%s, type=%s, price=%d, origQty=%d, remQty=%d, seq=%d, status=%s, tif=%s}",
                orderId, side, type, price, originalQuantity, remainingQuantity, arrivalSequence, status, timeInForce);
    }
}