package com.limitorderbook.model;

/**
 * Represents the side of an order: BUY or SELL.
 */
public enum OrderSide {
    BUY,
    SELL;

    /**
     * Returns the opposite side.
     *
     * @return the opposite OrderSide
     */
    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}