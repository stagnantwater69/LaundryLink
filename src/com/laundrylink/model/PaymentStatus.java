package com.laundrylink.model;

/**
 * Derived from payment records (see view v_order_balances); never stored.
 */
public enum PaymentStatus {
    UNPAID("Unpaid"),
    PARTIALLY_PAID("Partial"),
    PAID("Paid");

    private final String displayName;

    PaymentStatus(String displayName) {
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
