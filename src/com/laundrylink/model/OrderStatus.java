package com.laundrylink.model;

/**
 * Laundry processing stages. Stored in orders.laundry_status and
 * order_status_history using the enum constant name.
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

    @Override
    public String toString() {
        return displayName;
    }
}
