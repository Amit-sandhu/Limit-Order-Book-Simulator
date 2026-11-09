package com.limitorderbook.utils;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderType;
import com.limitorderbook.model.TimeInForce;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Generates reproducible random orders for testing and simulation.
 * Uses a configurable seed for deterministic behavior.
 */
public final class OrderGenerator {

    private final Random random;
    private final MatchingEngine engine;
    private final Consumer<Order> orderConsumer;

    // Configuration
    private int minPrice = 9000;      // 90.00
    private int maxPrice = 11000;     // 110.00
    private int minQuantity = 1;
    private int maxQuantity = 100;
    private double buyProbability = 0.5;
    private double marketOrderProbability = 0.1;
    private double cancelProbability = 0.05;
    private double modifyProbability = 0.02;
    private long[] timeInForceWeights = {80, 10, 10}; // GTC, IOC, FOK percentages

    /**
     * Creates a new order generator with the given seed.
     *
     * @param seed   random seed for reproducibility
     * @param engine the matching engine to submit orders to
     */
    public OrderGenerator(long seed, MatchingEngine engine) {
        this.random = new Random(seed);
        this.engine = engine;
        this.orderConsumer = order -> engine.submitLimitOrder(order.getSide(), order.getPrice(), order.getOriginalQuantity(), order.getTimeInForce());
    }

    /**
     * Creates a new order generator with custom order consumer.
     *
     * @param seed          random seed
     * @param orderConsumer consumer for generated orders
     */
    public OrderGenerator(long seed, Consumer<Order> orderConsumer) {
        this.random = new Random(seed);
        this.engine = null;
        this.orderConsumer = orderConsumer;
    }

