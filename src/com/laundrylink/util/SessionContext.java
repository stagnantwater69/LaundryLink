package com.laundrylink.util;

import com.laundrylink.model.User;
import com.laundrylink.service.ServiceException;

/**
 * Remembers who's logged in, so any screen can check the user, role and shop.
 * "Admin" here means the shop owner.
 */
public final class SessionContext {

    // The logged-in account, or null.
    private static User currentUser;

    private SessionContext() {
    }

    /** Saves the account after login or a profile update (without the password hash). */
    public static void signIn(User user) {
        if (user == null) {
            currentUser = null;
        } else {
            currentUser = user.withoutPasswordHash();
        }
    }

    /** Forgets the account on logout. */
    public static void signOut() {
        currentUser = null;
    }

    /** The logged-in account, or null. */
    public static User getCurrentUser() {
        return currentUser;
    }

    /** True if someone is logged in. */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /** True if the user is a shop owner. */
    public static boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    /** The logged-in account; fails with a message if the session has ended. */
    public static User requireLoggedIn() throws ServiceException {
        if (currentUser == null) {
            throw new ServiceException("Your session has ended. Please log in again.");
        }
        return currentUser;
    }

    /** The logged-in user's id. */
    public static int getCurrentUserId() throws ServiceException {
        return requireLoggedIn().getId();
    }

    /** The user's shop id. Every query is limited to this shop. */
    public static int getCurrentShopId() throws ServiceException {
        return requireLoggedIn().getShopId();
    }

    /** The logged-in owner; fails with a message if staff try an owner-only action. */
    public static User requireAdmin() throws ServiceException {
        User user = requireLoggedIn();
        if (!user.isAdmin()) {
            throw new ServiceException("Only the shop owner can do this.");
        }
        return user;
    }
}
