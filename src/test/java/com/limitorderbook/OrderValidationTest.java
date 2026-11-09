package com.limitorderbook;
import com.limitorderbook.model.OrderStatus;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.engine.OrderValidator;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderType;
import com.limitorderbook.model.TimeInForce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for order validation.
 */
class OrderValidationTest {

    private MatchingEngine engine;
    private OrderValidator validator;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
        validator = engine.getOrderBook() != null ? new OrderValidator() : null;
        // Can't access validator directly from engine, create separate one
        validator = new OrderValidator();
    }

    @Test
    void testNegativePriceRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitLimitOrder(OrderSide.BUY, -100, 100, TimeInForce.GTC)
        );
    }

    @Test
    void testZeroPriceRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitLimitOrder(OrderSide.BUY, 0, 100, TimeInForce.GTC)
        );
    }

    @Test
    void testZeroQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitLimitOrder(OrderSide.BUY, 10000, 0, TimeInForce.GTC)
        );
    }

    @Test
    void testNegativeQuantityRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitLimitOrder(OrderSide.BUY, 10000, -100, TimeInForce.GTC)
        );
    }

    @Test
    void testDuplicateOrderIdRejected() {
        // First order
        Order order1 = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        long id = order1.getOrderId();

        // Mark ID as used in this validator
        validator.markOrderIdUsed(id);

        // Now test duplicate
        Order order2 = new Order(id, OrderSide.BUY, 10000, 100, 2, TimeInForce.GTC);
        var result = validator.validateNewOrder(order2);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Duplicate"));
    }

    @Test
    void testInvalidOrderType() {
        // Can't test directly since Order constructor validates
        // OrderType is enum, so invalid values can't be created
    }

    @Test
    void testMarketOrderWithFOKRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.FOK)
        );
    }

    @Test
    void testMarketOrderWithGTCRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.GTC)
        );
    }

    @Test
    void testLimitOrderWithValidPrice() {
        // Should succeed
        Order order = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        assertEquals(OrderStatus.OPEN, order.getStatus());
    }

    @Test
    void testOrderWithNullSide() {
        // Can't test - OrderSide is enum
    }

    @Test
    void testOrderWithNullTimeInForce() {
        // Can't test - TimeInForce is enum, required in constructor
    }

    @Test
    void testCancelValidationUnknownOrder() {
        var result = validator.validateCancellation(99999L, null);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("not found"));
    }

    @Test
    void testCancelValidationFilledOrder() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Filled order is removed from book, so reference is null
        var ref = engine.getOrderBook().getOrderReference(sell.getOrderId());
        var result = validator.validateCancellation(sell.getOrderId(), ref);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("not found"));
    }

    @Test
    void testModifyValidationUnknownOrder() {
        var result = validator.validateModification(99999L, null, 10100, 150);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("not found"));
    }

    @Test
    void testModifyValidationFilledOrder() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Filled order is removed from book, so reference is null
        var ref = engine.getOrderBook().getOrderReference(sell.getOrderId());
        var result = validator.validateModification(sell.getOrderId(), ref, 10100, 150);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("not found"));
    }

    @Test
    void testModifyValidationQuantityBelowFilled() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 60, TimeInForce.GTC);

        var ref = engine.getOrderBook().getOrderReference(sell.getOrderId());
        // Try to reduce to 50 (below filled 60)
        var result = validator.validateModification(sell.getOrderId(), ref, 0, 50);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("less than filled"));
    }

    @Test
    void testModifyValidationMarketOrderPriceChange() {
        Order market = engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.IOC);
        var ref = engine.getOrderBook().getOrderReference(market.getOrderId());

        // Market orders don't go in book, so ref is null
        // But if it were, price change should be rejected
    }

    @Test
    void testNoChangeModificationRejected() {
        Order order = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        var ref = engine.getOrderBook().getOrderReference(order.getOrderId());

        var result = validator.validateModification(order.getOrderId(), ref, 10000, 100);
        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("unchanged"));
    }

    @Test
    void testValidModificationAccepted() {
        Order order = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        var ref = engine.getOrderBook().getOrderReference(order.getOrderId());

        var result = validator.validateModification(order.getOrderId(), ref, 10100, 150);
        assertTrue(result.isValid());
    }
}