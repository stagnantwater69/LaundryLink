package com.laundrylink.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ready-made service choices for the Services & Prices form. Picking one fills
 * in the name, pricing unit and work stages; the owner only adds the price.
 */
public final class ServicePreset {

    private static final OrderStatus W = OrderStatus.WASHING;
    private static final OrderStatus D = OrderStatus.DRYING;
    private static final OrderStatus I = OrderStatus.IRONING;

    /** Every ready-made choice, in the order shown in the dropdown. */
    public static final List<ServicePreset> ALL = Collections.unmodifiableList(Arrays.asList(
            new ServicePreset("Wash, Dry, and Fold", PricingUnit.KG, W, D),
            new ServicePreset("Wash, Dry, and Iron", PricingUnit.KG, W, D, I),
            new ServicePreset("Ironing", PricingUnit.PIECE, I),
            new ServicePreset("Comforter / Blanket", PricingUnit.PIECE, W, D),
            new ServicePreset("Curtains", PricingUnit.KG, W, D),
            new ServicePreset("Stain Removal", PricingUnit.PIECE, W),
            new ServicePreset("Dry Cleaning (Barong)", PricingUnit.PIECE)));

    private final String name;
    private final PricingUnit pricingUnit;
    private final List<OrderStatus> stages;

    private ServicePreset(String name, PricingUnit pricingUnit, OrderStatus... stages) {
        this.name = name;
        this.pricingUnit = pricingUnit;
        this.stages = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(stages)));
    }

    /** The preset with this name (ignoring case), or null. */
    public static ServicePreset find(String name) {
        for (ServicePreset preset : ALL) {
            if (preset.name.equalsIgnoreCase(name == null ? "" : name.trim())) {
                return preset;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public PricingUnit getPricingUnit() {
        return pricingUnit;
    }

    public List<OrderStatus> getStages() {
        return stages;
    }

    @Override
    public String toString() {
        return name;
    }
}
