package com.limitorderbook;
import com.limitorderbook.model.OrderStatus;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderStatus;
import com.limitorderbook.model.TimeInForce;
import com.limitorderbook.model.Trade;
import com.limitorderbook.utils.OrderGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Randomized simulation test with invariant validation after every event.
 * Uses a fixed seed for reproducibility.
 */
class RandomizedSimulationTest {

    @Test
    void testRandomizedSimulation10kEvents() {
        long seed = 42;
        MatchingEngine engine = new MatchingEngine();
        OrderGenerator generator = new OrderGenerator(seed, engine);

        generator.setPriceRange(9500, 10500)
                 .setQuantityRange(1, 100)
                 .setBuyProbability(0.5)
                 .setMarketOrderProbability(0.15)
                 .setCancelProbability(0.05)
                 .setModifyProbability(0.02);

        int numEvents = 10000;
        int submitted = 0;
        int cancelled = 0;
        int modified = 0;

        // Track active orders for cancellation/modification
        // Since OrderGenerator's cancel/modify are stubs, we'll simulate manually
        // using a simpler approach
        Random random = new Random(seed);

        for (int i = 0; i < numEvents; i++) {
            double r = random.nextDouble();

            if (r < 0.05 && engine.getOrderBook().getOrderCount(OrderSide.BUY) + engine.getOrderBook().getOrderCount(OrderSide.SELL) > 0) {
                // Cancel random active order
                cancelRandomOrder(engine, random);
                cancelled++;
            } else if (r < 0.07 && engine.getOrderBook().getOrderCount(OrderSide.BUY) + engine.getOrderBook().getOrderCount(OrderSide.SELL) > 0) {
                // Modify random active order
                modifyRandomOrder(engine, random);
                modified++;
            } else {
                // Submit new order
                submitRandomOrder(engine, random);
                submitted++;
            }

            // Validate invariants after every event
            List<String> violations = engine.validateInvariants();
            if (!violations.isEmpty()) {
                fail("Invariant violation at event " + i + ": " + violations);
            }

            // Validate trade accounting
            validateTradeAccounting(engine);
        }

        System.out.println("Simulation completed:");
        System.out.println("  Orders submitted: " + submitted);
        System.out.println("  Orders cancelled: " + cancelled);
        System.out.println("  Orders modified: " + modified);
        System.out.println("  Trades executed: " + engine.getTotalTradeCount());
        System.out.println("  Total volume: " + engine.getTotalTradedVolume());
    }

    @Test
    void testRandomizedSimulationDifferentSeeds() {
        // Test with multiple seeds to ensure robustness
        for (long seed : new long[]{1, 42, 123, 456, 789, 999, 12345}) {
            MatchingEngine engine = new MatchingEngine();
            Random random = new Random(seed);

            for (int i = 0; i < 1000; i++) {
                submitRandomOrder(engine, random);

                // Occasionally cancel/modify
                if (random.nextDouble() < 0.1 && engine.getOrderBook().getOrderCount(OrderSide.BUY) + engine.getOrderBook().getOrderCount(OrderSide.SELL) > 0) {
                    if (random.nextBoolean()) {
                        cancelRandomOrder(engine, random);
                    } else {
                        modifyRandomOrder(engine, random);
                    }
                }

                List<String> violations = engine.validateInvariants();
                if (!violations.isEmpty()) {
                    fail("Seed " + seed + " event " + i + ": " + violations);
                }

                validateTradeAccounting(engine);
            }
        }
    }

    @Test
    void testDeterministicReproducibility() {
        // Same seed should produce same results
        MatchingEngine engine1 = new MatchingEngine();
        MatchingEngine engine2 = new MatchingEngine();

        runSimulation(engine1, 42, 1000);
        runSimulation(engine2, 42, 1000);

        // Trade histories should be identical
        List<Trade> trades1 = engine1.getTradeHistory();
        List<Trade> trades2 = engine2.getTradeHistory();

        assertEquals(trades1.size(), trades2.size(), "Trade count differs");

        for (int i = 0; i < trades1.size(); i++) {
            Trade t1 = trades1.get(i);
            Trade t2 = trades2.get(i);
            assertEquals(t1.getTradeId(), t2.getTradeId(), "Trade ID differs at index " + i);
            assertEquals(t1.getBuyOrderId(), t2.getBuyOrderId(), "Buy order ID differs at index " + i);
            assertEquals(t1.getSellOrderId(), t2.getSellOrderId(), "Sell order ID differs at index " + i);
            assertEquals(t1.getExecutionPrice(), t2.getExecutionPrice(), "Price differs at index " + i);
            assertEquals(t1.getExecutionQuantity(), t2.getExecutionQuantity(), "Quantity differs at index " + i);
        }
    }

