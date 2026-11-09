package com.limitorderbook.benchmarks;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.TimeInForce;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for the Matching Engine.
 *
 * Run with: java -jar benchmarks.jar
 *
 * Measures:
 * - Order submission latency
 * - Matching throughput
 * - Memory allocation rates
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, warmups = 1)
public class OrderMatchingBenchmark {

    private MatchingEngine engine;

    @Param({"100", "1000", "10000"})
    private int orderCount;

    @Setup(Level.Trial)
    public void setup() {
        engine = new MatchingEngine();
    }

    @Setup(Level.Invocation)
    public void reset() {
        engine.reset();
    }

    /**
     * Benchmark: Submit limit orders without matching (empty opposite side)
     * Measures pure order book insertion performance.
     */
    @Benchmark
    public void submitLimitOrdersNoMatch(Blackhole bh) {
        for (int i = 0; i < orderCount; i++) {
            Order order = engine.submitLimitOrder(
                OrderSide.BUY,
                10000 - (i % 100),  // Vary prices
                100 + (i % 50),
                TimeInForce.GTC
            );
            bh.consume(order);
        }
    }

    /**
     * Benchmark: Submit and match limit orders (crossing spread)
     * Measures full matching pipeline performance.
     */
    @Benchmark
    public void submitAndMatchLimitOrders(Blackhole bh) {
        // Pre-populate sell side
        for (int i = 0; i < orderCount / 2; i++) {
            engine.submitLimitOrder(OrderSide.SELL, 10000 + (i % 50), 100, TimeInForce.GTC);
        }

        // Submit crossing buy orders
        for (int i = 0; i < orderCount / 2; i++) {
            Order order = engine.submitLimitOrder(
                OrderSide.BUY,
                10100 - (i % 50),  // Cross the spread
                100,
                TimeInForce.GTC
            );
            bh.consume(order);
        }
    }

    /**
     * Benchmark: Market order matching against deep book
     * Measures market order execution performance.
     */
    @Benchmark
    public void marketOrderMatching(Blackhole bh) {
        // Build deep order book
        for (int i = 0; i < orderCount; i++) {
            engine.submitLimitOrder(OrderSide.SELL, 10000 + (i % 100), 100, TimeInForce.GTC);
        }

        // Execute market buy orders
        for (int i = 0; i < orderCount / 10; i++) {
            Order order = engine.submitMarketOrder(OrderSide.BUY, 500, TimeInForce.IOC);
            bh.consume(order);
        }
    }

    /**
     * Benchmark: Mixed workload - realistic trading pattern
     * Simulates: 60% limit orders, 20% market orders, 10% cancellations, 10% modifications
     */
    @Benchmark
    public void mixedWorkload(Blackhole bh) {
        // Pre-populate
        for (int i = 0; i < 1000; i++) {
            engine.submitLimitOrder(OrderSide.SELL, 10000 + (i % 50), 100, TimeInForce.GTC);
            engine.submitLimitOrder(OrderSide.BUY, 9950 - (i % 50), 100, TimeInForce.GTC);
        }

        for (int i = 0; i < orderCount; i++) {
            int op = i % 10;
            if (op < 6) {
                // 60% limit orders
                OrderSide side = (op % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                long price = side == OrderSide.BUY ? 9950 - (i % 50) : 10000 + (i % 50);
                Order order = engine.submitLimitOrder(side, price, 100, TimeInForce.GTC);
                bh.consume(order);
            } else if (op < 8) {
                // 20% market orders
                OrderSide side = (op % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                Order order = engine.submitMarketOrder(side, 100, TimeInForce.IOC);
                bh.consume(order);
            } else if (op == 8) {
                // 10% cancellations
                if (engine.getOrderBook().getAllOrders().size() > 0) {
                    var orders = engine.getOrderBook().getAllOrders();
                    long id = orders.get(i % orders.size()).getOrderId();
                    engine.cancelOrder(id);
                }
            } else {
                // 10% modifications
                if (engine.getOrderBook().getAllOrders().size() > 0) {
                    var orders = engine.getOrderBook().getAllOrders();
                    long id = orders.get(i % orders.size()).getOrderId();
                    try {
                        engine.modifyOrder(id, 0, 150);  // Increase quantity
                    } catch (Exception e) {
                        // Ignore validation failures
                    }
                }
            }
        }
    }

    /**
     * Benchmark: High-frequency order submission (many small orders)
     * Measures throughput under high order rate.
     */
    @Benchmark
    @OperationsPerInvocation(1000)
    public void highFrequencySubmission(Blackhole bh) {
        for (int i = 0; i < 1000; i++) {
            Order order = engine.submitLimitOrder(
                OrderSide.BUY,
                10000,
                1,
                TimeInForce.IOC
            );
            bh.consume(order);
        }
    }

    /**
     * Benchmark: FOK order handling
     * Measures fill-or-kill order performance.
     */
    @Benchmark
    public void fokOrderHandling(Blackhole bh) {
        // Build book with limited liquidity
        for (int i = 0; i < 100; i++) {
            engine.submitLimitOrder(OrderSide.SELL, 10000 + i, 100, TimeInForce.GTC);
        }

        // Submit FOK orders that will fail (insufficient liquidity)
        for (int i = 0; i < orderCount / 10; i++) {
            Order order = engine.submitLimitOrder(
                OrderSide.BUY,
                10200,
                50000,  // More than available
                TimeInForce.FOK
            );
            bh.consume(order);
        }
    }

    /**
     * Benchmark: Order book depth query
     * Measures market data query performance.
     */
    @Benchmark
    public void marketDepthQuery(Blackhole bh) {
        // Build deep book
        for (int i = 0; i < orderCount; i++) {
            engine.submitLimitOrder(OrderSide.SELL, 10000 + (i % 100), 100, TimeInForce.GTC);
            engine.submitLimitOrder(OrderSide.BUY, 9900 - (i % 100), 100, TimeInForce.GTC);
        }

        // Query depth
        for (int i = 0; i < 100; i++) {
            var depth = engine.getMarketDepth(OrderSide.SELL, 10);
            var depth2 = engine.getMarketDepth(OrderSide.BUY, 10);
            bh.consume(depth);
            bh.consume(depth2);
        }
    }

    /**
     * Benchmark: Trade history query
     * Measures trade history retrieval performance.
     */
    @Benchmark
    public void tradeHistoryQuery(Blackhole bh) {
        // Generate trades
        for (int i = 0; i < orderCount / 2; i++) {
            engine.submitLimitOrder(OrderSide.SELL, 10000, 100, TimeInForce.GTC);
            engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);
        }

        // Query history
        for (int i = 0; i < 100; i++) {
            var history = engine.getRecentTrades(100);
            bh.consume(history);
        }
    }
}