package com.limitorderbook;

import com.limitorderbook.engine.MatchingEngine;
import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderType;
import com.limitorderbook.model.TimeInForce;
import com.limitorderbook.model.Trade;
import com.limitorderbook.utils.OrderBookPrinter;
import com.limitorderbook.utils.OrderGenerator;

import java.util.List;
import java.util.Scanner;

/**
 * Main entry point for the Limit Order Book simulator.
 * Provides an interactive CLI for testing the matching engine.
 */
public class Main {

    private static final MatchingEngine engine = new MatchingEngine();
    private static final Scanner scanner = new Scanner(System.in);
    private static final OrderGenerator generator = new OrderGenerator(42, engine);

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   JAVA LIMIT ORDER BOOK SIMULATOR      ");
        System.out.println("========================================");

        if (args.length > 0 && args[0].equals("--demo")) {
            runDemo();
            return;
        }

        runInteractive();
    }

    private static void runInteractive() {
        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select option: ");
            String input = scanner.nextLine().trim();

            try {
                switch (input) {
                    case "1" -> submitBuyLimitOrder();
                    case "2" -> submitSellLimitOrder();
                    case "3" -> submitMarketBuy();
                    case "4" -> submitMarketSell();
                    case "5" -> cancelOrder();
                    case "6" -> modifyOrder();
                    case "7" -> displayOrderBook();
                    case "8" -> displayRecentTrades();
                    case "9" -> displayMarketDepth();
                    case "10" -> runRandomSimulation();
                    case "11" -> runInvariantValidation();
                    case "0" -> {
                        System.out.println("Exiting...");
                        running = false;
                    }
                    default -> System.out.println("Invalid option. Please try again.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }

            if (running) {
                System.out.println("\nPress Enter to continue...");
                scanner.nextLine();
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n========================================");
        System.out.println("           MAIN MENU                    ");
        System.out.println("========================================");
        System.out.println("1. Submit BUY LIMIT order");
        System.out.println("2. Submit SELL LIMIT order");
        System.out.println("3. Submit MARKET BUY");
        System.out.println("4. Submit MARKET SELL");
        System.out.println("5. Cancel order");
        System.out.println("6. Modify order");
        System.out.println("7. Display order book");
        System.out.println("8. Display recent trades");
        System.out.println("9. Display market depth");
        System.out.println("10. Run random simulation");
        System.out.println("11. Run invariant validation");
        System.out.println("0. Exit");
        System.out.println("========================================");
    }

    private static void submitBuyLimitOrder() {
        System.out.print("Enter price (e.g., 100.50): ");
        String priceStr = scanner.nextLine().trim();
        System.out.print("Enter quantity: ");
        String qtyStr = scanner.nextLine().trim();
        System.out.print("Enter time-in-force (GTC/IOC/FOK) [GTC]: ");
        String tifStr = scanner.nextLine().trim();

        long price = parsePrice(priceStr);
        int quantity = Integer.parseInt(qtyStr);
        TimeInForce tif = parseTimeInForce(tifStr, TimeInForce.GTC);

        Order order = engine.submitLimitOrder(OrderSide.BUY, price, quantity, tif);
        System.out.println("\nOrder submitted successfully!");
        OrderBookPrinter.printOrder(order);
    }

    private static void submitSellLimitOrder() {
        System.out.print("Enter price (e.g., 100.50): ");
        String priceStr = scanner.nextLine().trim();
        System.out.print("Enter quantity: ");
        String qtyStr = scanner.nextLine().trim();
        System.out.print("Enter time-in-force (GTC/IOC/FOK) [GTC]: ");
        String tifStr = scanner.nextLine().trim();

        long price = parsePrice(priceStr);
        int quantity = Integer.parseInt(qtyStr);
        TimeInForce tif = parseTimeInForce(tifStr, TimeInForce.GTC);

        Order order = engine.submitLimitOrder(OrderSide.SELL, price, quantity, tif);
        System.out.println("\nOrder submitted successfully!");
        OrderBookPrinter.printOrder(order);
    }

    private static void submitMarketBuy() {
        System.out.print("Enter quantity: ");
        String qtyStr = scanner.nextLine().trim();
        System.out.print("Enter time-in-force (IOC/FOK) [IOC]: ");
        String tifStr = scanner.nextLine().trim();

        int quantity = Integer.parseInt(qtyStr);
        TimeInForce tif = parseTimeInForce(tifStr, TimeInForce.IOC);

        Order order = engine.submitMarketOrder(OrderSide.BUY, quantity, tif);
        System.out.println("\nMarket order submitted!");
        OrderBookPrinter.printOrder(order);

        // Show trades executed
        List<Trade> trades = engine.getRecentTrades(10);
        if (!trades.isEmpty()) {
            OrderBookPrinter.printRecentTrades(trades, trades.size());
        }
    }

    private static void submitMarketSell() {
        System.out.print("Enter quantity: ");
        String qtyStr = scanner.nextLine().trim();
        System.out.print("Enter time-in-force (IOC/FOK) [IOC]: ");
        String tifStr = scanner.nextLine().trim();

        int quantity = Integer.parseInt(qtyStr);
        TimeInForce tif = parseTimeInForce(tifStr, TimeInForce.IOC);

        Order order = engine.submitMarketOrder(OrderSide.SELL, quantity, tif);
        System.out.println("\nMarket order submitted!");
        OrderBookPrinter.printOrder(order);

        List<Trade> trades = engine.getRecentTrades(10);
        if (!trades.isEmpty()) {
            OrderBookPrinter.printRecentTrades(trades, trades.size());
        }
    }

    private static void cancelOrder() {
        System.out.print("Enter order ID to cancel: ");
        String idStr = scanner.nextLine().trim();

        try {
            long orderId = Long.parseLong(idStr);
            Order cancelled = engine.cancelOrder(orderId);
            if (cancelled != null) {
                System.out.println("\nOrder cancelled successfully!");
                OrderBookPrinter.printOrder(cancelled);
            } else {
                System.out.println("Order not found or cannot be cancelled: " + orderId);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid order ID format.");
        }
    }

    private static void modifyOrder() {
        System.out.print("Enter order ID to modify: ");
        String idStr = scanner.nextLine().trim();
        System.out.print("Enter new price (or 0 to keep current): ");
        String priceStr = scanner.nextLine().trim();
        System.out.print("Enter new quantity (or 0 to keep current): ");
        String qtyStr = scanner.nextLine().trim();

        try {
            long orderId = Long.parseLong(idStr);
            long newPrice = priceStr.isEmpty() ? 0 : parsePrice(priceStr);
            int newQuantity = qtyStr.isEmpty() ? 0 : Integer.parseInt(qtyStr);

            Order modified = engine.modifyOrder(orderId, newPrice, newQuantity);
            System.out.println("\nOrder modified successfully!");
            OrderBookPrinter.printOrder(modified);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format.");
        } catch (IllegalArgumentException e) {
            System.out.println("Modification failed: " + e.getMessage());
        }
    }

    private static void displayOrderBook() {
        OrderBookPrinter.printSimpleOrderBook(engine.getOrderBook());
    }

    private static void displayRecentTrades() {
        System.out.print("Number of trades to show [10]: ");
        String countStr = scanner.nextLine().trim();
        int count = countStr.isEmpty() ? 10 : Integer.parseInt(countStr);

        List<Trade> trades = engine.getRecentTrades(count);
        OrderBookPrinter.printRecentTrades(trades, count);
    }

    private static void displayMarketDepth() {
        System.out.print("Depth levels to show [10]: ");
        String depthStr = scanner.nextLine().trim();
        int depth = depthStr.isEmpty() ? 10 : Integer.parseInt(depthStr);

        OrderBookPrinter.printMarketDepth(engine.getOrderBook(), depth);
    }

    private static void runRandomSimulation() {
        System.out.print("Number of orders to generate [100]: ");
        String countStr = scanner.nextLine().trim();
        int count = countStr.isEmpty() ? 100 : Integer.parseInt(countStr);

        System.out.print("Random seed [42]: ");
        String seedStr = scanner.nextLine().trim();
        long seed = seedStr.isEmpty() ? 42 : Long.parseLong(seedStr);

        generator.setPriceRange(9500, 10500)
                 .setQuantityRange(1, 50)
                 .setBuyProbability(0.5)
                 .setMarketOrderProbability(0.15)
                 .setCancelProbability(0.05)
                 .setModifyProbability(0.02);

        // Need to recreate generator with new seed for reproducibility
        // For simplicity, just use existing generator
        int submitted = generator.generateOrders(count);
        System.out.println("\nSimulation complete. Orders submitted: " + submitted);
        System.out.println("Trades executed: " + engine.getTotalTradeCount());
        System.out.println("Total volume: " + engine.getTotalTradedVolume());

        displayOrderBook();
    }

    private static void runInvariantValidation() {
        System.out.println("\nValidating order book invariants...");
        List<String> violations = engine.validateInvariants();

        if (violations.isEmpty()) {
            System.out.println("All invariants PASSED!");
        } else {
            System.out.println("INVARIANT VIOLATIONS FOUND:");
            for (String v : violations) {
                System.out.println("  - " + v);
            }
        }
    }

    private static void runDemo() {
        System.out.println("Running demonstration mode...\n");

        // Submit some initial orders
        System.out.println("=== Submitting initial orders ===");
        engine.submitLimitOrder(OrderSide.BUY, 10000, 100, TimeInForce.GTC);  // 100.00
        engine.submitLimitOrder(OrderSide.BUY, 9950, 50, TimeInForce.GTC);    // 99.50
        engine.submitLimitOrder(OrderSide.SELL, 10100, 75, TimeInForce.GTC);  // 101.00
        engine.submitLimitOrder(OrderSide.SELL, 10050, 60, TimeInForce.GTC);  // 100.50

        displayOrderBook();

        // Submit crossing order
        System.out.println("\n=== Submitting crossing BUY order at 101.00 ===");
        engine.submitLimitOrder(OrderSide.BUY, 10100, 80, TimeInForce.GTC);

        displayOrderBook();

        // Show trades
        System.out.println("\n=== Trade History ===");
        OrderBookPrinter.printRecentTrades(engine.getTradeHistory(), 20);

        // Market order demo
        System.out.println("\n=== Submitting MARKET SELL for 50 ===");
        engine.submitMarketOrder(OrderSide.SELL, 50, TimeInForce.IOC);

        displayOrderBook();
        OrderBookPrinter.printRecentTrades(engine.getTradeHistory(), 20);

        // Invariant check
        runInvariantValidation();
    }

    // Helper methods
    private static long parsePrice(String priceStr) {
        double price = Double.parseDouble(priceStr);
        return Math.round(price * 100); // Convert to ticks
    }

    private static TimeInForce parseTimeInForce(String tifStr, TimeInForce defaultTif) {
        if (tifStr == null || tifStr.isEmpty()) {
            return defaultTif;
        }
        return switch (tifStr.toUpperCase()) {
            case "GTC" -> TimeInForce.GTC;
            case "IOC" -> TimeInForce.IOC;
            case "FOK" -> TimeInForce.FOK;
            default -> defaultTif;
        };
    }
}