package com.limitorderbook;
import com.limitorderbook.model.OrderStatus;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderStatus;
import com.limitorderbook.model.TimeInForce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for order book invariants.
 */
class OrderBookInvariantTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void testInvariantsAfterNewOrders() {
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10100, 100, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        assertTrue(violations.isEmpty(), "Invariants violated: " + violations);
    }

    @Test
    void testInvariantsAfterPartialExecution() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 60, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        assertTrue(violations.isEmpty(), "Invariants violated: " + violations);
    }

    @Test
    void testInvariantsAfterFullExecution() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        assertTrue(violations.isEmpty(), "Invariants violated: " + violations);
    }

    @Test
    void testInvariantsAfterCancellation() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.cancelOrder(sell.getOrderId());

        List<String> violations = engine.validateInvariants();
        assertTrue(violations.isEmpty(), "Invariants violated: " + violations);
    }

    @Test
    void testInvariantsAfterModification() {
        Order original = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        engine.modifyOrder(original.getOrderId(), 10100, 150);

        List<String> violations = engine.validateInvariants();
        assertTrue(violations.isEmpty(), "Invariants violated: " + violations);
    }

    @Test
    void testInvariantsAfterMultipleOperations() {
        // Complex sequence of operations
        Order b1 = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        Order s1 = engine.submitLimitOrder(OrderSide.SELL, 10100, 100, TimeInForce.GTC);
        Order b2 = engine.submitLimitOrder(OrderSide.BUY, 10050, 50, TimeInForce.GTC);

        // Partial fill
        engine.submitLimitOrder(OrderSide.SELL, 10000, 75, TimeInForce.GTC);

        // Cancel
        engine.cancelOrder(b2.getOrderId());

        // Modify
        engine.modifyOrder(b1.getOrderId(), 10100, 0);

        List<String> violations = engine.validateInvariants();
        assertTrue(violations.isEmpty(), "Invariants violated: " + violations);
    }

    @Test
    void testNoNegativeRemainingQuantity() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        for (String v : violations) {
            assertFalse(v.contains("negative remaining quantity"), "Negative quantity found: " + v);
        }
    }

    @Test
    void testNoActiveOrderWithZeroRemaining() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        for (String v : violations) {
            assertFalse(v.contains("zero remaining quantity"), "Zero remaining found: " + v);
        }
    }

    @Test
    void testFilledOrdersNotInBook() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        for (String v : violations) {
            assertFalse(v.contains("not in lookup map"), "Filled order in book: " + v);
        }
    }

    @Test
    void testCancelledOrdersNotInBook() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.cancelOrder(sell.getOrderId());

        List<String> violations = engine.validateInvariants();
        for (String v : violations) {
            assertFalse(v.contains("not in lookup map"), "Cancelled order in book: " + v);
        }
    }

    @Test
    void testFilledQuantityNotExceedOriginal() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        List<String> violations = engine.validateInvariants();
        for (String v : violations) {
            assertFalse(v.contains("exceeds original"), "Filled exceeds original: " + v);
        }
    }
}