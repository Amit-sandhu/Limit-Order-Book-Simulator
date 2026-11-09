package com.limitorderbook.engine;

import com.limitorderbook.model.Order;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a price level in the order book, maintaining FIFO order of orders.
 *
 * Uses an intrusive doubly-linked structure via OrderNode for O(1) cancellation
 * when the node reference is known.
 *
 * Time Complexity:
 * - addOrder: O(1)
 * - removeFirstOrder: O(1)
 * - cancelOrder (with node): O(1)
 * - cancelOrder (without node): O(N) where N = orders at this price level
 * - isEmpty: O(1)
 * - size: O(1)
 * - iterator: O(1) to create, O(1) per next()
 */
public final class PriceLevel implements Iterable<Order> {
    private final long price;
    private final Deque<OrderNode> queue;
    private int totalQuantity;

    /**
     * Creates a new price level.
     *
     * @param price the price in integer ticks
     */
    public PriceLevel(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        this.price = price;
        this.queue = new ArrayDeque<>();
        this.totalQuantity = 0;
    }

    /**
     * Adds an order to the end of the queue (FIFO).
     *
     * @param order the order to add
     * @return the node containing the order (for O(1) cancellation)
     */
    public OrderNode addOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        if (order.getPrice() != price) {
            throw new IllegalArgumentException("Order price " + order.getPrice() +
                    " does not match price level " + price);
        }
        if (!order.isActive()) {
            throw new IllegalStateException("Cannot add non-active order to price level");
        }

        OrderNode node = new OrderNode(order);
        queue.addLast(node);
        totalQuantity += order.getRemainingQuantity();
        return node;
    }

    /**
     * Adds an order to the front of the queue (for preserving time priority
     * on partial fills of resting orders).
     *
     * @param order the order to add
     * @return the node containing the order
     */
    public OrderNode addOrderToFront(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        if (order.getPrice() != price) {
            throw new IllegalArgumentException("Order price " + order.getPrice() +
                    " does not match price level " + price);
        }
        if (!order.isActive()) {
            throw new IllegalStateException("Cannot add non-active order to price level");
        }

        OrderNode node = new OrderNode(order);
        queue.addFirst(node);
        totalQuantity += order.getRemainingQuantity();
        return node;
    }

    /**
     * Removes and returns the first order in the queue (FIFO).
     *
     * @return the first order, or null if queue is empty
     */
    public Order removeFirstOrder() {
        OrderNode node = queue.pollFirst();
        if (node == null) {
            return null;
        }
        totalQuantity -= node.order.getRemainingQuantity();
        return node.order;
    }

    /**
     * Returns the first order without removing it.
     *
     * @return the first order, or null if queue is empty
     */
    public Order peekFirstOrder() {
        OrderNode node = queue.peekFirst();
        return node != null ? node.order : null;
    }

    /**
     * Cancels a specific order by its node reference (O(1)).
     *
     * @param node the node returned when the order was added
     * @return true if the order was found and removed
     */
    public boolean cancelOrder(OrderNode node) {
        if (node == null || node.removed) {
            return false;
        }
        boolean removed = queue.remove(node);
        if (removed) {
            node.removed = true;
            totalQuantity -= node.order.getRemainingQuantity();
        }
        return removed;
    }

    /**
     * Cancels an order by order ID (O(N) search).
     * Use {@link #cancelOrder(OrderNode)} for O(1) cancellation.
     *
     * @param orderId the order ID to cancel
     * @return the cancelled order, or null if not found
     */
    public Order cancelOrderById(long orderId) {
        Iterator<OrderNode> iterator = queue.iterator();
        while (iterator.hasNext()) {
            OrderNode node = iterator.next();
            if (node.order.getOrderId() == orderId) {
                iterator.remove();
                node.removed = true;
                totalQuantity -= node.order.getRemainingQuantity();
                return node.order;
            }
        }
        return null;
    }

    /**
     * Updates the total quantity when an order's remaining quantity changes.
     * Call this after a partial fill.
     *
     * @param delta the change in remaining quantity (negative for fills)
     */
    public void adjustQuantity(int delta) {
        totalQuantity += delta;
        if (totalQuantity < 0) {
            throw new IllegalStateException("Total quantity cannot be negative");
        }
    }

    public long getPrice() {
        return price;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getOrderCount() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns an iterator over orders in FIFO order.
     * The iterator is fail-fast.
     */
    public Iterator<Order> iterator() {
        return new Iterator<>() {
            private final Iterator<OrderNode> nodeIterator = queue.iterator();

            @Override
            public boolean hasNext() {
                return nodeIterator.hasNext();
            }

            @Override
            public Order next() {
                if (!nodeIterator.hasNext()) {
                    throw new NoSuchElementException();
                }
                return nodeIterator.next().order;
            }
        };
    }

    @Override
    public String toString() {
        return String.format("PriceLevel{price=%d, orders=%d, totalQty=%d}", price, queue.size(), totalQuantity);
    }

    /**
     * Node in the intrusive doubly-linked queue.
     * Allows O(1) removal when the node reference is known.
     */
    public static final class OrderNode {
        final Order order;
        boolean removed = false;

        private OrderNode(Order order) {
            this.order = order;
        }

        public Order getOrder() {
            return order;
        }

        public boolean isRemoved() {
            return removed;
        }
    }
}