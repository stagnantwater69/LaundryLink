package com.laundrylink.model;

/**
 * Worked out from the payments (see v_order_balances); never saved.
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

    /** Shows the readable name in lists. */
    @Override
    public String toString() {
        return displayName;
    }
}
