package com.laundrylink.model;

import com.laundrylink.util.MoneyFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One cash payment on an order. Its status is the order's current payment
 * status (Paid / Partial).
 */
public class Payment {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final int id;
    private final int orderId;
    private final String orderNumber;
    private final String customerName;
    private final BigDecimal amount;
    private final LocalDateTime paidAt;
    private final String receivedBy;
    private final PaymentStatus orderPaymentStatus;

    /** One payment record. */
    public Payment(int id, int orderId, String orderNumber, String customerName, BigDecimal amount,
            LocalDateTime paidAt, String receivedBy, PaymentStatus orderPaymentStatus) {
        this.id = id;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.amount = amount;
        this.paidAt = paidAt;
        this.receivedBy = receivedBy;
        this.orderPaymentStatus = orderPaymentStatus;
    }

    public int getId() {
        return id;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public PaymentStatus getOrderPaymentStatus() {
        return orderPaymentStatus;
    }

    // ---- Text for the table columns ----

    /** "PAY-0001" */
    public String getPaymentCode() {
        return String.format("PAY-%04d", id);
    }

    public String getAmountText() {
        return MoneyFormat.php(amount);
    }

    /** Payment time, like "Oct 2, 10:45 AM". */
    public String getPaidAtText() {
        if (paidAt == null) {
            return "";
        }
        return paidAt.format(DATE_TIME);
    }

    public String getStatusText() {
        return orderPaymentStatus.getDisplayName();
    }
}
