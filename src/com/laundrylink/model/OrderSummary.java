package com.laundrylink.model;

import java.math.BigDecimal;
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

    public OrderSummary(int id, String orderNumber, String customerName, String services, LocalDateTime receivedAt,
            OrderStatus status, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance,
            PaymentStatus paymentStatus) {
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
}
