package com.laundrylink.model;

/**
 * Account roles, saved by name in users.role.
 */
public enum Role {
    ADMIN("Owner"),       // saved as ADMIN
    STAFF("Staff");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Shows the readable name in lists. */
    @Override
    public String toString() {
        return displayName;
    }
}
