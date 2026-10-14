package com.laundrylink.model;

import java.time.LocalDate;
import java.util.ArrayList;
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
    /** Work stages the order's services need (set in Services & Prices). */
    private final List<OrderStatus> neededStages;

    /** Full details of one order. */
    public OrderDetails(OrderSummary summary, int customerId, LocalDate expectedCompletionDate, String notes,
            String createdByName, List<OrderItemLine> items, List<StatusChange> statusHistory,
            List<OrderStatus> neededStages) {
        this.summary = summary;
        this.customerId = customerId;
        this.expectedCompletionDate = expectedCompletionDate;
        this.notes = notes;
        this.createdByName = createdByName;
        this.items = items;
        this.statusHistory = statusHistory;
        this.neededStages = neededStages;
    }

    /** Customer and items can only change while Received and unpaid. */
    public boolean isItemEditable() {
        return summary.getStatus() == OrderStatus.RECEIVED && summary.getAmountPaid().signum() == 0;
    }

    /** Same rule as editing: unpaid and not started yet. */
    public boolean isCancellable() {
        return isItemEditable();
    }

    /** Stages Update Status can move to; empty if the order can't move on. */
    public List<OrderStatus> getNextStatuses() {
        return summary.getStatus().nextStages();
    }

    /** The stage Update Status suggests: the next stage the services need, else Ready for Pickup. */
    public OrderStatus getSuggestedNextStatus() {
        List<OrderStatus> next = getNextStatuses();
        for (OrderStatus stage : next) {
            if (neededStages.contains(stage)) {
                return stage;
            }
        }
        return next.contains(OrderStatus.READY_FOR_PICKUP) ? OrderStatus.READY_FOR_PICKUP : null;
    }

    /** Needed stages that moving to this stage would skip (empty if none). */
    public List<OrderStatus> getSkippedStages(OrderStatus target) {
        List<OrderStatus> skipped = new ArrayList<>();
        OrderStatus current = summary.getStatus();
        for (OrderStatus stage : neededStages) {
            if (stage.ordinal() > current.ordinal() && stage.ordinal() < target.ordinal()) {
                skipped.add(stage);
            }
        }
        return skipped;
    }

    public List<OrderStatus> getNeededStages() {
        return neededStages;
    }

    /** The stage Undo Status goes back to, or null if it can't undo. */
    public OrderStatus getUndoTarget() {
        return undoTarget(summary.getStatus(), statusHistory);
    }

    /**
     * The stage before the last change, if that change can be undone: the order must
     * be in a work stage (not Received, Released or Cancelled) and the last change
     * must not be an undo itself. History is oldest first.
     */
    public static OrderStatus undoTarget(OrderStatus current, List<StatusChange> history) {
        if (undoBlockReason(current, history) != null) {
            return null;
        }
        return history.get(history.size() - 2).getStatus();
    }

    /** Why the last change can't be undone, or null if it can. */
    public static String undoBlockReason(OrderStatus current, List<StatusChange> history) {
        if (current == OrderStatus.RECEIVED || current == OrderStatus.RELEASED
                || current == OrderStatus.CANCELLED) {
            return "Only Washing, Drying, Ironing and Ready for Pickup can be undone.";
        }
        if (history.size() < 2 || history.get(history.size() - 1).getStatus() != current) {
            return "This order's status history doesn't match its status, so it can't be undone.";
        }
        if (history.get(history.size() - 1).isUndo()) {
            return "The last change was already an undo. Use Update Status to move the order forward.";
        }
        return null;
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
