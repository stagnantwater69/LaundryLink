package com.laundrylink.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One order shown in selected customer's order history. */
public class CustomerOrderSummary {

    private final int id;
    private final String orderNumber;
    private final LocalDateTime receivedAt;
    private final String services;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final BigDecimal amountPaid;
    private final BigDecimal balance;
    private final PaymentStatus paymentStatus;

    public CustomerOrderSummary(int id, String orderNumber, LocalDateTime receivedAt, String services,
            OrderStatus status, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance,
            PaymentStatus paymentStatus) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.receivedAt = receivedAt;
        this.services = services;
        this.status = status;
        this.totalAmount = totalAmount;
        this.amountPaid = amountPaid;
        this.balance = balance;
        this.paymentStatus = paymentStatus;
    }

    public int getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public String getServices() { return services; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getBalance() { return balance; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
}
