package com.limitorderbook.model;

/**
 * Time-in-force policies for orders.
 * GTC (Good Till Cancelled): Order remains active until filled or cancelled.
 * IOC (Immediate Or Cancel): Order executes immediately, remaining quantity cancelled.
 * FOK (Fill Or Kill): Order must be filled completely immediately, otherwise cancelled.
 */
public enum TimeInForce {
    GTC,
    IOC,
    FOK;

    /**
     * Checks if this time-in-force allows partial fills to remain in the book.
     *
     * @return true for GTC, false for IOC and FOK
     */
    public boolean allowsResting() {
        return this == GTC;
    }

    /**
     * Checks if this time-in-force requires full immediate execution.
     *
     * @return true for FOK, false for GTC and IOC
     */
    public boolean requiresFullFill() {
        return this == FOK;
    }
}