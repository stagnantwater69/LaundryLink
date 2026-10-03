package com.laundrylink.model;

/**
 * Account roles. Stored in users.role using the enum constant name.
 */
public enum Role {
    ADMIN("Owner"),       // stored as ADMIN in the database
    STAFF("Staff");

    private final String displayName;

    Role(String displayName) {
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
