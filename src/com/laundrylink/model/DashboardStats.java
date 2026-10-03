package com.laundrylink.model;

import java.math.BigDecimal;

/**
 * Dashboard figures. "Selected day" values use the chosen date; "current"
 * values describe the shop right now regardless of date.
 */
public class DashboardStats {

    // Selected day
    private final int ordersReceived;
    private final BigDecimal paymentsCollected;
    private final int released;

    // Current
    private final int readyForPickup;
    private final BigDecimal outstandingBalance;
    private final int receivedCount;
    private final int washingCount;
    private final int dryingCount;

    public DashboardStats(int ordersReceived, BigDecimal paymentsCollected, int released, int readyForPickup,
            BigDecimal outstandingBalance, int receivedCount, int washingCount, int dryingCount) {
        this.ordersReceived = ordersReceived;
        this.paymentsCollected = paymentsCollected;
        this.released = released;
        this.readyForPickup = readyForPickup;
        this.outstandingBalance = outstandingBalance;
        this.receivedCount = receivedCount;
        this.washingCount = washingCount;
        this.dryingCount = dryingCount;
    }

    public int getOrdersReceived() {
        return ordersReceived;
    }

    public BigDecimal getPaymentsCollected() {
        return paymentsCollected;
    }

    public int getReleased() {
        return released;
    }

    public int getReadyForPickup() {
        return readyForPickup;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public int getReceivedCount() {
        return receivedCount;
    }

    public int getWashingCount() {
        return washingCount;
    }

    public int getDryingCount() {
        return dryingCount;
    }

    /** Orders currently being processed (washing + drying). */
    public int getProcessingCount() {
        return washingCount + dryingCount;
    }
}
