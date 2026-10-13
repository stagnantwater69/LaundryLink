package com.laundrylink.model;

import java.time.LocalDate;
import java.util.List;

/**
 * An order opened on Laundry Orders: the summary plus what's needed to edit it.
 */
public class OrderDetails {

    private final OrderSummary summary;
    private final int customerId;
    private final LocalDate expectedCompletionDate;
    private final String notes;
    private final String createdByName;
    private final List<OrderItemLine> items;
    private final List<StatusChange> statusHistory;

    /** Full details of one order. */
    public OrderDetails(OrderSummary summary, int customerId, LocalDate expectedCompletionDate, String notes,
            String createdByName, List<OrderItemLine> items, List<StatusChange> statusHistory) {
        this.summary = summary;
        this.customerId = customerId;
        this.expectedCompletionDate = expectedCompletionDate;
        this.notes = notes;
        this.createdByName = createdByName;
        this.items = items;
        this.statusHistory = statusHistory;
    }

    /** Customer and items can only change while Received and unpaid. */
    public boolean isItemEditable() {
        return summary.getStatus() == OrderStatus.RECEIVED && summary.getAmountPaid().signum() == 0;
    }

    /** Same rule as editing: unpaid and not started yet. */
    public boolean isCancellable() {
        return isItemEditable();
    }

    /** The next stage for Update Status, or null if the order can't move on. */
    public OrderStatus getNextStatus() {
        switch (summary.getStatus()) {
            case RECEIVED:
                return OrderStatus.WASHING;
            case WASHING:
                return OrderStatus.DRYING;
            case DRYING:
                return OrderStatus.READY_FOR_PICKUP;
            default:
                return null;
        }
    }

    /** Released and cancelled orders are closed. */
    public boolean isOpen() {
        return summary.getStatus() != OrderStatus.RELEASED && summary.getStatus() != OrderStatus.CANCELLED;
    }

    public int getId() {
        return summary.getId();
    }

    public OrderSummary getSummary() {
        return summary;
    }

    public int getCustomerId() {
        return customerId;
    }

    public LocalDate getExpectedCompletionDate() {
        return expectedCompletionDate;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public List<OrderItemLine> getItems() {
        return items;
    }

    /** Oldest change first. */
    public List<StatusChange> getStatusHistory() {
        return statusHistory;
    }
}
