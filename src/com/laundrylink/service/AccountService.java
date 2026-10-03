package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.UserDAO;
import com.laundrylink.model.Role;
import com.laundrylink.model.User;
import com.laundrylink.util.PasswordHasher;
import com.laundrylink.util.SessionContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Account Management rules: first-run owner setup, the owner's management of
 * staff accounts, and each user's own profile/password.
 *
 * There is exactly one owner (created by Owner Setup). The owner can only
 * create Staff accounts, never another owner. Owner-only methods check the
 * role here (not just by hiding buttons) and re-read the acting account from
 * the database. In the database the owner's role is stored as 'ADMIN'.
 */
public class AccountService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NAME_PART_LENGTH = 50;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._]{3,30}");

    private final UserDAO userDAO = new UserDAO();

    // ------------------------------------------------------------------
    // First-run owner setup
    // ------------------------------------------------------------------

    public boolean isOwnerSetupRequired() throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return !userDAO.adminExists(connection, false);
        }
    }

    /**
     * Creates the one owner account. Eligibility is re-checked inside the
     * transaction, so this fails once the owner exists.
     */
    public User registerOwner(String firstName, String middleName, String lastName, String username,
            String password, String confirmPassword) throws ServiceException, SQLException {
        PersonName name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);
        requireNewPassword(password, confirmPassword);
        String hash = PasswordHasher.hash(password);

        return saveWithUniqueUsername(() -> TransactionHelper.inTransaction(connection -> {
            // Without this, two simultaneous setups both pass the check and deadlock on insert.
            userDAO.lockOwnerSetup(connection);
            if (userDAO.adminExists(connection, true)) {
                throw new ServiceException("The owner account has already been set up. Please log in instead.");
            }
            requireUsernameAvailable(connection, cleanUsername, 0);
            int id = userDAO.insert(connection, name.first, name.middle, name.last, cleanUsername, hash, Role.ADMIN);
            return userDAO.findById(connection, id).withoutPasswordHash();
        }));
    }

    // ------------------------------------------------------------------
    // Staff accounts (owner only)
    // ------------------------------------------------------------------

    /** Staff accounts only; the owner's own account is edited in My Profile. */
    public List<User> listStaffAccounts() throws ServiceException, SQLException {
        try (Connection connection = ConnectionFactory.getConnection()) {
            requireActiveOwner(connection);
            List<User> safeUsers = new ArrayList<>();
            for (User user : userDAO.findAllStaff(connection)) {
                safeUsers.add(user.withoutPasswordHash());
            }
            return safeUsers;
        }
    }

    /**
     * Creates a new account. Always a Staff account: the owner cannot create
     * another owner.
     */
    public User createStaffAccount(String firstName, String middleName, String lastName, String username,
            String password, String confirmPassword) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        PersonName name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);
        requireNewPassword(password, confirmPassword);
        String hash = PasswordHasher.hash(password);

        return saveWithUniqueUsername(() -> TransactionHelper.inTransaction(connection -> {
            requireActiveOwner(connection);
            requireUsernameAvailable(connection, cleanUsername, 0);
            int id = userDAO.insert(connection, name.first, name.middle, name.last, cleanUsername, hash, Role.STAFF);
            return userDAO.findById(connection, id).withoutPasswordHash();
        }));
    }

    /**
     * Updates an account's name and username. The role never changes here, so
     * a staff account can never be turned into an owner. A non-empty
     * newPassword also resets that account's password in the same transaction.
     *
     * @param userId account to update
     * @param firstName first name
     * @param middleName middle name (optional, blank = none)
     * @param lastName last name
     * @param username login name
     * @param newPassword new password, or blank to keep the current one
     * @param confirmPassword must match newPassword
     * @return the saved account (without its password hash)
     * @throws com.laundrylink.service.ServiceException when a rule or validation fails
     * @throws java.sql.SQLException when the database cannot be reached
     */
    public User updateAccount(int userId, String firstName, String middleName, String lastName, String username,
            String newPassword, String confirmPassword) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        PersonName name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);
        boolean resetPassword = (newPassword != null && !newPassword.isEmpty())
                || (confirmPassword != null && !confirmPassword.isEmpty());
        String hash = null;
        if (resetPassword) {
            requireNewPassword(newPassword, confirmPassword);
            hash = PasswordHasher.hash(newPassword);
        }
        String newHash = hash;

        return saveWithUniqueUsername(() -> TransactionHelper.inTransaction(connection -> {
            requireActiveOwner(connection);
            User target = requireStaffAccount(connection, userId);
            requireUsernameAvailable(connection, cleanUsername, userId);

            userDAO.update(connection, userId, name.first, name.middle, name.last, cleanUsername, target.getRole());
            if (newHash != null) {
                userDAO.updatePassword(connection, userId, newHash);
            }
            return userDAO.findById(connection, userId).withoutPasswordHash();
        }));
    }

    /**
     * Activates or deactivates an account. Accounts are never deleted, and the
     * owner account can never be deactivated.
     *
     * @param userId account to change
     * @param active true to allow login, false to block it
     * @throws com.laundrylink.service.ServiceException when a rule fails
     * @throws java.sql.SQLException when the database cannot be reached
     */
    public void setAccountActive(int userId, boolean active) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        TransactionHelper.inTransaction(connection -> {
            requireActiveOwner(connection);
            requireStaffAccount(connection, userId);
            userDAO.updateActive(connection, userId, active);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // My Profile (any logged-in user, own account only)
    // ------------------------------------------------------------------

    public User updateOwnProfile(String firstName, String middleName, String lastName, String username)
            throws ServiceException, SQLException {
        int currentUserId = SessionContext.getCurrentUserId();
        PersonName name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);

        return saveWithUniqueUsername(() -> TransactionHelper.inTransaction(connection -> {
            User me = requireActiveSelf(connection, currentUserId);
            requireUsernameAvailable(connection, cleanUsername, me.getId());
            userDAO.update(connection, me.getId(), name.first, name.middle, name.last, cleanUsername, me.getRole());
            return userDAO.findById(connection, me.getId()).withoutPasswordHash();
        }));
    }

    public void changeOwnPassword(String currentPassword, String newPassword, String confirmPassword)
            throws ServiceException, SQLException {
        int currentUserId = SessionContext.getCurrentUserId();
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new ServiceException("Enter your current password.");
        }
        requireNewPassword(newPassword, confirmPassword);
        if (newPassword.equals(currentPassword)) {
            throw new ServiceException("The new password must be different from the current password.");
        }
        String newHash = PasswordHasher.hash(newPassword);

        TransactionHelper.inTransaction(connection -> {
            User me = requireActiveSelf(connection, currentUserId);
            if (!PasswordHasher.verify(currentPassword, me.getPasswordHash())) {
                throw new ServiceException("Your current password is incorrect.");
            }
            userDAO.updatePassword(connection, me.getId(), newHash);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Rule helpers
    // ------------------------------------------------------------------

    private User requireActiveOwner(Connection connection) throws ServiceException, SQLException {
        int actorId = SessionContext.requireAdmin().getId();
        User actor = userDAO.findById(connection, actorId);
        if (actor == null || !actor.isActive() || !actor.isAdmin()) {
            throw new ServiceException("Only the shop owner can manage staff accounts.");
        }
        return actor;
    }

    private User requireActiveSelf(Connection connection, int userId) throws ServiceException, SQLException {
        User me = userDAO.findById(connection, userId);
        if (me == null || !me.isActive()) {
            throw new ServiceException("Your account is no longer active. Please log in again.");
        }
        return me;
    }

    /** Staff Accounts may only change staff; the owner account is edited in My Profile and never deactivated. */
    private User requireStaffAccount(Connection connection, int userId) throws ServiceException, SQLException {
        User user = userDAO.findById(connection, userId);
        if (user == null) {
            throw new ServiceException("That account no longer exists. Refresh the list and try again.");
        }
        if (user.isAdmin()) {
            throw new ServiceException("The owner account can't be changed here. Use My Profile instead.");
        }
        return user;
    }

    private void requireUsernameAvailable(Connection connection, String username, int excludeUserId)
            throws ServiceException, SQLException {
        if (userDAO.usernameExists(connection, username, excludeUserId)) {
            throw new ServiceException("The username \"" + username + "\" is already taken.");
        }
    }

    /** Turns a duplicate-key race on the unique username into a readable message. */
    private <T> T saveWithUniqueUsername(SaveAction<T> action) throws ServiceException, SQLException {
        try {
            return action.run();
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new ServiceException("That username is already taken.");
            }
            throw e;
        }
    }

    private interface SaveAction<T> {
        T run() throws ServiceException, SQLException;
    }

    /** First, middle (optional, null when blank) and last name after trimming. */
    static final class PersonName {
        final String first;
        final String middle;
        final String last;

        private PersonName(String first, String middle, String last) {
            this.first = first;
            this.middle = middle;
            this.last = last;
        }
    }

    static PersonName requireName(String firstName, String middleName, String lastName) throws ServiceException {
        String first = cleanNamePart(firstName, "First name");
        String middle = cleanNamePart(middleName, "Middle name");
        String last = cleanNamePart(lastName, "Last name");
        if (first.isEmpty()) {
            throw new ServiceException("Enter a first name.");
        }
        if (last.isEmpty()) {
            throw new ServiceException("Enter a last name.");
        }
        return new PersonName(first, middle.isEmpty() ? null : middle, last);
    }

    private static String cleanNamePart(String value, String label) throws ServiceException {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (clean.length() > MAX_NAME_PART_LENGTH) {
            throw new ServiceException(label + " must be at most " + MAX_NAME_PART_LENGTH + " characters.");
        }
        return clean;
    }

    static String requireUsername(String username) throws ServiceException {
        String clean = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(clean).matches()) {
            throw new ServiceException("Username must be 3-30 characters using letters, numbers, dots, or underscores.");
        }
        return clean;
    }

    static void requireNewPassword(String password, String confirmPassword) throws ServiceException {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new ServiceException("Password must be at most " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (!password.equals(confirmPassword)) {
            throw new ServiceException("The passwords do not match.");
        }
    }
}
