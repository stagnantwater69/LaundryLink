package com.laundrylink.model;

import com.laundrylink.util.MoneyFormat;
import java.math.BigDecimal;

/**
 * An active service as offered on the order form, with its current price.
 * The service catalog itself belongs to Service and Price Management.
 */
public class ServiceOption {

    public static final String PER_KILOGRAM = "KG";
    public static final String PER_PIECE = "PIECE";

    private final int id;
    private final String name;
    private final String pricingUnit;
    private final BigDecimal currentPrice;

    public ServiceOption(int id, String name, String pricingUnit, BigDecimal currentPrice) {
        this.id = id;
        this.name = name;
        this.pricingUnit = pricingUnit;
        this.currentPrice = currentPrice;
    }

    /** Wraps a catalog record from ServiceCatalogService for the order form. */
    public static ServiceOption from(LaundryService service) {
        return new ServiceOption(service.getId(), service.getServiceName(),
                service.getPricingUnit().name(), service.getCurrentPrice());
    }

    public boolean isPerKilogram() {
        return PER_KILOGRAM.equals(pricingUnit);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPricingUnit() {
        return pricingUnit;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    /** "Wash, Dry, and Fold - PHP 65.00 / kg". */
    @Override
    public String toString() {
        return name + " - " + MoneyFormat.php(currentPrice) + (isPerKilogram() ? " / kg" : " / pc");
    }
}
