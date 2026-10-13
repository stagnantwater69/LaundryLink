package com.laundrylink.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.laundrylink.util.MoneyFormat;

/**
 * One order as shown in lists, with payment totals from v_order_balances.
 */
public class OrderSummary {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final int id;
    private final String orderNumber;
    private final String customerName;
    private final String services;
    private final LocalDateTime receivedAt;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final BigDecimal amountPaid;
    private final BigDecimal balance;
    private final PaymentStatus paymentStatus;
    private final String customerPhone;
    private final LocalDate dueDate;

    /** Summary without the phone and due date. */
    public OrderSummary(int id, String orderNumber, String customerName, String services, LocalDateTime receivedAt,
            OrderStatus status, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance,
            PaymentStatus paymentStatus) {
        this(id, orderNumber, customerName, services, receivedAt, status, totalAmount, amountPaid, balance,
                paymentStatus, null, null);
    }

    /** Full summary. */
    public OrderSummary(int id, String orderNumber, String customerName, String services, LocalDateTime receivedAt,
            OrderStatus status, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance,
            PaymentStatus paymentStatus, String customerPhone, LocalDate dueDate) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.services = services;
        this.receivedAt = receivedAt;
        this.status = status;
        this.totalAmount = totalAmount;
        this.amountPaid = amountPaid;
        this.balance = balance;
        this.paymentStatus = paymentStatus;
        this.customerPhone = customerPhone;
        this.dueDate = dueDate;
    }

    /** Past its due date and not yet released or cancelled. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today)
                && status != OrderStatus.RELEASED && status != OrderStatus.CANCELLED;
    }

    /** Can be released: ready for pickup and fully paid. */
    public boolean isReleasable() {
        return status == OrderStatus.READY_FOR_PICKUP && balance.signum() == 0;
    }

    public int getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getServices() {
        return services;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    /** Customer's phone, or null. */
    public String getCustomerPhone() {
        return customerPhone;
    }

    /** Due date, or null. */
    public LocalDate getDueDate() {
        return dueDate;
    }

    // ---- Text used by the table columns ----

    /** Due date like "Oct 2, 2026", or a dash if not set. */
    public String getDueDateText() {
        return dueDate == null ? "\u2014" : dueDate.format(DATE);
    }

    /** Total as "PHP 1,234.00". */
    public String getTotalText() {
        return MoneyFormat.php(totalAmount);
    }

    /** Laundry stage name, e.g. "Washing". */
    public String getStatusText() {
        return status.getDisplayName();
    }

    /** Payment status name, e.g. "Partial". */
    public String getPaymentText() {
        return paymentStatus.getDisplayName();
    }
}
