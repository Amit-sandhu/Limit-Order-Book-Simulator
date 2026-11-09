package com.limitorderbook.engine;

import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;

import java.util.*;

/**
 * The order book maintaining buy and sell sides with price-time priority.
 *
 * Data Structure:
 * - BUY side: TreeMap<Long, PriceLevel> with reverse order (highest price first)
 * - SELL side: TreeMap<Long, PriceLevel> with natural order (lowest price first)
 * - Order lookup: HashMap<Long, OrderReference> for O(1) order access
 *
 * Time Complexity (P = number of price levels, N = orders at a level):
 * - addOrder: O(log P) for TreeMap + O(1) for queue = O(log P)
 * - getBestBid/Ask: O(1) (first entry of TreeMap)
 * - removePriceLevel: O(log P)
 * - cancelOrder: O(1) lookup + O(1) queue removal = O(1) average
 * - modifyOrder: O(log P) for potential price level change
 * - getMarketDepth: O(D) where D = requested depth
 *
 * Note: Complete matching operation complexity depends on number of matches,
 * not O(1). Each match involves O(log P) for potential level removal.
 */
public final class OrderBook {
    // BUY side: highest price first (reverse order)
    private final NavigableMap<Long, PriceLevel> buyLevels;
    // SELL side: lowest price first (natural order)
    private final NavigableMap<Long, PriceLevel> sellLevels;
    // Order lookup: orderId -> OrderReference
    private final Map<Long, OrderReference> orderLookup;

    public OrderBook() {
        this.buyLevels = new TreeMap<>(Comparator.reverseOrder());
        this.sellLevels = new TreeMap<>();
        this.orderLookup = new HashMap<>();
    }

    /**
     * Adds an order to the appropriate side of the book.
     *
     * @param order the order to add (must be active, limit order)
     * @return the OrderNode for O(1) cancellation
     */
    public PriceLevel.OrderNode addOrder(Order order) {
        if (order.isMarketOrder()) {
            throw new IllegalArgumentException("Market orders cannot be added to the order book");
        }
        if (!order.isActive()) {
            throw new IllegalStateException("Only active orders can be added to the book");
        }

        OrderSide side = order.getSide();
        long price = order.getPrice();

        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        PriceLevel level = levels.get(price);

        if (level == null) {
            level = new PriceLevel(price);
            levels.put(price, level);
        }

        PriceLevel.OrderNode node = level.addOrder(order);

        // Register in lookup map
        OrderReference ref = new OrderReference(side, level, node);
        OrderReference existing = orderLookup.put(order.getOrderId(), ref);
        if (existing != null) {
            throw new IllegalStateException("Order ID already exists in book: " + order.getOrderId());
        }

        return node;
    }

    /**
     * Gets the best bid price (highest buy price).
     *
     * @return best bid price in ticks, or null if no buy orders
     */
    public Long getBestBid() {
        Map.Entry<Long, PriceLevel> entry = buyLevels.firstEntry();
        return entry != null ? entry.getKey() : null;
    }

    /**
     * Gets the best ask price (lowest sell price).
     *
     * @return best ask price in ticks, or null if no sell orders
     */
    public Long getBestAsk() {
        Map.Entry<Long, PriceLevel> entry = sellLevels.firstEntry();
        return entry != null ? entry.getKey() : null;
    }

    /**
     * Gets the best bid price level.
     *
     * @return best bid PriceLevel, or null if no buy orders
     */
    public PriceLevel getBestBidLevel() {
        Map.Entry<Long, PriceLevel> entry = buyLevels.firstEntry();
        return entry != null ? entry.getValue() : null;
    }

    /**
     * Gets the best ask price level.
     *
     * @return best ask PriceLevel, or null if no sell orders
     */
    public PriceLevel getBestAskLevel() {
        Map.Entry<Long, PriceLevel> entry = sellLevels.firstEntry();
        return entry != null ? entry.getValue() : null;
    }

    /**
     * Checks if the book can match (best bid >= best ask).
     */
    public boolean canMatch() {
        Long bestBid = getBestBid();
        Long bestAsk = getBestAsk();
        return bestBid != null && bestAsk != null && bestBid >= bestAsk;
    }

    /**
     * Checks if a limit order at the given price can match against the opposite side.
     *
     * @param side  the side of the incoming order
     * @param price the limit price of the incoming order
     * @return true if there's a matching opposite order
     */
    public boolean canMatchAtPrice(OrderSide side, long price) {
        Long bestOppositePrice = side == OrderSide.BUY ? getBestAsk() : getBestBid();
        if (bestOppositePrice == null) {
            return false;
        }
        return side == OrderSide.BUY ? price >= bestOppositePrice : price <= bestOppositePrice;
    }

