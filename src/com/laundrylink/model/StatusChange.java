package com.laundrylink.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One status history row: the stage the order moved into, when, and by whom.
 */
public class StatusChange {

    private final LocalDateTime changedAt;
    private final OrderStatus status;
    private final String changedByName;

    /** One history row. */
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

    /** When it changed, like "Oct 2, 2026 09:15 AM". */
    public String getChangedAtText() {
        return changedAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a"));
    }

    /** The stage name, e.g. "Ready for Pickup". */
    public String getStatusText() {
        return status.getDisplayName();
    }
}