    // Configuration setters
    public OrderGenerator setPriceRange(int minPrice, int maxPrice) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        return this;
    }

    public OrderGenerator setQuantityRange(int minQuantity, int maxQuantity) {
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        return this;
    }

    public OrderGenerator setBuyProbability(double buyProbability) {
        this.buyProbability = Math.max(0, Math.min(1, buyProbability));
        return this;
    }

    public OrderGenerator setMarketOrderProbability(double prob) {
        this.marketOrderProbability = Math.max(0, Math.min(1, prob));
        return this;
    }

    public OrderGenerator setCancelProbability(double prob) {
        this.cancelProbability = Math.max(0, Math.min(1, prob));
        return this;
    }

    public OrderGenerator setModifyProbability(double prob) {
        this.modifyProbability = Math.max(0, Math.min(1, prob));
        return this;
    }

    public OrderGenerator setTimeInForceWeights(int gtcPct, int iocPct, int fokPct) {
        int total = gtcPct + iocPct + fokPct;
        if (total != 100) {
            throw new IllegalArgumentException("TimeInForce weights must sum to 100");
        }
        this.timeInForceWeights = new long[]{gtcPct, iocPct, fokPct};
        return this;
    }

    /**
     * Generates and submits a specified number of random orders.
     *
     * @param count number of orders to generate
     * @return number of orders successfully submitted
     */
    public int generateOrders(int count) {
        int submitted = 0;
        for (int i = 0; i < count; i++) {
            if (generateSingleOrder()) {
                submitted++;
            }
        }
        return submitted;
    }

    /**
     * Generates a single random order event (order, cancel, or modify).
     *
     * @return true if an order was submitted, false if cancel/modify
     */
    public boolean generateSingleOrder() {
        double r = random.nextDouble();

        if (r < cancelProbability && engine != null) {
            // Cancel random order
            cancelRandomOrder();
            return false;
        } else if (r < cancelProbability + modifyProbability && engine != null) {
            // Modify random order
            modifyRandomOrder();
            return false;
        } else {
            // Submit new order
            submitRandomOrder();
            return true;
        }
    }

    /**
     * Submits a random new order.
     */
    public Order submitRandomOrder() {
        OrderSide side = random.nextDouble() < buyProbability ? OrderSide.BUY : OrderSide.SELL;
        OrderType type = random.nextDouble() < marketOrderProbability ? OrderType.MARKET : OrderType.LIMIT;
        int quantity = random.nextInt(minQuantity, maxQuantity + 1);
        TimeInForce tif = type == OrderType.MARKET ? selectMarketTimeInForce() : selectTimeInForce();

        Order order;
        if (type == OrderType.MARKET) {
            if (engine != null) {
                order = engine.submitMarketOrder(side, quantity, tif);
            } else {
                // Can't create market order without engine for ID generation
                throw new UnsupportedOperationException("Market orders require engine reference");
            }
        } else {
            long price = random.nextLong(minPrice, maxPrice + 1);
            if (engine != null) {
                order = engine.submitLimitOrder(side, price, quantity, tif);
            } else {
                // Create order with dummy IDs for testing
                order = new Order(0, side, price, quantity, 0, tif);
            }
        }

        if (orderConsumer != null && engine == null) {
            orderConsumer.accept(order);
        }

        return order;
    }

    /**
     * Cancels a random active order.
     */
    public boolean cancelRandomOrder() {
        if (engine == null) return false;

        // Get all active orders from engine's order book
        // Since we can't directly access, we'll track submitted orders
        return false; // Simplified - would need order tracking
    }

    /**
     * Modifies a random active order.
     */
    public boolean modifyRandomOrder() {
        if (engine == null) return false;
        return false; // Simplified
    }

    private TimeInForce selectTimeInForce() {
        int r = random.nextInt(100);
        if (r < timeInForceWeights[0]) return TimeInForce.GTC;
        if (r < timeInForceWeights[0] + timeInForceWeights[1]) return TimeInForce.IOC;
        return TimeInForce.FOK;
    }

    private TimeInForce selectMarketTimeInForce() {
        // Market orders can only use IOC (not GTC or FOK)
        return TimeInForce.IOC;
    }

    /**
     * Generates a reproducible sequence of mixed events for simulation.
     *
     * @param numEvents      total number of events
     * @param eventConsumer  consumer for each generated event
     */
    public void generateMixedEvents(int numEvents, EventConsumer eventConsumer) {
        for (int i = 0; i < numEvents; i++) {
            double r = random.nextDouble();

            if (r < cancelProbability) {
                // Cancel event - need reference to existing order
                eventConsumer.onCancel(generateRandomOrderId());
            } else if (r < cancelProbability + modifyProbability) {
                // Modify event
                eventConsumer.onModify(generateRandomOrderId(),
                        random.nextBoolean() ? random.nextLong(minPrice, maxPrice + 1) : 0,
                        random.nextBoolean() ? random.nextInt(minQuantity, maxQuantity + 1) : 0);
            } else {
                // New order event
                OrderSide side = random.nextDouble() < buyProbability ? OrderSide.BUY : OrderSide.SELL;
                OrderType type = random.nextDouble() < marketOrderProbability ? OrderType.MARKET : OrderType.LIMIT;
                int quantity = random.nextInt(minQuantity, maxQuantity + 1);
                TimeInForce tif = type == OrderType.MARKET ? selectMarketTimeInForce() : selectTimeInForce();

                if (type == OrderType.LIMIT) {
                    long price = random.nextLong(minPrice, maxPrice + 1);
                    eventConsumer.onNewLimitOrder(side, price, quantity, tif);
                } else {
                    eventConsumer.onNewMarketOrder(side, quantity, tif);
                }
            }
        }
    }

    private long generateRandomOrderId() {
        // Generate a plausible order ID - in real use, would pick from active orders
        return random.nextLong(1, 10000);
    }

    /**
     * Interface for consuming generated events.
     * Not a @FunctionalInterface because it has multiple methods.
     */
    public interface EventConsumer {
        void onNewLimitOrder(OrderSide side, long price, int quantity, TimeInForce tif);
        void onNewMarketOrder(OrderSide side, int quantity, TimeInForce tif);
        void onCancel(long orderId);
        void onModify(long orderId, long newPrice, int newQuantity);
    }
}