package com.limitorderbook.utils;

import com.limitorderbook.engine.OrderBook;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.Trade;

import java.util.List;

/**
 * Utility for printing order book and trade information in formatted tables.
 */
public final class OrderBookPrinter {

    private OrderBookPrinter() {
        // Utility class
    }

    /**
     * Prints the full order book with price levels and orders.
     */
    public static void printOrderBook(OrderBook orderBook) {
        System.out.println("\n========================================");
        System.out.println("           LIMIT ORDER BOOK             ");
        System.out.println("========================================");

        printSide(orderBook, OrderSide.SELL, "SELL");
        System.out.println("----------------------------------------");
        printSide(orderBook, OrderSide.BUY, "BUY");
        System.out.println("========================================\n");
    }

    private static void printSide(OrderBook orderBook, OrderSide side, String label) {
        List<OrderBook.PriceLevelSnapshot> depth = orderBook.getMarketDepth(side, 10);

        System.out.println(label + " SIDE:");
        System.out.printf("%-10s %10s %10s %s%n", "PRICE", "TOTAL QTY", "ORDERS", "DETAILS");
        System.out.println("------------------------------------------------------------------");

        if (depth.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }

        for (OrderBook.PriceLevelSnapshot level : depth) {
            System.out.printf("%-10.2f %10d %10d ",
                    level.getPriceAsDouble(), level.getTotalQuantity(), level.getOrderCount());

            // Print individual orders at this level (first few)
            OrderBook.OrderReference ref = getFirstOrderAtPrice(orderBook, side, (long) (level.getPrice() * 100));
            if (ref != null) {
                Order order = ref.getOrder();
                System.out.printf("[%d@%d]", order.getRemainingQuantity(), order.getOrderId());
                OrderBook.OrderReference next = getNextOrderAtLevel(ref);
                int count = 1;
                while (next != null && count < 3) {
                    System.out.printf(" -> [%d@%d]", next.getOrder().getRemainingQuantity(), next.getOrder().getOrderId());
                    next = getNextOrderAtLevel(next);
                    count++;
                }
                if (level.getOrderCount() > 3) {
                    System.out.printf(" ... +%d more", level.getOrderCount() - 3);
                }
            }
            System.out.println();
        }
    }

    private static OrderBook.OrderReference getFirstOrderAtPrice(OrderBook orderBook, OrderSide side, long price) {
        // This is a workaround since we can't directly access the price level's iterator
        // In a real implementation, we'd expose this properly
        return null;
    }

    private static OrderBook.OrderReference getNextOrderAtLevel(OrderBook.OrderReference ref) {
        return null;
    }

    /**
     * Prints a simple text-based order book view.
     */
    public static void printSimpleOrderBook(OrderBook orderBook) {
        System.out.println(orderBook.toString());
    }

    /**
     * Prints recent trades in a formatted table.
     */
    public static void printRecentTrades(List<Trade> trades, int count) {
        System.out.println("\n========================================");
        System.out.println("           RECENT TRADES                ");
        System.out.println("========================================");
        System.out.printf("%-10s %-12s %-12s %10s %10s%n", "TRADE ID", "BUY ORDER", "SELL ORDER", "PRICE", "QTY");
        System.out.println("------------------------------------------------------------------------");

        int start = Math.max(0, trades.size() - count);
        for (int i = start; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            System.out.printf("%-10d %-12d %-12d %10.2f %10d%n",
                    trade.getTradeId(),
                    trade.getBuyOrderId(),
                    trade.getSellOrderId(),
                    trade.getExecutionPriceAsDouble(),
                    trade.getExecutionQuantity());
        }
        System.out.println("========================================\n");
    }

    /**
     * Prints a single trade execution.
     */
    public static void printTradeExecution(Trade trade) {
        System.out.println("----------------------------------------");
        System.out.println("         TRADE EXECUTED                 ");
        System.out.println("----------------------------------------");
        System.out.printf("Trade ID     : %d%n", trade.getTradeId());
        System.out.printf("Buy Order ID : %d%n", trade.getBuyOrderId());
        System.out.printf("Sell Order ID: %d%n", trade.getSellOrderId());
        System.out.printf("Price        : %.2f%n", trade.getExecutionPriceAsDouble());
        System.out.printf("Quantity     : %d%n", trade.getExecutionQuantity());
        System.out.println("----------------------------------------");
    }

    /**
     * Prints market depth for both sides.
     */
    public static void printMarketDepth(OrderBook orderBook, int depth) {
        System.out.println("\n========================================");
        System.out.println("           MARKET DEPTH                 ");
        System.out.println("========================================");

        System.out.println("SELL (Ask):");
        System.out.printf("%-10s %10s %10s%n", "PRICE", "TOTAL QTY", "ORDERS");
        for (OrderBook.PriceLevelSnapshot level : orderBook.getMarketDepth(OrderSide.SELL, depth)) {
            System.out.printf("%-10.2f %10d %10d%n",
                    level.getPriceAsDouble(), level.getTotalQuantity(), level.getOrderCount());
        }

        System.out.println("BUY (Bid):");
        System.out.printf("%-10s %10s %10s%n", "PRICE", "TOTAL QTY", "ORDERS");
        for (OrderBook.PriceLevelSnapshot level : orderBook.getMarketDepth(OrderSide.BUY, depth)) {
            System.out.printf("%-10.2f %10d %10d%n",
                    level.getPriceAsDouble(), level.getTotalQuantity(), level.getOrderCount());
        }
        System.out.println("========================================\n");
    }

    /**
     * Prints order details.
     */
    public static void printOrder(Order order) {
        if (order == null) {
            System.out.println("Order: null");
            return;
        }
        System.out.println("----------------------------------------");
        System.out.println("ORDER DETAILS");
        System.out.println("----------------------------------------");
        System.out.printf("Order ID     : %d%n", order.getOrderId());
        System.out.printf("Side         : %s%n", order.getSide());
        System.out.printf("Type         : %s%n", order.getType());
        System.out.printf("Price        : %.2f%n", order.getPrice() / 100.0);
        System.out.printf("Original Qty : %d%n", order.getOriginalQuantity());
        System.out.printf("Remaining Qty: %d%n", order.getRemainingQuantity());
        System.out.printf("Filled Qty   : %d%n", order.getFilledQuantity());
        System.out.printf("Arrival Seq  : %d%n", order.getArrivalSequence());
        System.out.printf("Status       : %s%n", order.getStatus());
        System.out.printf("TimeInForce  : %s%n", order.getTimeInForce());
        System.out.println("----------------------------------------");
    }
}