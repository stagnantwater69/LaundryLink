package com.laundrylink.model;

import java.math.BigDecimal;

/**
 * An order on the Record Payment card: who it's for, what was ordered,
 * and its totals.
 */
public class PayableOrder {

    private final int id;
    private final String orderNumber;
    private final String customerName;
    private final String services;
    private final String quantityText;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final BigDecimal amountPaid;
    private final BigDecimal balance;

    /** Order with its totals for Record Payment. */
    public PayableOrder(int id, String orderNumber, String customerName, String services, String quantityText,
            OrderStatus status, BigDecimal totalAmount, BigDecimal amountPaid, BigDecimal balance) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.services = services;
        this.quantityText = quantityText;
        this.status = status;
        this.totalAmount = totalAmount;
        this.amountPaid = amountPaid;
        this.balance = balance;
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

    public String getQuantityText() {
        return quantityText;
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

    /** Takes payments only if not cancelled and still owing money. */
    public boolean canTakePayment() {
        if (status == OrderStatus.CANCELLED) {
            return false;
        }
        return balance.signum() > 0;
    }

    /** Text shown in the order box. */
    @Override
    public String toString() {
        return orderNumber + " - " + customerName;
    }
}
