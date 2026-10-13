package com.laundrylink.model;

import com.laundrylink.util.MoneyFormat;
import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * One service line on an order, with the name, unit and price saved at the
 * time. itemId is 0 until the line is saved.
 */
public class OrderItemLine {

    private final int itemId;
    private final int serviceId;
    private final String serviceName;
    private final String pricingUnit;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;

    /** Line for display only (no ids). */
    public OrderItemLine(String serviceName, String pricingUnit, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal subtotal) {
        this(0, 0, serviceName, pricingUnit, quantity, unitPrice, subtotal);
    }

    /** Line with its saved item id and service id. */
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

    // ---- Text used by the table columns ----

    /** Quantity with its unit: "4 kg", "1 pc" or "3 pcs". */
    public String getQuantityText() {
        String number = new DecimalFormat("#,##0.##").format(quantity);
        if ("KG".equals(pricingUnit)) {
            return number + " kg";
        }
        return quantity.compareTo(BigDecimal.ONE) == 0 ? number + " pc" : number + " pcs";
    }

    /** Unit price as "PHP 65.00". */
    public String getUnitPriceText() {
        return MoneyFormat.php(unitPrice);
    }

    /** Subtotal as "PHP 260.00". */
    public String getSubtotalText() {
        return MoneyFormat.php(subtotal);
    }
}
