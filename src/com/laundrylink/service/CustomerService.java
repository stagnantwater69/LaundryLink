package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.CustomerDAO;
import com.laundrylink.model.Customer;
import com.laundrylink.model.CustomerOrderSummary;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Business rules for Customer Management. */
public class CustomerService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_CONTACT_LENGTH = 20;
    private static final int MAX_ADDRESS_LENGTH = 255;

    private final CustomerDAO customerDAO = new CustomerDAO();

    public List<Customer> findCustomers(String search) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return customerDAO.find(connection, search);
        }
    }

    public Customer addCustomer(String firstName, String middleName, String lastName,
            String contactNumber, String address) throws ServiceException, SQLException {
        CustomerFields fields = validate(firstName, middleName, lastName, contactNumber, address);
        try (Connection connection = ConnectionFactory.getConnection()) {
            int id = customerDAO.insert(connection, fields.first, fields.middle, fields.last,
                    fields.contact, fields.address);
            return customerDAO.findById(connection, id);
        }
    }

    public Customer updateCustomer(int id, String firstName, String middleName, String lastName,
            String contactNumber, String address) throws ServiceException, SQLException {
        CustomerFields fields = validate(firstName, middleName, lastName, contactNumber, address);
        try (Connection connection = ConnectionFactory.getConnection()) {
            if (customerDAO.findById(connection, id) == null) {
                throw new ServiceException("That customer no longer exists. Refresh the list and try again.");
            }
            if (!customerDAO.update(connection, id, fields.first, fields.middle, fields.last,
                    fields.contact, fields.address)) {
                throw new ServiceException("Customer could not be updated.");
            }
            return customerDAO.findById(connection, id);
        }
    }

    public void deleteCustomer(int id) throws ServiceException, SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            if (customerDAO.findById(connection, id) == null) {
                throw new ServiceException("That customer no longer exists. Refresh the list and try again.");
            }
            if (customerDAO.deleteIfNoOrders(connection, id)) {
                return;
            }
            int orderCount = customerDAO.countOrders(connection, id);
            throw new ServiceException("Customer cannot be deleted because this customer has "
                    + orderCount + (orderCount == 1 ? " existing order." : " existing orders."));
        }
    }

    public List<CustomerOrderSummary> getOrderHistory(int customerId) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return customerDAO.findOrderHistory(connection, customerId);
        }
    }

    public int getOrderCount(int customerId) throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return customerDAO.countOrders(connection, customerId);
        }
    }

    private CustomerFields validate(String firstName, String middleName, String lastName,
            String contactNumber, String address) throws ServiceException {
        String first = cleanRequired(firstName, "First name", MAX_NAME_LENGTH);
        String last = cleanRequired(lastName, "Last name", MAX_NAME_LENGTH);
        String middle = cleanOptional(middleName, "Middle name", MAX_NAME_LENGTH);
        String contact = cleanOptional(contactNumber, "Contact number", MAX_CONTACT_LENGTH);
        String cleanAddress = cleanOptional(address, "Address", MAX_ADDRESS_LENGTH);
        return new CustomerFields(first, middle, last, contact, cleanAddress);
    }

    private static String cleanRequired(String value, String label, int max) throws ServiceException {
        String clean = normalize(value);
        if (clean.isEmpty()) {
            throw new ServiceException("Enter " + label.toLowerCase() + ".");
        }
        if (clean.length() > max) {
            throw new ServiceException(label + " must be at most " + max + " characters.");
        }
        return clean;
    }

    private static String cleanOptional(String value, String label, int max) throws ServiceException {
        String clean = normalize(value);
        if (clean.length() > max) {
            throw new ServiceException(label + " must be at most " + max + " characters.");
        }
        return clean.isEmpty() ? null : clean;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static final class CustomerFields {
        final String first;
        final String middle;
        final String last;
        final String contact;
        final String address;

        CustomerFields(String first, String middle, String last, String contact, String address) {
            this.first = first;
            this.middle = middle;
            this.last = last;
            this.contact = contact;
            this.address = address;
        }
    }
}
