package com.laundrylink.model;

/**
 * A customer as picked on the order form: just enough to identify them.
 * Full customer records belong to Customer Management.
 */
public class CustomerOption {

    private final int id;
    private final String name;
    private final String contactNumber;

    public CustomerOption(int id, String name, String contactNumber) {
        this.id = id;
        this.name = name;
        this.contactNumber = contactNumber;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    /** "Maria Lopez Santos (09171234567)", or just the name when there is no contact number. */
    @Override
    public String toString() {
        return contactNumber == null || contactNumber.isEmpty() ? name : name + " (" + contactNumber + ")";
    }
}
