package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.LaundryServiceDAO;
import com.laundrylink.model.LaundryService;
import com.laundrylink.model.PricingUnit;
import com.laundrylink.model.User;
import com.laundrylink.util.SessionContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Service catalog rules shared by the Services & Prices and Laundry Orders
 * modules.
 */
public class ServiceCatalogService {

    private static final int MAX_SERVICE_NAME_LENGTH = 100;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final LaundryServiceDAO laundryServiceDAO = new LaundryServiceDAO();

    /**
     * Loads the catalog for the Services & Prices screen. The owner sees every
     * service so inactive records can be reactivated; staff see active services.
     */
    public List<LaundryService> listServicesForCurrentUser() throws ServiceException, SQLException {
        User user = SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return user.isAdmin()
                    ? laundryServiceDAO.findAll(connection)
                    : laundryServiceDAO.findAllActive(connection);
        }
    }

    /**
     * Shared read contract for any screen that needs services available for a
     * new order.
     */
    public List<LaundryService> listActiveServices() throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return laundryServiceDAO.findAllActive(connection);
        }
    }

    static String requireServiceName(String serviceName) throws ServiceException {
        String cleanName = serviceName == null ? "" : serviceName.trim().replaceAll("\\s+", " ");
        if (cleanName.isEmpty()) {
            throw new ServiceException("Enter a service name.");
        }
        if (cleanName.length() > MAX_SERVICE_NAME_LENGTH) {
            throw new ServiceException("Service name must be at most "
                    + MAX_SERVICE_NAME_LENGTH + " characters.");
        }
        return cleanName;
    }

    static PricingUnit requirePricingUnit(PricingUnit pricingUnit) throws ServiceException {
        if (pricingUnit == null) {
            throw new ServiceException("Select a pricing unit.");
        }
        return pricingUnit;
    }

    static BigDecimal requirePrice(BigDecimal price) throws ServiceException {
        if (price == null) {
            throw new ServiceException("Enter a service price.");
        }
        if (price.signum() <= 0) {
            throw new ServiceException("Service price must be greater than zero.");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new ServiceException("Service price must have at most two decimal places.");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new ServiceException("Service price must not exceed " + MAX_PRICE.toPlainString() + ".");
        }
        return price.setScale(2);
    }
}
