package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.UserDAO;
import com.laundrylink.model.User;
import com.laundrylink.util.PasswordHasher;
import com.laundrylink.util.SessionContext;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Login and logout.
 */
public class AuthService {

    private static final String INVALID_LOGIN = "Incorrect username or password.";

    private final UserDAO userDAO = new UserDAO();

    /**
     * Verifies the credentials and returns the account (without its password hash).
     * Does not change the session; the caller signs in on the JavaFX thread.
     */
    public User authenticate(String username, String password) throws ServiceException, SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.isEmpty() || password == null || password.isEmpty()) {
            throw new ServiceException("Enter your username and password.");
        }

        User user;
        try (Connection connection = ConnectionFactory.getConnection()) {
            user = userDAO.findByUsername(connection, cleanUsername);
        }

        if (user == null) {
            // Spend the same hashing time so unknown usernames are not revealed by timing.
            PasswordHasher.verify(password, DummyHash.VALUE);
            throw new ServiceException(INVALID_LOGIN);
        }
        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            throw new ServiceException(INVALID_LOGIN);
        }
        if (!user.isActive()) {
            throw new ServiceException("This account has been deactivated. Please contact the shop owner.");
        }
        return user.withoutPasswordHash();
    }

    public void logout() {
        SessionContext.signOut();
    }

    private static final class DummyHash {
        static final String VALUE = PasswordHasher.hash("laundrylink-dummy-password");
    }
}
