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
 * Tests for market orders.
 */
class MarketOrderTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void testMarketBuyExecution() {
        // Build sell side
        engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);  // 100.00
        engine.submitLimitOrder(OrderSide.SELL, 10100, 50, TimeInForce.GTC);  // 101.00
        engine.submitLimitOrder(OrderSide.SELL, 10200, 50, TimeInForce.GTC);  // 102.00

        // Market buy for 100 units
        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.IOC);

        assertEquals(OrderStatus.FILLED, marketBuy.getStatus());
        assertEquals(100, marketBuy.getFilledQuantity());

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(2, trades.size()); // 50 + 50 = 100

        // Should execute at best available prices: 100.00 then 101.00
        assertEquals(10000, trades.get(0).getExecutionPrice());
        assertEquals(50, trades.get(0).getExecutionQuantity());
        assertEquals(10100, trades.get(1).getExecutionPrice());
        assertEquals(50, trades.get(1).getExecutionQuantity());
    }

    @Test
    void testMarketSellExecution() {
        // Build buy side
        engine.submitLimitOrder(OrderSide.BUY, 10200, 50, TimeInForce.GTC);  // 102.00
        engine.submitLimitOrder(OrderSide.BUY, 10100, 50, TimeInForce.GTC);  // 101.00
        engine.submitLimitOrder(OrderSide.BUY, 10000, 50, TimeInForce.GTC);  // 100.00

        // Market sell for 100 units
        Order marketSell = engine.submitMarketOrder(OrderSide.SELL, 100, TimeInForce.IOC);

        assertEquals(OrderStatus.FILLED, marketSell.getStatus());

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(2, trades.size());

        // Should execute at best available prices: 102.00 then 101.00
        assertEquals(10200, trades.get(0).getExecutionPrice());
        assertEquals(50, trades.get(0).getExecutionQuantity());
        assertEquals(10100, trades.get(1).getExecutionPrice());
        assertEquals(50, trades.get(1).getExecutionQuantity());
    }

    @Test
    void testMarketOrderInsufficientLiquidity() {
        // Only 50 units available
        engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);

        // Market buy for 100 - only 50 available
        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.IOC);

        // Should be partially filled and then expired (IOC)
        assertEquals(OrderStatus.EXPIRED, marketBuy.getStatus());
        assertEquals(50, marketBuy.getFilledQuantity());
        assertEquals(50, marketBuy.getRemainingQuantity());
    }

    @Test
    void testMarketOrderIOC() {
        engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);

        // IOC market buy for 100 - only 50 available
        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.IOC);

        // IOC: execute available, cancel remainder
        assertEquals(OrderStatus.EXPIRED, marketBuy.getStatus());
        assertEquals(50, marketBuy.getFilledQuantity());
    }

    @Test
    void testMarketOrderFOK() {
        // Market orders cannot use FOK (rejected by validator)
        assertThrows(IllegalArgumentException.class, () ->
                engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.FOK)
        );
    }

    @Test
    void testMarketOrderNoLiquidity() {
        // Empty book
        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.IOC);

        // Should expire immediately
        assertEquals(OrderStatus.EXPIRED, marketBuy.getStatus());
        assertEquals(0, marketBuy.getFilledQuantity());
        assertTrue(engine.getTradeHistory().isEmpty());
    }

    @Test
    void testMarketOrderPriceProtection() {
        // This test would require configuring price protection
        // For now, just verify market orders execute at resting prices
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 50, TimeInForce.IOC);

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(10000, trades.get(0).getExecutionPrice()); // Resting order price
    }

    @Test
    void testMarketOrderMultiplePriceLevels() {
        // Sell orders at different prices
        engine.submitLimitOrder(OrderSide.SELL, 10000, 30, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10100, 30, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10200, 30, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10300, 30, TimeInForce.GTC);

        // Market buy for 100
        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 100, TimeInForce.IOC);

        // Should fill 30+30+30+10 = 100 (but only 120 available, so 100 filled)
        // Wait, only 120 available, so 100 filled
        assertEquals(100, marketBuy.getFilledQuantity());

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(4, trades.size()); // 4 price levels touched

        // Quantities: 30, 30, 30, 10
        assertEquals(30, trades.get(0).getExecutionQuantity());
        assertEquals(30, trades.get(1).getExecutionQuantity());
        assertEquals(30, trades.get(2).getExecutionQuantity());
        assertEquals(10, trades.get(3).getExecutionQuantity());
    }

    @Test
    void testMarketOrderVsLimitOrderAtSamePrice() {
        // Limit sell at 100.00
        engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);

        // Market buy should match at 100.00
        Order marketBuy = engine.submitMarketOrder(OrderSide.BUY, 50, TimeInForce.IOC);

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(10000, trades.get(0).getExecutionPrice());
        assertEquals(50, trades.get(0).getExecutionQuantity());
    }
}