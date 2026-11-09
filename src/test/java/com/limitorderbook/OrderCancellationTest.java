package com.limitorderbook;
import com.limitorderbook.model.OrderStatus;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderStatus;
import com.limitorderbook.model.TimeInForce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for order cancellation.
 */
class OrderCancellationTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void testCancelFirstOrderAtPriceLevel() {
        // Two sells at same price
        Order sell1 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        Order sell2 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Cancel first order
        Order cancelled = engine.cancelOrder(sell1.getOrderId());

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(sell1.getOrderId(), cancelled.getOrderId());

        // sell2 should still be in book
        assertEquals(OrderStatus.OPEN, sell2.getStatus());
    }

    @Test
    void testCancelMiddleOrderAtPriceLevel() {
        // Three sells at same price
        Order sell1 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        Order sell2 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        Order sell3 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Cancel middle order
        Order cancelled = engine.cancelOrder(sell2.getOrderId());

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        // sell1 and sell3 should remain
        assertEquals(OrderStatus.OPEN, sell1.getStatus());
        assertEquals(OrderStatus.OPEN, sell3.getStatus());
    }

    @Test
    void testCancelLastOrderAtPriceLevel() {
        Order sell1 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        Order sell2 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Cancel last order
        Order cancelled = engine.cancelOrder(sell2.getOrderId());

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        // sell1 should remain
        assertEquals(OrderStatus.OPEN, sell1.getStatus());
    }

    @Test
    void testCancelOnlyOrderAtPriceLevel() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Cancel the only order at this price level
        Order cancelled = engine.cancelOrder(sell.getOrderId());

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());

        // Price level should be removed, book empty on sell side
        assertNull(engine.getBestAsk());
    }

    @Test
    void testCancelPartiallyFilledOrder() {
        // Submit large sell
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Partially fill with buy
        engine.submitLimitOrder(OrderSide.BUY, 10000, 40, TimeInForce.GTC);

        // Sell should be partially filled
        assertEquals(OrderStatus.PARTIALLY_FILLED, sell.getStatus());
        assertEquals(60, sell.getRemainingQuantity());

        // Cancel the partially filled order
        Order cancelled = engine.cancelOrder(sell.getOrderId());

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void testCancelUnknownOrder() {
        Order cancelled = engine.cancelOrder(99999L);
        assertNull(cancelled);
    }

    @Test
    void testCancelAlreadyCancelledOrder() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Cancel once
        engine.cancelOrder(sell.getOrderId());

        // Try to cancel again
        Order cancelled = engine.cancelOrder(sell.getOrderId());
        assertNull(cancelled); // Should return null (not found)
    }

    @Test
    void testCancelAlreadyFilledOrder() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Sell is now filled
        assertEquals(OrderStatus.FILLED, sell.getStatus());

        // Try to cancel filled order
        Order cancelled = engine.cancelOrder(sell.getOrderId());
        assertNull(cancelled); // Should return null (not in book)
    }

    @Test
    void testCancellationDoesNotAffectOtherOrders() {
        // Multiple orders at different prices
        Order sell1 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        Order sell2 = engine.submitLimitOrder(OrderSide.SELL, 10100, 100, TimeInForce.GTC);
        Order sell3 = engine.submitLimitOrder(OrderSide.SELL, 10200, 100, TimeInForce.GTC);

        // Cancel middle price level
        engine.cancelOrder(sell2.getOrderId());

        // Other orders should be unaffected
        assertEquals(OrderStatus.OPEN, sell1.getStatus());
        assertEquals(OrderStatus.OPEN, sell3.getStatus());

        // Best ask should now be 10000 (lowest remaining sell price)
        assertEquals(10000, engine.getBestAsk());
    }
}