package com.laundrylink.util;

import com.laundrylink.model.User;
import com.laundrylink.service.ServiceException;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Holds the logged-in account for the running application.
 * Shared contract for all modules: use getCurrentUserId() and requireAdmin().
 * "Admin" in method names means the shop owner (role ADMIN in the database).
 */
public final class SessionContext {

    private static final ReadOnlyObjectWrapper<User> CURRENT_USER = new ReadOnlyObjectWrapper<>();

    private SessionContext() {
    }

    /** Call on the JavaFX thread after a successful login or profile update. */
    public static void signIn(User user) {
        CURRENT_USER.set(user == null ? null : user.withoutPasswordHash());
    }

    /** Clears the session on logout. */
    public static void signOut() {
        CURRENT_USER.set(null);
    }

    public static ReadOnlyObjectProperty<User> currentUserProperty() {
        return CURRENT_USER.getReadOnlyProperty();
    }

    public static User getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static boolean isLoggedIn() {
        return CURRENT_USER.get() != null;
    }

    public static boolean isAdmin() {
        User user = CURRENT_USER.get();
        return user != null && user.isAdmin();
    }

    public static User requireLoggedIn() throws ServiceException {
        User user = CURRENT_USER.get();
        if (user == null) {
            throw new ServiceException("Your session has ended. Please log in again.");
        }
        return user;
    }

    public static int getCurrentUserId() throws ServiceException {
        return requireLoggedIn().getId();
    }

    public static User requireAdmin() throws ServiceException {
        User user = requireLoggedIn();
        if (!user.isAdmin()) {
            throw new ServiceException("Only the shop owner can do this.");
        }
        return user;
    }
}
