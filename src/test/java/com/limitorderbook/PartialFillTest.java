package com.limitorderbook;
import com.limitorderbook.model.OrderStatus;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderStatus;
import com.limitorderbook.model.TimeInForce;
import com.limitorderbook.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for partial order execution.
 */
class PartialFillTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void testPartialBuyExecution() {
        // Sell 100 at 100.00
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Buy 200 at 100.00 - should partially fill sell
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10000, 200, TimeInForce.GTC);

        // Buy should be partially filled with 100 remaining
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(100, buy.getFilledQuantity());
        assertEquals(100, buy.getRemainingQuantity());

        // Sell should be filled (removed from book)
        assertEquals(OrderStatus.FILLED, sell.getStatus());

        // One trade for 100 units
        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).getExecutionQuantity());
    }

    @Test
    void testPartialSellExecution() {
        // Buy 100 at 100.00
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Sell 200 at 100.00
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 200, TimeInForce.GTC);

        assertEquals(OrderStatus.PARTIALLY_FILLED, sell.getStatus());
        assertEquals(100, sell.getFilledQuantity());
        assertEquals(100, sell.getRemainingQuantity());
    }

    @Test
    void testMultiplePartialExecutions() {
        // Multiple small sells
        engine.submitLimitOrder(OrderSide.SELL, 10000, 30, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10000, 20, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10000, 40, TimeInForce.GTC);

        // Large buy order
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Should execute three trades: 30, 20, 40 = 90 total
        // Buy should have 10 remaining
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(90, buy.getFilledQuantity());
        assertEquals(10, buy.getRemainingQuantity());

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(3, trades.size());

        // Check individual trade quantities
        assertEquals(30, trades.get(0).getExecutionQuantity());
        assertEquals(20, trades.get(1).getExecutionQuantity());
        assertEquals(40, trades.get(2).getExecutionQuantity());
    }

    @Test
    void testPartialFillWithMultiplePriceLevels() {
        // Sells at different prices
        engine.submitLimitOrder(OrderSide.SELL, 9900, 50, TimeInForce.GTC);   // 99.00
        engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);  // 100.00
        engine.submitLimitOrder(OrderSide.SELL, 10100, 50, TimeInForce.GTC);  // 101.00

        // Buy 120 at 101.00
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10100, 120, TimeInForce.GTC);

        // Should fill all three levels: 50 + 50 + 20 = 120
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(3, engine.getTradeHistory().size());

        // Check execution prices match resting orders
        assertEquals(9900, engine.getTradeHistory().get(0).getExecutionPrice());
        assertEquals(10000, engine.getTradeHistory().get(1).getExecutionPrice());
        assertEquals(10100, engine.getTradeHistory().get(2).getExecutionPrice());

        // Quantities: 50, 50, 20
        assertEquals(50, engine.getTradeHistory().get(0).getExecutionQuantity());
        assertEquals(50, engine.getTradeHistory().get(1).getExecutionQuantity());
        assertEquals(20, engine.getTradeHistory().get(2).getExecutionQuantity());
    }

    @Test
    void testRemainingQuantitiesAfterPartialFill() {
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10000, 60, TimeInForce.GTC);

        // Buy filled completely
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(60, buy.getFilledQuantity());
        assertEquals(0, buy.getRemainingQuantity());

        // Sell partially filled
        assertEquals(OrderStatus.PARTIALLY_FILLED, sell.getStatus());
        assertEquals(60, sell.getFilledQuantity());
        assertEquals(40, sell.getRemainingQuantity());

        // Original quantities unchanged
        assertEquals(100, sell.getOriginalQuantity());
        assertEquals(60, buy.getOriginalQuantity());
    }
}