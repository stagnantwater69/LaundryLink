package com.laundrylink.model;

import java.time.LocalDateTime;

/**
 * One row of an order's status history: the stage it moved into, when, and by whom.
 */
public class StatusChange {

    private final LocalDateTime changedAt;
    private final OrderStatus status;
    private final String changedByName;

    public StatusChange(LocalDateTime changedAt, OrderStatus status, String changedByName) {
        this.changedAt = changedAt;
        this.status = status;
        this.changedByName = changedByName;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getChangedByName() {
        return changedByName;
    }
}