    /**
     * Removes and returns the best order from the specified side.
     *
     * @param side the side to remove from
     * @return the removed order, or null if no orders on that side
     */
    public Order removeBestOrder(OrderSide side) {
        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        Map.Entry<Long, PriceLevel> entry = levels.firstEntry();
        if (entry == null) {
            return null;
        }

        PriceLevel level = entry.getValue();
        Order order = level.removeFirstOrder();

        // Remove from lookup
        orderLookup.remove(order.getOrderId());

        // Remove empty price level
        if (level.isEmpty()) {
            levels.remove(entry.getKey());
        }

        return order;
    }

    /**
     * Gets and removes the first order at the best price level for the given side.
     * Used for market order matching.
     *
     * @param side the side to match against (opposite of market order side)
     * @return the resting order, or null if no liquidity
     */
    public Order takeFirstOrderAtBestPrice(OrderSide side) {
        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        Map.Entry<Long, PriceLevel> entry = levels.firstEntry();
        if (entry == null) {
            return null;
        }

        PriceLevel level = entry.getValue();
        if (level.isEmpty()) {
            levels.remove(entry.getKey());
            return takeFirstOrderAtBestPrice(side); // Recurse to next level
        }

        Order order = level.removeFirstOrder();

        // Remove from lookup
        orderLookup.remove(order.getOrderId());

        // Remove empty price level
        if (level.isEmpty()) {
            levels.remove(entry.getKey());
        }

        return order;
    }

    /**
     * Gets the best price for the given side.
     *
     * @param side the side
     * @return best price in ticks, or null if no orders
     */
    public Long getBestPrice(OrderSide side) {
        return side == OrderSide.BUY ? getBestBid() : getBestAsk();
    }

    /**
     * Re-adds an order to the front of its price level queue (for partial fills
     * where the resting order retains time priority).
     * This is a special operation for when a resting order is partially filled.
     *
     * @param order the order to re-add (must be active)
     */
    public void reAddOrderToFront(Order order) {
        if (!order.isActive() || order.isMarketOrder()) {
            throw new IllegalArgumentException("Can only re-add active limit orders");
        }

        OrderSide side = order.getSide();
        long price = order.getPrice();

        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        PriceLevel level = levels.get(price);

        if (level == null) {
            level = new PriceLevel(price);
            levels.put(price, level);
        }

        // Add to front of queue - we need to support this
        PriceLevel.OrderNode node = level.addOrderToFront(order);

        // Update lookup
        OrderReference ref = new OrderReference(side, level, node);
        orderLookup.put(order.getOrderId(), ref);
    }

    /**
     * Cancels an order by ID.
     *
     * @param orderId the order ID to cancel
     * @return the cancelled order, or null if not found
     */
    public Order cancelOrder(long orderId) {
        OrderReference ref = orderLookup.remove(orderId);
        if (ref == null) {
            return null;
        }

        PriceLevel level = ref.priceLevel;
        Order order = ref.node.getOrder();
        boolean removed = level.cancelOrder(ref.node);

        if (!removed) {
            // Should not happen if book is consistent
            orderLookup.put(orderId, ref); // Put back
            return null;
        }

        // Remove empty price level
        if (level.isEmpty()) {
            NavigableMap<Long, PriceLevel> levels = ref.side == OrderSide.BUY ? buyLevels : sellLevels;
            levels.remove(level.getPrice());
        }

        return order;
    }

    /**
     * Gets an order reference by ID (for modification).
     *
     * @param orderId the order ID
     * @return the OrderReference, or null if not found
     */
    public OrderReference getOrderReference(long orderId) {
        return orderLookup.get(orderId);
    }

    /**
     * Moves an order to a different price level (for price modification).
     * Removes from current level and adds to new level.
     *
     * @param ref       the order reference
     * @param newPrice  the new price
     * @return the new OrderNode at the new price level
     */
    public PriceLevel.OrderNode moveOrderToPriceLevel(OrderReference ref, long newPrice) {
        // Remove from current level
        PriceLevel currentLevel = ref.priceLevel;
        boolean removed = currentLevel.cancelOrder(ref.node);
        if (!removed) {
            throw new IllegalStateException("Order not found in current price level");
        }

        // Remove empty price level
        if (currentLevel.isEmpty()) {
            NavigableMap<Long, PriceLevel> levels = ref.side == OrderSide.BUY ? buyLevels : sellLevels;
            levels.remove(currentLevel.getPrice());
        }

        // Add to new price level
        NavigableMap<Long, PriceLevel> levels = ref.side == OrderSide.BUY ? buyLevels : sellLevels;
        PriceLevel newLevel = levels.get(newPrice);
        if (newLevel == null) {
            newLevel = new PriceLevel(newPrice);
            levels.put(newPrice, newLevel);
        }

        PriceLevel.OrderNode newNode = newLevel.addOrder(ref.node.getOrder());

        // Update lookup
        ref.priceLevel = newLevel;
        ref.node = newNode;

        return newNode;
    }

