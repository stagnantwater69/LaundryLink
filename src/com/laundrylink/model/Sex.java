package com.laundrylink.model;

/**
 * Optional customer sex, used to pick the avatar. Saved by name in
 * customers.sex; NULL means not set.
 */
public enum Sex {
    MALE("Male"),
    FEMALE("Female");

    private final String displayName;

    Sex(String displayName) {
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
