package com.laundrylink.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Shared money and quantity formatting. Amounts are shown as "PHP 1,234.50".
 */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    public static String php(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return "PHP " + new DecimalFormat("#,##0.00").format(value);
    }

    /** "4 kg x PHP 65.00" or "2 pcs x PHP 150.00". */
    public static String quantityTimesPrice(BigDecimal quantity, String pricingUnit, BigDecimal unitPrice) {
        String qty = new DecimalFormat("#,##0.##").format(quantity);
        String unit = "KG".equals(pricingUnit) ? "kg" : (quantity.compareTo(BigDecimal.ONE) == 0 ? "pc" : "pcs");
        return qty + " " + unit + " x " + php(unitPrice);
    }
}