    /**
     * Adjusts quantity at a price level (for partial fills).
     *
     * @param side   the side
     * @param price  the price
     * @param delta  the quantity change (negative for fills)
     */
    public void adjustLevelQuantity(OrderSide side, long price, int delta) {
        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        PriceLevel level = levels.get(price);
        if (level != null) {
            level.adjustQuantity(delta);
        }
    }

    /**
     * Gets market depth for the specified side.
     *
     * @param side  the side
     * @param depth number of price levels to return
     * @return list of (price, totalQuantity) pairs
     */
    public List<PriceLevelSnapshot> getMarketDepth(OrderSide side, int depth) {
        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        List<PriceLevelSnapshot> result = new ArrayList<>(Math.min(depth, levels.size()));

        for (Map.Entry<Long, PriceLevel> entry : levels.entrySet()) {
            if (result.size() >= depth) break;
            PriceLevel level = entry.getValue();
            if (!level.isEmpty()) {
                result.add(new PriceLevelSnapshot(entry.getKey(), level.getTotalQuantity(), level.getOrderCount()));
            }
        }
        return result;
    }

    /**
     * Gets all active orders in the book (for debugging/validation).
     */
    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        for (PriceLevel level : buyLevels.values()) {
            for (Order order : level) {
                orders.add(order);
            }
        }
        for (PriceLevel level : sellLevels.values()) {
            for (Order order : level) {
                orders.add(order);
            }
        }
        return orders;
    }

    /**
     * Gets the number of price levels on a side.
     */
    public int getPriceLevelCount(OrderSide side) {
        return side == OrderSide.BUY ? buyLevels.size() : sellLevels.size();
    }

    /**
     * Gets the total number of orders on a side.
     */
    public int getOrderCount(OrderSide side) {
        int count = 0;
        NavigableMap<Long, PriceLevel> levels = side == OrderSide.BUY ? buyLevels : sellLevels;
        for (PriceLevel level : levels.values()) {
            count += level.getOrderCount();
        }
        return count;
    }

    /**
     * Checks if the book is empty on a side.
     */
    public boolean isEmpty(OrderSide side) {
        return side == OrderSide.BUY ? buyLevels.isEmpty() : sellLevels.isEmpty();
    }

    /**
     * Clears all orders from the book.
     */
    public void clear() {
        buyLevels.clear();
        sellLevels.clear();
        orderLookup.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ORDER BOOK ===\n");

        sb.append("SELL:\n");
        for (Map.Entry<Long, PriceLevel> entry : sellLevels.descendingMap().entrySet()) {
            PriceLevel level = entry.getValue();
            if (!level.isEmpty()) {
                sb.append(String.format("  %8.2f  %6d  (%d orders)\n",
                        entry.getKey() / 100.0, level.getTotalQuantity(), level.getOrderCount()));
            }
        }

        sb.append("----\n");
        sb.append("BUY:\n");
        for (Map.Entry<Long, PriceLevel> entry : buyLevels.entrySet()) {
            PriceLevel level = entry.getValue();
            if (!level.isEmpty()) {
                sb.append(String.format("  %8.2f  %6d  (%d orders)\n",
                        entry.getKey() / 100.0, level.getTotalQuantity(), level.getOrderCount()));
            }
        }
        sb.append("==================");
        return sb.toString();
    }

    /**
     * Reference to an order in the book for O(1) access.
     */
    public static final class OrderReference {
        final OrderSide side;
        PriceLevel priceLevel;
        PriceLevel.OrderNode node;

        private OrderReference(OrderSide side, PriceLevel priceLevel, PriceLevel.OrderNode node) {
            this.side = side;
            this.priceLevel = priceLevel;
            this.node = node;
        }

        public OrderSide getSide() {
            return side;
        }

        public PriceLevel getPriceLevel() {
            return priceLevel;
        }

        public PriceLevel.OrderNode getNode() {
            return node;
        }

        public Order getOrder() {
            return node.getOrder();
        }
    }

    /**
     * Snapshot of a price level for market depth display.
     */
    public static final class PriceLevelSnapshot {
        private final long price;
        private final int totalQuantity;
        private final int orderCount;

        public PriceLevelSnapshot(long price, int totalQuantity, int orderCount) {
            this.price = price;
            this.totalQuantity = totalQuantity;
            this.orderCount = orderCount;
        }

        public long getPrice() {
            return price;
        }

        public int getTotalQuantity() {
            return totalQuantity;
        }

        public int getOrderCount() {
            return orderCount;
        }

        public double getPriceAsDouble() {
            return price / 100.0;
        }

        @Override
        public String toString() {
            return String.format("%.2f x %d (%d orders)", getPriceAsDouble(), totalQuantity, orderCount);
        }
    }
}