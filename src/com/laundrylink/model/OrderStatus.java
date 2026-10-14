package com.laundrylink.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Laundry stages, saved by name in orders and order_status_history.
 * Work stages are listed in shop order; an order may skip the ones it doesn't need.
 */
public enum OrderStatus {
    RECEIVED("Received"),
    WASHING("Washing"),
    DRYING("Drying"),
    IRONING("Ironing"),
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

    /**
     * Stages Update Status can move to from here: any later work stage up to
     * Ready for Pickup. Released is left out (it needs Release Order).
     */
    public List<OrderStatus> nextStages() {
        List<OrderStatus> stages = new ArrayList<>();
        if (ordinal() < READY_FOR_PICKUP.ordinal()) {
            for (OrderStatus stage : values()) {
                if (stage.ordinal() > ordinal() && stage.ordinal() <= READY_FOR_PICKUP.ordinal()) {
                    stages.add(stage);
                }
            }
        }
        return stages;
    }

    /** Shows the readable name in lists. */
    @Override
    public String toString() {
        return displayName;
    }
}
