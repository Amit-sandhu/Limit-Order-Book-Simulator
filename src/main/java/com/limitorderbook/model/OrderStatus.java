package com.limitorderbook.model;

/**
 * Represents the lifecycle status of an order.
 */
public enum OrderStatus {
    /**
     * Order has been created but not yet submitted to the engine.
     */
    NEW,
    /**
     * Order is active in the order book with remaining quantity.
     */
    OPEN,
    /**
     * Order has been partially filled and remains active.
     */
    PARTIALLY_FILLED,
    /**
     * Order has been completely filled.
     */
    FILLED,
    /**
     * Order was cancelled by the user.
     */
    CANCELLED,
    /**
     * Order was rejected due to validation failure.
     */
    REJECTED,
    /**
     * Order expired due to time-in-force policy (IOC/FOK).
     */
    EXPIRED;

    /**
     * Checks if the order is in a terminal state (no longer active).
     *
     * @return true if the order cannot be modified or cancelled
     */
    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }

    /**
     * Checks if the order is active in the order book.
     *
     * @return true if the order can still be matched
     */
    public boolean isActive() {
        return this == OPEN || this == PARTIALLY_FILLED;
    }
}