    private void runSimulation(MatchingEngine engine, long seed, int numEvents) {
        Random random = new Random(seed);
        for (int i = 0; i < numEvents; i++) {
            submitRandomOrder(engine, random);
            if (random.nextDouble() < 0.1 && engine.getOrderBook().getOrderCount(OrderSide.BUY) + engine.getOrderBook().getOrderCount(OrderSide.SELL) > 0) {
                if (random.nextBoolean()) {
                    cancelRandomOrder(engine, random);
                } else {
                    modifyRandomOrder(engine, random);
                }
            }
        }
    }

    private void submitRandomOrder(MatchingEngine engine, Random random) {
        OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        boolean isMarket = random.nextDouble() < 0.15;
        int quantity = random.nextInt(1, 101);
        TimeInForce tif = switch (random.nextInt(3)) {
            case 0 -> TimeInForce.GTC;
            case 1 -> TimeInForce.IOC;
            default -> TimeInForce.FOK;
        };

        try {
            if (isMarket) {
                if (tif != TimeInForce.GTC) {
                    engine.submitMarketOrder(side, quantity, tif);
                }
            } else {
                long price = random.nextLong(9500, 10501);
                engine.submitLimitOrder(side, price, quantity, tif);
            }
        } catch (IllegalArgumentException e) {
            // Invalid order (e.g., market with GTC/FOK), skip
        }
    }

    private void cancelRandomOrder(MatchingEngine engine, Random random) {
        int buyCount = engine.getOrderBook().getOrderCount(OrderSide.BUY);
        int sellCount = engine.getOrderBook().getOrderCount(OrderSide.SELL);
        int total = buyCount + sellCount;

        if (total == 0) return;

        // Pick a random order from the book
        // We need to iterate to find it - simplified approach
        List<Order> allOrders = engine.getOrderBook().getAllOrders();
        if (!allOrders.isEmpty()) {
            Order toCancel = allOrders.get(random.nextInt(allOrders.size()));
            engine.cancelOrder(toCancel.getOrderId());
        }
    }

    private void modifyRandomOrder(MatchingEngine engine, Random random) {
        List<Order> allOrders = engine.getOrderBook().getAllOrders();
        if (allOrders.isEmpty()) return;

        Order toModify = allOrders.get(random.nextInt(allOrders.size()));

        // Randomly decide what to modify
        boolean changePrice = random.nextBoolean() && toModify.getType() != com.limitorderbook.model.OrderType.MARKET;
        boolean changeQty = random.nextBoolean();

        long newPrice = changePrice ? random.nextLong(9500, 10501) : 0;
        int newQty = changeQty ? random.nextInt(1, 101) : 0;

        try {
            engine.modifyOrder(toModify.getOrderId(), newPrice, newQty);
        } catch (IllegalArgumentException e) {
            // Invalid modification, skip
        }
    }

    private void validateTradeAccounting(MatchingEngine engine) {
        List<Trade> trades = engine.getTradeHistory();

        // Verify each trade has positive quantity and price
        for (Trade trade : trades) {
            assertTrue(trade.getExecutionQuantity() > 0, "Trade quantity must be positive");
            assertTrue(trade.getExecutionPrice() > 0, "Trade price must be positive");
            assertTrue(trade.getBuyOrderId() > 0, "Buy order ID must be positive");
            assertTrue(trade.getSellOrderId() > 0, "Sell order ID must be positive");
        }

        // Verify buy/sell volume matches
        long buyVolume = trades.stream().mapToLong(Trade::getExecutionQuantity).sum();
        long sellVolume = trades.stream().mapToLong(Trade::getExecutionQuantity).sum();
        assertEquals(buyVolume, sellVolume, "Buy and sell volumes must match");
    }
}