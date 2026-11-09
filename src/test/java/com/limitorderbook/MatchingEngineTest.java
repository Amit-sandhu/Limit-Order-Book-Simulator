package com.limitorderbook;
import com.limitorderbook.model.OrderStatus;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderType;
import com.limitorderbook.model.TimeInForce;
import com.limitorderbook.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for basic matching engine functionality.
 */
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void testBasicBuySellMatching() {
        // Submit a sell order at 100.00
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
        assertEquals(OrderStatus.OPEN, sell.getStatus());

        // Submit a buy order at 100.00 (crosses spread)
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Both should be filled
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(OrderStatus.FILLED, sell.getStatus());

        // Check trade was created
        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());

        Trade trade = trades.get(0);
        assertEquals(buy.getOrderId(), trade.getBuyOrderId());
        assertEquals(sell.getOrderId(), trade.getSellOrderId());
        assertEquals(10000, trade.getExecutionPrice());
        assertEquals(100, trade.getExecutionQuantity());
    }

    @Test
    void testNoMatchWhenBidBelowAsk() {
        // Submit sell at 101.00
        engine.submitLimitOrder(OrderSide.SELL, 10100, 100, TimeInForce.GTC);

        // Submit buy at 100.00 (below ask)
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);

        // Buy should remain open, no trade
        assertEquals(OrderStatus.OPEN, buy.getStatus());
        assertTrue(engine.getTradeHistory().isEmpty());

        // Order book should have both orders
        assertEquals(10100, engine.getBestAsk());
        assertEquals(10000, engine.getBestBid());
    }

    @Test
    void testMultipleOrdersAtDifferentPrices() {
        // Build sell side: 100.00, 101.00, 102.00
        engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10100, 50, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10200, 50, TimeInForce.GTC);

        // Buy at 102.00 should match all three levels
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10200, 150, TimeInForce.GTC);

        assertEquals(OrderStatus.FILLED, buy.getStatus());

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(3, trades.size());

        // Prices should be 10000, 10100, 10200 (resting order prices)
        assertEquals(10000, trades.get(0).getExecutionPrice());
        assertEquals(10100, trades.get(1).getExecutionPrice());
        assertEquals(10200, trades.get(2).getExecutionPrice());

        // Quantities should be 50, 50, 50
        assertEquals(50, trades.get(0).getExecutionQuantity());
        assertEquals(50, trades.get(1).getExecutionQuantity());
        assertEquals(50, trades.get(2).getExecutionQuantity());
    }

    @Test
    void testSamePriceFifoPriority() {
        // Submit two sell orders at same price
        Order sell1 = engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);
        Order sell2 = engine.submitLimitOrder(OrderSide.SELL, 10000, 50, TimeInForce.GTC);

        // Buy order should match sell1 first (FIFO)
        engine.submitLimitOrder(OrderSide.BUY, 10000, 50, TimeInForce.GTC);

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(sell1.getOrderId(), trades.get(0).getSellOrderId());

        // sell1 should be filled, sell2 should remain open
        assertEquals(OrderStatus.FILLED, sell1.getStatus());
        assertEquals(OrderStatus.OPEN, sell2.getStatus());
    }

    @Test
    void testCorrectExecutionPrice() {
        // Sell at 99.00, Buy at 101.00
        // Execution price should be 99.00 (resting sell order price)
        engine.submitLimitOrder(OrderSide.SELL, 9900, 100, TimeInForce.GTC);
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10100, 100, TimeInForce.GTC);

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(9900, trades.get(0).getExecutionPrice());
    }

    @Test
    void testMultipleCounterparties() {
        // Three sell orders at different prices
        engine.submitLimitOrder(OrderSide.SELL, 10000, 30, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10100, 30, TimeInForce.GTC);
        engine.submitLimitOrder(OrderSide.SELL, 10200, 30, TimeInForce.GTC);

        // Single buy order for 90 units
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 10200, 90, TimeInForce.GTC);

        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertEquals(3, engine.getTradeHistory().size());

        // Total volume should be 90
        long totalVolume = engine.getTradeHistory().stream()
                .mapToLong(Trade::getExecutionQuantity)
                .sum();
        assertEquals(90, totalVolume);
    }

    @Test
    void testBuyOrderPriorityHigherPriceFirst() {
        // Buy orders at different prices
        Order buy1 = engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC); // 100.00
        Order buy2 = engine.submitLimitOrder(OrderSide.BUY, 10100, 100, TimeInForce.GTC); // 101.00
        Order buy3 = engine.submitLimitOrder(OrderSide.BUY, 9900, 100, TimeInForce.GTC);  // 99.00

        // Sell at 100.50 should match buy2 first (highest bid)
        Order sell = engine.submitLimitOrder(OrderSide.SELL, 10050, 100, TimeInForce.GTC);

        // buy2 should be filled first (101.00 >= 100.50)
        // then buy1 (100.00 < 100.50 so no match)
        // Execution price is the resting order's price (buy2 at 10100)

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(buy2.getOrderId(), trades.get(0).getBuyOrderId());
        assertEquals(10100, trades.get(0).getExecutionPrice()); // resting BUY price (maker)

        // buy1 and buy3 should remain open
        assertEquals(OrderStatus.OPEN, buy1.getStatus());
        assertEquals(OrderStatus.OPEN, buy3.getStatus());
    }

    @Test
    void testSellOrderPriorityLowerPriceFirst() {
        // Sell orders at different prices
        Order sell1 = engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC); // 100.00
        Order sell2 = engine.submitLimitOrder(OrderSide.SELL, 10100, 100, TimeInForce.GTC); // 101.00
        Order sell3 = engine.submitLimitOrder(OrderSide.SELL, 9900, 100, TimeInForce.GTC);  // 99.00

        // Buy at 99.50 should match sell3 first (lowest ask)
        Order buy = engine.submitLimitOrder(OrderSide.BUY, 9950, 100, TimeInForce.GTC);

        List<Trade> trades = engine.getTradeHistory();
        assertEquals(1, trades.size());
        assertEquals(sell3.getOrderId(), trades.get(0).getSellOrderId());
        assertEquals(9900, trades.get(0).getExecutionPrice()); // resting sell price

        assertEquals(OrderStatus.OPEN, sell1.getStatus());
        assertEquals(OrderStatus.OPEN, sell2.getStatus());
    }
}