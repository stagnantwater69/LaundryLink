package com.laundrylink.model;

import java.time.LocalDateTime;

/** Customer record used by Customer Management. */
public class Customer {

    private final int id;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final Sex sex;
    private final String contactNumber;
    private final String address;
    private final LocalDateTime createdAt;
    private final int orderCount;

    public Customer(int id, String firstName, String middleName, String lastName, Sex sex,
            String contactNumber, String address, LocalDateTime createdAt, int orderCount) {
        this.id = id;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.sex = sex;
        this.contactNumber = contactNumber;
        this.address = address;
        this.createdAt = createdAt;
        this.orderCount = orderCount;
    }

    public int getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    /** Null when not specified. */
    public Sex getSex() { return sex; }
    public String getContactNumber() { return contactNumber; }
    public String getAddress() { return address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getOrderCount() { return orderCount; }

    /** Display code shown on the Customers screen, e.g. C-007. */
    public String getCustomerCode() {
        return String.format("C-%03d", id);
    }

    public String getFullName() {
        String middle = middleName == null || middleName.trim().isEmpty() ? "" : middleName.trim() + " ";
        return firstName + " " + middle + lastName;
    }
}
