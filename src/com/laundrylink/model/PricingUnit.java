package com.laundrylink.model;

/**
 * Ways a laundry service can be priced. The enum constant names are stored
 * in services.pricing_unit in the database.
 */
public enum PricingUnit {
    KG("Per Kilogram"),
    PIECE("Per Piece");

    private final String displayName;

    PricingUnit(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
