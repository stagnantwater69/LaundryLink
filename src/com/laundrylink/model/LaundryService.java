package com.laundrylink.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A laundry service and its current price (row in the services table).
 * The needs flags say which work stages (Washing, Drying, Ironing) the service goes through.
 */
public class LaundryService {

    private int id;
    private String serviceName;
    private PricingUnit pricingUnit;
    private BigDecimal currentPrice;
    private boolean active;
    private LocalDateTime createdAt;
    private boolean needsWashing;
    private boolean needsDrying;
    private boolean needsIroning;

    public LaundryService() {
    }

    public LaundryService(int id, String serviceName, PricingUnit pricingUnit,
            BigDecimal currentPrice, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.serviceName = serviceName;
        this.pricingUnit = pricingUnit;
        this.currentPrice = currentPrice;
        this.active = active;
        this.createdAt = createdAt;
    }

    /** A service with the work stages it needs. */
    public LaundryService(int id, String serviceName, PricingUnit pricingUnit, BigDecimal currentPrice,
            boolean active, LocalDateTime createdAt, List<OrderStatus> stages) {
        this(id, serviceName, pricingUnit, currentPrice, active, createdAt);
        setStages(stages);
    }

    /** Work stages this service needs, in shop order (e.g. [Washing, Drying]). */
    public List<OrderStatus> getStages() {
        List<OrderStatus> stages = new ArrayList<>();
        if (needsWashing) {
            stages.add(OrderStatus.WASHING);
        }
        if (needsDrying) {
            stages.add(OrderStatus.DRYING);
        }
        if (needsIroning) {
            stages.add(OrderStatus.IRONING);
        }
        return stages;
    }

    public void setStages(List<OrderStatus> stages) {
        needsWashing = stages.contains(OrderStatus.WASHING);
        needsDrying = stages.contains(OrderStatus.DRYING);
        needsIroning = stages.contains(OrderStatus.IRONING);
    }

    /** "Washing, Drying", or "None" for services with no work stage. */
    public String getStagesText() {
        return stagesText(getStages());
    }

    /** Stage names joined with commas, or "None". */
    public static String stagesText(List<OrderStatus> stages) {
        if (stages.isEmpty()) {
            return "None";
        }
        StringBuilder text = new StringBuilder();
        for (OrderStatus stage : stages) {
            text.append(text.length() == 0 ? "" : ", ").append(stage.getDisplayName());
        }
        return text.toString();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public PricingUnit getPricingUnit() {
        return pricingUnit;
    }

    public void setPricingUnit(PricingUnit pricingUnit) {
        this.pricingUnit = pricingUnit;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return serviceName;
    }
}
