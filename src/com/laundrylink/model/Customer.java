package com.laundrylink.model;

import java.time.LocalDateTime;

/** Customer record used by Customer Management. */
public class Customer {

    private final int id;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final String contactNumber;
    private final String address;
    private final LocalDateTime createdAt;

    public Customer(int id, String firstName, String middleName, String lastName,
            String contactNumber, String address, LocalDateTime createdAt) {
        this.id = id;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.contactNumber = contactNumber;
        this.address = address;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    public String getContactNumber() { return contactNumber; }
    public String getAddress() { return address; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getFullName() {
        String middle = middleName == null || middleName.trim().isEmpty() ? "" : middleName.trim() + " ";
        return firstName + " " + middle + lastName;
    }
}
