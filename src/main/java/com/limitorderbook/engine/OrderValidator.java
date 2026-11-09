package com.limitorderbook.engine;

import com.limitorderbook.model.Order;
import com.limitorderbook.model.OrderSide;
import com.limitorderbook.model.OrderType;
import com.limitorderbook.model.OrderStatus;
import com.limitorderbook.model.TimeInForce;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates orders before they are processed by the matching engine.
 * Ensures all business rules and constraints are met.
 */
public final class OrderValidator {

    private final Set<Long> usedOrderIds = new HashSet<>();

    /**
     * Validates a new order submission.
     *
     * @param order the order to validate
     * @return ValidationResult indicating success or failure with reason
     */
    public ValidationResult validateNewOrder(Order order) {
        // Check order ID uniqueness
        if (usedOrderIds.contains(order.getOrderId())) {
            return ValidationResult.reject("Duplicate order ID: " + order.getOrderId());
        }

        // Basic field validation (also done in Order constructor, but double-check)
        if (order.getOrderId() <= 0) {
            return ValidationResult.reject("Order ID must be positive");
        }

        if (order.getSide() == null) {
            return ValidationResult.reject("Order side cannot be null");
        }

        if (order.getType() == null) {
            return ValidationResult.reject("Order type cannot be null");
        }

        if (order.getTimeInForce() == null) {
            return ValidationResult.reject("Time in force cannot be null");
        }

        // Limit order specific validation
        if (order.isLimitOrder()) {
            if (order.getPrice() <= 0) {
                return ValidationResult.reject("Limit order price must be positive");
            }
        } else {
            // Market order - price should be 0
            if (order.getPrice() != 0) {
                return ValidationResult.reject("Market order price must be 0");
            }
        }

        if (order.getOriginalQuantity() <= 0) {
            return ValidationResult.reject("Order quantity must be positive");
        }

        if (order.getRemainingQuantity() != order.getOriginalQuantity()) {
            return ValidationResult.reject("New order must have remaining quantity equal to original quantity");
        }

        if (order.getStatus() != OrderStatus.NEW) {
            return ValidationResult.reject("New order must have status NEW");
        }

        // TimeInForce validation
        if (order.getType() == OrderType.MARKET && order.getTimeInForce() == TimeInForce.FOK) {
            return ValidationResult.reject("Market orders cannot use FOK time-in-force");
        }

        // Valid combination checks
        if (order.getType() == OrderType.LIMIT && order.getTimeInForce() == TimeInForce.IOC) {
            // IOC limit orders are valid - they execute immediately or cancel
        }

        // All validations passed
        usedOrderIds.add(order.getOrderId());
        return ValidationResult.accept();
    }

    /**
     * Validates an order cancellation request.
     *
     * @param orderId   the order ID to cancel
     * @param orderRef  the order reference from the book (null if not found)
     * @return ValidationResult
     */
    public ValidationResult validateCancellation(long orderId, OrderBook.OrderReference orderRef) {
        if (orderId <= 0) {
            return ValidationResult.reject("Order ID must be positive");
        }

        if (orderRef == null) {
            return ValidationResult.reject("Order not found: " + orderId);
        }

        Order order = orderRef.getOrder();
        if (order.isTerminal()) {
            return ValidationResult.reject("Cannot cancel order in terminal state: " + order.getStatus());
        }

        return ValidationResult.accept();
    }

    /**
     * Validates an order modification request.
     *
     * @param orderId       the order ID to modify
     * @param orderRef      the order reference from the book
     * @param newPrice      new price (0 for no change, only for limit orders)
     * @param newQuantity   new quantity (0 for no change)
     * @return ValidationResult
     */
    public ValidationResult validateModification(long orderId, OrderBook.OrderReference orderRef,
                                                 long newPrice, int newQuantity) {
        if (orderId <= 0) {
            return ValidationResult.reject("Order ID must be positive");
        }

        if (orderRef == null) {
            return ValidationResult.reject("Order not found: " + orderId);
        }

        Order order = orderRef.getOrder();

        if (order.isTerminal()) {
            return ValidationResult.reject("Cannot modify order in terminal state: " + order.getStatus());
        }

        if (!order.isActive()) {
            return ValidationResult.reject("Cannot modify non-active order");
        }

        // Check if anything is actually changing
        boolean priceChange = newPrice > 0 && newPrice != order.getPrice();
        boolean qtyChange = newQuantity > 0 && newQuantity != order.getOriginalQuantity();

        if (!priceChange && !qtyChange) {
            return ValidationResult.reject("No modification specified (price and quantity unchanged)");
        }

        // Validate price change
        if (priceChange) {
            if (order.isMarketOrder()) {
                return ValidationResult.reject("Cannot change price of market order");
            }
            if (newPrice <= 0) {
                return ValidationResult.reject("New price must be positive");
            }
        }

        // Validate quantity change
        if (qtyChange) {
            if (newQuantity <= 0) {
                return ValidationResult.reject("New quantity must be positive");
            }
            int filledQty = order.getFilledQuantity();
            if (newQuantity < filledQty) {
                return ValidationResult.reject("New quantity " + newQuantity +
                        " cannot be less than filled quantity " + filledQty);
            }
        }

        return ValidationResult.accept();
    }

    /**
     * Validates a market order submission.
     *
     * @param order the market order to validate
     * @return ValidationResult
     */
    public ValidationResult validateMarketOrder(Order order) {
        if (!order.isMarketOrder()) {
            return ValidationResult.reject("Not a market order");
        }

        ValidationResult baseResult = validateNewOrder(order);
        if (!baseResult.isValid()) {
            return baseResult;
        }

        // Additional market order checks
        if (order.getTimeInForce() == TimeInForce.FOK) {
            return ValidationResult.reject("Market orders cannot use FOK (use IOC for immediate execution)");
        }

        return ValidationResult.accept();
    }

    /**
     * Checks if an order ID has been used.
     */
    public boolean isOrderIdUsed(long orderId) {
        return usedOrderIds.contains(orderId);
    }

    /**
     * Marks an order ID as used (for orders created outside validation).
     */
    public void markOrderIdUsed(long orderId) {
        usedOrderIds.add(orderId);
    }

    /**
     * Clears the used order ID set (for testing/reset).
     */
    public void clear() {
        usedOrderIds.clear();
    }

    /**
     * Result of a validation check.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final String reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static ValidationResult accept() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + reason;
        }
    }
}