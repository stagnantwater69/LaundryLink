package com.laundrylink.model;

import java.time.LocalDateTime;

/**
 * A staff or owner account (a row in users) and its shop.
 */
public class User {

    private int id;
    private int shopId;
    private String shopName;
    private String firstName;
    private String middleName;
    private String lastName;
    private String username;
    private String passwordHash;
    private Role role;
    private boolean active;
    private LocalDateTime createdAt;

    /** Empty account. */
    public User() {
    }

    /** Account with all its saved fields. */
    public User(int id, int shopId, String shopName, String firstName, String middleName, String lastName,
            String username, String passwordHash, Role role, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.shopId = shopId;
        this.shopName = shopName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    /** Copy without the password hash, safe to keep in the session or UI. */
    public User withoutPasswordHash() {
        return new User(id, shopId, shopName, firstName, middleName, lastName, username, null, role, active,
                createdAt);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** "First Middle Last" (middle name only when present). */
    public String getFullName() {
        return hasMiddleName()
                ? firstName + " " + middleName + " " + lastName
                : firstName + " " + lastName;
    }

    /** "Last, First Middle", for lists sorted by last name. */
    public String getSortableName() {
        return hasMiddleName()
                ? lastName + ", " + firstName + " " + middleName
                : lastName + ", " + firstName;
    }

    private boolean hasMiddleName() {
        return middleName != null && !middleName.isEmpty();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /** The shop this account belongs to. */
    public int getShopId() {
        return shopId;
    }

    public void setShopId(int shopId) {
        this.shopId = shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
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

    /** Middle name for tables (blank instead of null). */
    public String getMiddleNameText() {
        return middleName == null ? "" : middleName;
    }

    /** "Active" or "Inactive" for the Staff Accounts table. */
    public String getStatusText() {
        return active ? "Active" : "Inactive";
    }

    /** "Juan Cruz (juan.c)" for lists. */
    @Override
    public String toString() {
        return getFullName() + " (" + username + ")";
    }
}
