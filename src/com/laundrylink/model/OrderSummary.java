package com.laundrylink.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One order as listed on the dashboard: totals and payment figures derived
 * from v_order_balances.
 */
public class OrderSummary {

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

    public OrderSummary(int id, String orderNumber, String customerName, String services, LocalDateTime receivedAt,
            OrderStatus status, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance,
            PaymentStatus paymentStatus) {
        this(id, orderNumber, customerName, services, receivedAt, status, totalAmount, amountPaid, balance,
                paymentStatus, null, null);
    }

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

    /** Past its expected completion date and not yet handed over or cancelled. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today)
                && status != OrderStatus.RELEASED && status != OrderStatus.CANCELLED;
    }

    /** Release rule: ready for pickup and fully paid. */
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

    /** Customer contact number, or null when not recorded. */
    public String getCustomerPhone() {
        return customerPhone;
    }

    /** Expected completion date, or null when not set. */
    public LocalDate getDueDate() {
        return dueDate;
    }
}
