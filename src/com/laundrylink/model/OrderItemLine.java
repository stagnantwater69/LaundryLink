package com.laundrylink.model;

import java.math.BigDecimal;

/**
 * One service line of an order, using the name/unit/price snapshots saved
 * with the order. itemId is 0 for a line not saved yet.
 */
public class OrderItemLine {

    private final int itemId;
    private final int serviceId;
    private final String serviceName;
    private final String pricingUnit;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;

    public OrderItemLine(String serviceName, String pricingUnit, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal subtotal) {
        this(0, 0, serviceName, pricingUnit, quantity, unitPrice, subtotal);
    }

    public OrderItemLine(int itemId, int serviceId, String serviceName, String pricingUnit, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal subtotal) {
        this.itemId = itemId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.pricingUnit = pricingUnit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public int getItemId() {
        return itemId;
    }

    public int getServiceId() {
        return serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getPricingUnit() {
        return pricingUnit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
