package com.limitorderbook.model;

/**
 * Represents an executed trade between a buy order and a sell order.
 *
 * Immutable after construction.
 * Trade ID and execution sequence are assigned by the matching engine.
 * Execution price follows the resting order's price (maker-taker model).
 */
public final class Trade {
    private final long tradeId;
    private final long buyOrderId;
    private final long sellOrderId;
    private final long executionPrice; // Price in integer ticks
    private final int executionQuantity;
    private final long executionSequence; // Global execution sequence for ordering

    /**
     * Creates a new trade.
     *
     * @param tradeId           unique trade identifier
     * @param buyOrderId        ID of the buy order
     * @param sellOrderId       ID of the sell order
     * @param executionPrice    execution price in integer ticks (resting order's price)
     * @param executionQuantity executed quantity (must be > 0)
     * @param executionSequence global execution sequence number
     * @throws IllegalArgumentException if validation fails
     */
    public Trade(long tradeId, long buyOrderId, long sellOrderId,
                 long executionPrice, int executionQuantity, long executionSequence) {
        if (tradeId <= 0) {
            throw new IllegalArgumentException("Trade ID must be positive");
        }
        if (buyOrderId <= 0) {
            throw new IllegalArgumentException("Buy order ID must be positive");
        }
        if (sellOrderId <= 0) {
            throw new IllegalArgumentException("Sell order ID must be positive");
        }
        if (executionPrice <= 0) {
            throw new IllegalArgumentException("Execution price must be positive");
        }
        if (executionQuantity <= 0) {
            throw new IllegalArgumentException("Execution quantity must be positive");
        }
        if (executionSequence < 0) {
            throw new IllegalArgumentException("Execution sequence cannot be negative");
        }

        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.executionPrice = executionPrice;
        this.executionQuantity = executionQuantity;
        this.executionSequence = executionSequence;
    }

    public long getTradeId() {
        return tradeId;
    }

    public long getBuyOrderId() {
        return buyOrderId;
    }

    public long getSellOrderId() {
        return sellOrderId;
    }

    public long getExecutionPrice() {
        return executionPrice;
    }

    public int getExecutionQuantity() {
        return executionQuantity;
    }

    public long getExecutionSequence() {
        return executionSequence;
    }

    /**
     * Gets the execution price as a double (for display purposes).
     * Assumes 100 ticks = 1 currency unit.
     */
    public double getExecutionPriceAsDouble() {
        return executionPrice / 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return tradeId == trade.tradeId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(tradeId);
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, buyId=%d, sellId=%d, price=%d, qty=%d, seq=%d}",
                tradeId, buyOrderId, sellOrderId, executionPrice, executionQuantity, executionSequence);
    }
}