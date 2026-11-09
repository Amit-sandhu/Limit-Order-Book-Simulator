package com.limitorderbook.benchmarks;

import com.limitorderbook.engine.OrderBook;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.TimeInForce;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for the OrderBook data structure operations.
 * Isolates the core data structure performance from matching logic.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, warmups = 1)
public class OrderBookBenchmark {

    private OrderBook orderBook;

    @Param({"1000", "10000", "100000"})
    private int orderCount;

    @Setup(Level.Trial)
    public void setup() {
        orderBook = new OrderBook();
    }

    @Setup(Level.Invocation)
    public void reset() {
        orderBook.clear();
    }

    /**
     * Benchmark: Add orders to empty book
     * Measures TreeMap insertion performance.
     */
    @Benchmark
    public void addOrdersEmptyBook(Blackhole bh) {
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.BUY,
                10000 - (i % 100),
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }
        bh.consume(orderBook.size());
    }

    /**
     * Benchmark: Add orders to pre-populated book
     * Measures insertion into existing price levels.
     */
    @Benchmark
    public void addOrdersPopulatedBook(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < 10000; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.SELL,
                10000 + (i % 50),
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Add more orders
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                10000 + i + 1,
                OrderSide.SELL,
                10000 + (i % 50),
                100,
                10000 + i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }
        bh.consume(orderBook.size());
    }

    /**
     * Benchmark: Cancel order from front of queue
     * Measures O(1) cancellation performance.
     */
    @Benchmark
    public void cancelFrontOrder(Blackhole bh) {
        // Add orders
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.BUY,
                10000,
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Cancel from front
        for (int i = 0; i < orderCount; i++) {
            Order cancelled = orderBook.cancelOrder(i + 1);
            bh.consume(cancelled);
        }
    }

    /**
     * Benchmark: Cancel order from middle of queue
     * Measures O(1) intrusive linked list removal.
     */
    @Benchmark
    public void cancelMiddleOrder(Blackhole bh) {
        // Add orders
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.BUY,
                10000,
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Cancel from middle
        for (int i = 0; i < orderCount; i += 2) {
            Order cancelled = orderBook.cancelOrder(i + 1);
            bh.consume(cancelled);
        }
    }

    /**
     * Benchmark: Get best bid/ask
     * Measures TreeMap.firstKey/lastKey performance.
     */
    @Benchmark
    public void getBestPrices(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < orderCount; i++) {
            Order buy = new Order(i * 2 + 1, OrderSide.BUY, 10000 - (i % 100), 100, i * 2 + 1, TimeInForce.GTC);
            buy.open();
            Order sell = new Order(i * 2 + 2, OrderSide.SELL, 10000 + (i % 100), 100, i * 2 + 2, TimeInForce.GTC);
            sell.open();
            orderBook.addOrder(buy);
            orderBook.addOrder(sell);
        }

        // Query best prices repeatedly
        for (int i = 0; i < 10000; i++) {
            Long bestBid = orderBook.getBestBid();
            Long bestAsk = orderBook.getBestAsk();
            bh.consume(bestBid);
            bh.consume(bestAsk);
        }
    }

    /**
     * Benchmark: Take first order at best price
     * Measures price level queue dequeue performance.
     */
    @Benchmark
    public void takeFirstOrder(Blackhole bh) {
        // Pre-populate with many orders at same price
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.SELL,
                10000,
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Take orders one by one
        for (int i = 0; i < orderCount; i++) {
            Order taken = orderBook.takeFirstOrderAtBestPrice(OrderSide.SELL);
            bh.consume(taken);
        }
    }

    /**
     * Benchmark: Re-add order to front of queue
     * Measures re-queue performance after partial fill.
     */
    @Benchmark
    public void reAddOrderToFront(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.SELL,
                10000,
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Take and re-add repeatedly
        for (int i = 0; i < 1000; i++) {
            Order taken = orderBook.takeFirstOrderAtBestPrice(OrderSide.SELL);
            if (taken != null) {
                orderBook.reAddOrderToFront(taken);
            }
            bh.consume(taken);
        }
    }

    /**
     * Benchmark: Get order reference (HashMap lookup)
     * Measures O(1) order lookup performance.
     */
    @Benchmark
    public void getOrderReference(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.BUY,
                10000,
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Lookup orders
        for (int i = 0; i < orderCount; i++) {
            var ref = orderBook.getOrderReference(i + 1);
            bh.consume(ref);
        }
    }

    /**
     * Benchmark: Get market depth
     * Measures depth snapshot creation performance.
     */
    @Benchmark
    public void getMarketDepth(Blackhole bh) {
        // Pre-populate with multiple price levels
        for (int i = 0; i < orderCount; i++) {
            Order buy = new Order(i * 2 + 1, OrderSide.BUY, 10000 - (i % 100), 100, i * 2 + 1, TimeInForce.GTC);
            buy.open();
            Order sell = new Order(i * 2 + 2, OrderSide.SELL, 10000 + (i % 100), 100, i * 2 + 2, TimeInForce.GTC);
            sell.open();
            orderBook.addOrder(buy);
            orderBook.addOrder(sell);
        }

        // Query depth
        for (int i = 0; i < 1000; i++) {
            var buyDepth = orderBook.getMarketDepth(OrderSide.BUY, 10);
            var sellDepth = orderBook.getMarketDepth(OrderSide.SELL, 10);
            bh.consume(buyDepth);
            bh.consume(sellDepth);
        }
    }

    /**
     * Benchmark: Full order book iteration
     * Measures iterating all orders performance.
     */
    @Benchmark
    public void iterateAllOrders(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < orderCount; i++) {
            Order order = new Order(
                i + 1,
                OrderSide.BUY,
                10000 - (i % 100),
                100,
                i + 1,
                TimeInForce.GTC
            );
            order.open();
            orderBook.addOrder(order);
        }

        // Iterate
        var orders = orderBook.getAllOrders();
        for (Order order : orders) {
            bh.consume(order);
        }
    }

    /**
     * Benchmark: Clear order book
     * Measures cleanup performance.
     */
    @Benchmark
    public void clearOrderBook(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < orderCount; i++) {
            Order buy = new Order(i * 2 + 1, OrderSide.BUY, 10000, 100, i * 2 + 1, TimeInForce.GTC);
            buy.open();
            Order sell = new Order(i * 2 + 2, OrderSide.SELL, 10000, 100, i * 2 + 2, TimeInForce.GTC);
            sell.open();
            orderBook.addOrder(buy);
            orderBook.addOrder(sell);
        }

        orderBook.clear();
        bh.consume(orderBook.size());
    }
}