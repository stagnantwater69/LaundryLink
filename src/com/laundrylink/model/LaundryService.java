package com.laundrylink.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A laundry service and its current price (row in the services table).
 */
public class LaundryService {

    private int id;
    private String serviceName;
    private PricingUnit pricingUnit;
    private BigDecimal currentPrice;
    private boolean active;
    private LocalDateTime createdAt;

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
