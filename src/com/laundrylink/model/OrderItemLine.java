package com.laundrylink.model;

import java.math.BigDecimal;

/**
 * One service line of an order, using the name/unit/price snapshots saved
 * with the order.
 */
public class OrderItemLine {

    private final String serviceName;
    private final String pricingUnit;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;

    public OrderItemLine(String serviceName, String pricingUnit, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal subtotal) {
        this.serviceName = serviceName;
        this.pricingUnit = pricingUnit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
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
