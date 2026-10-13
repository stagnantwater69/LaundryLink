package com.laundrylink.model;

/**
 * Laundry stages, saved by name in orders and order_status_history.
 */
public enum OrderStatus {
    RECEIVED("Received"),
    WASHING("Washing"),
    DRYING("Drying"),
    READY_FOR_PICKUP("Ready for Pickup"),
    RELEASED("Released"),
    CANCELLED("Cancelled");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Shows the readable name in lists. */
    @Override
    public String toString() {
        return displayName;
    }
}
