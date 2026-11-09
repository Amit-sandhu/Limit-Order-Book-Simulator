package com.limitorderbook.model;

/**
 * Represents the type of an order.
 * LIMIT: Order executes at specified price or better.
 * MARKET: Order executes at best available price.
 */
public enum OrderType {
    LIMIT,
    MARKET
}