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

/**
 * Account rules: registering a shop, managing staff, and each user's own profile.
 * The owner's role is 'ADMIN', and owners can only add Staff to their own shop.
 */
public class AccountService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NAME_PART_LENGTH = 50;
    private static final int MAX_SHOP_NAME_LENGTH = 100;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 30;

    private final UserDAO userDAO = new UserDAO();

    // ---- Shop registration ----

    /** True if no shop is registered yet (Welcome then opens Register). */
    public boolean isShopRegistrationRequired() throws SQLException {
        Connection connection = ConnectionFactory.getConnection();
        try {
            return !userDAO.anyShopExists(connection);
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Creates a new shop with its owner account. */
    public User registerShopOwner(String shopName, String firstName, String middleName, String lastName,
            String username, String password, String confirmPassword) throws ServiceException, SQLException {
        String cleanShopName = requireShopName(shopName);
        String[] name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);
        requireNewPassword(password, confirmPassword);
        String hash = PasswordHasher.hash(password);

        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            requireUsernameAvailable(connection, cleanUsername, 0);
            int shopId = userDAO.insertShop(connection, cleanShopName);
            int id = userDAO.insert(connection, shopId, name[0], name[1], name[2], cleanUsername, hash, Role.ADMIN);
            User owner = userDAO.findById(connection, id);
            connection.commit();
            return owner.withoutPasswordHash();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            if (DbUtil.isDuplicateKey(e)) {
                throw new ServiceException("That username is already taken.");
            }
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    // ---- Staff accounts ----

    /** The staff of the owner's shop (owners edit themselves in My Profile). */
    public List<User> listStaffAccounts() throws ServiceException, SQLException {
        Connection connection = ConnectionFactory.getConnection();
        try {
            User owner = requireActiveOwner(connection);
            List<User> safeUsers = new ArrayList<User>();
            for (User user : userDAO.findAllStaff(connection, owner.getShopId())) {
                safeUsers.add(user.withoutPasswordHash());
            }
            return safeUsers;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Adds a Staff account to the owner's shop. */
    public User createStaffAccount(String firstName, String middleName, String lastName, String username,
            String password, String confirmPassword) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        String[] name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);
        requireNewPassword(password, confirmPassword);
        String hash = PasswordHasher.hash(password);

        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            User owner = requireActiveOwner(connection);
            requireUsernameAvailable(connection, cleanUsername, 0);
            int id = userDAO.insert(connection, owner.getShopId(), name[0], name[1], name[2], cleanUsername, hash,
                    Role.STAFF);
            User saved = userDAO.findById(connection, id);
            connection.commit();
            return saved.withoutPasswordHash();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            if (DbUtil.isDuplicateKey(e)) {
                throw new ServiceException("That username is already taken.");
            }
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Updates a staff member's name and username, and resets the password if one is typed. */
    public User updateAccount(int userId, String firstName, String middleName, String lastName, String username,
            String newPassword, String confirmPassword) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        String[] name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);
        boolean resetPassword = !isBlank(newPassword) || !isBlank(confirmPassword);
        String newHash = null;
        if (resetPassword) {
            requireNewPassword(newPassword, confirmPassword);
            newHash = PasswordHasher.hash(newPassword);
        }

        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            User owner = requireActiveOwner(connection);
            User target = requireStaffAccount(connection, owner, userId);
            requireUsernameAvailable(connection, cleanUsername, userId);
            userDAO.update(connection, userId, name[0], name[1], name[2], cleanUsername, target.getRole());
            if (newHash != null) {
                userDAO.updatePassword(connection, userId, newHash);
            }
            User saved = userDAO.findById(connection, userId);
            connection.commit();
            return saved.withoutPasswordHash();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            if (DbUtil.isDuplicateKey(e)) {
                throw new ServiceException("That username is already taken.");
            }
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Turns a staff login on or off. Accounts are never deleted. */
    public void setAccountActive(int userId, boolean active) throws ServiceException, SQLException {
        SessionContext.requireAdmin();
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            User owner = requireActiveOwner(connection);
            requireStaffAccount(connection, owner, userId);
            userDAO.updateActive(connection, userId, active);
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    // ---- My Profile ----

    /** Saves the user's own name and username. */
    public User updateOwnProfile(String firstName, String middleName, String lastName, String username)
            throws ServiceException, SQLException {
        int currentUserId = SessionContext.getCurrentUserId();
        String[] name = requireName(firstName, middleName, lastName);
        String cleanUsername = requireUsername(username);

        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            User me = requireActiveSelf(connection, currentUserId);
            requireUsernameAvailable(connection, cleanUsername, me.getId());
            userDAO.update(connection, me.getId(), name[0], name[1], name[2], cleanUsername, me.getRole());
            User saved = userDAO.findById(connection, me.getId());
            connection.commit();
            return saved.withoutPasswordHash();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            if (DbUtil.isDuplicateKey(e)) {
                throw new ServiceException("That username is already taken.");
            }
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    /** Changes the user's password after checking the current one. */
    public void changeOwnPassword(String currentPassword, String newPassword, String confirmPassword)
            throws ServiceException, SQLException {
        int currentUserId = SessionContext.getCurrentUserId();
        if (isBlank(currentPassword)) {
            throw new ServiceException("Enter your current password.");
        }
        requireNewPassword(newPassword, confirmPassword);
        if (newPassword.equals(currentPassword)) {
            throw new ServiceException("The new password must be different from the current password.");
        }
        String newHash = PasswordHasher.hash(newPassword);

        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            User me = requireActiveSelf(connection, currentUserId);
            if (!PasswordHasher.verify(currentPassword, me.getPasswordHash())) {
                throw new ServiceException("Your current password is incorrect.");
            }
            userDAO.updatePassword(connection, me.getId(), newHash);
            connection.commit();
        } catch (SQLException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } catch (ServiceException e) {
            DbUtil.rollbackQuietly(connection);
            throw e;
        } finally {
            DbUtil.closeQuietly(connection);
        }
    }

    // ---- Rule helpers ----

    /** Re-checks that the current user is still an active owner. */
    private User requireActiveOwner(Connection connection) throws ServiceException, SQLException {
        int actorId = SessionContext.requireAdmin().getId();
        User actor = userDAO.findById(connection, actorId);
        if (actor == null || !actor.isActive() || !actor.isAdmin()) {
            throw new ServiceException("Only the shop owner can manage staff accounts.");
        }
        return actor;
    }

    /** Re-checks that the current user's account is still active. */
    private User requireActiveSelf(Connection connection, int userId) throws ServiceException, SQLException {
        User me = userDAO.findById(connection, userId);
        if (me == null || !me.isActive()) {
            throw new ServiceException("Your account is no longer active. Please log in again.");
        }
        return me;
    }

    /** Makes sure the account is staff in the owner's shop. */
    private User requireStaffAccount(Connection connection, User owner, int userId)
            throws ServiceException, SQLException {
        User user = userDAO.findById(connection, userId);
        if (user == null || user.getShopId() != owner.getShopId()) {
            throw new ServiceException("That account no longer exists. Refresh the list and try again.");
        }
        if (user.isAdmin()) {
            throw new ServiceException("The owner account can't be changed here. Use My Profile instead.");
        }
        return user;
    }

    /** Fails if another account already has this username. */
    private void requireUsernameAvailable(Connection connection, String username, int excludeUserId)
            throws ServiceException, SQLException {
        if (userDAO.usernameExists(connection, username, excludeUserId)) {
            throw new ServiceException("The username \"" + username + "\" is already taken.");
        }
    }

    // ---- Input checks ----

    /** Returns {first, middle, last}, trimmed; middle is null if blank. */
    static String[] requireName(String firstName, String middleName, String lastName) throws ServiceException {
        String first = cleanNamePart(firstName, "First name");
        String middle = cleanNamePart(middleName, "Middle name");
        String last = cleanNamePart(lastName, "Last name");
        if (first.isEmpty()) {
            throw new ServiceException("Enter a first name.");
        }
        if (last.isEmpty()) {
            throw new ServiceException("Enter a last name.");
        }
        String[] name = new String[3];
        name[0] = first;
        name[1] = middle.isEmpty() ? null : middle;
        name[2] = last;
        return name;
    }

    /** Trims a name part and checks its length. */
    private static String cleanNamePart(String value, String label) throws ServiceException {
        String clean = collapseSpaces(value);
        if (clean.length() > MAX_NAME_PART_LENGTH) {
            throw new ServiceException(label + " must be at most " + MAX_NAME_PART_LENGTH + " characters.");
        }
        return clean;
    }

    /** Trims the shop name and checks it's filled in and not too long. */
    static String requireShopName(String shopName) throws ServiceException {
        String clean = collapseSpaces(shopName);
        if (clean.isEmpty()) {
            throw new ServiceException("Enter your shop name.");
        }
        if (clean.length() > MAX_SHOP_NAME_LENGTH) {
            throw new ServiceException("Shop name must be at most " + MAX_SHOP_NAME_LENGTH + " characters.");
        }
        return clean;
    }

    /** Username: 3-30 letters, numbers, dots or underscores. */
    static String requireUsername(String username) throws ServiceException {
        String clean = username == null ? "" : username.trim();
        boolean valid = clean.length() >= MIN_USERNAME_LENGTH && clean.length() <= MAX_USERNAME_LENGTH;
        int i = 0;
        while (valid && i < clean.length()) {
            char c = clean.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_';
            if (!allowed) {
                valid = false;
            }
            i++;
        }
        if (!valid) {
            throw new ServiceException("Username must be 3-30 characters using letters, numbers, dots, or underscores.");
        }
        return clean;
    }

    /** Checks the password length and that both entries match. */
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

    /** True if the text is null or empty. */
    private static boolean isBlank(String text) {
        return text == null || text.isEmpty();
    }

    /** Trims the text and squeezes repeated spaces into one. */
    private static String collapseSpaces(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        StringBuilder result = new StringBuilder();
        boolean lastWasSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean isSpace = Character.isWhitespace(c);
            if (isSpace && !lastWasSpace) {
                result.append(' ');
            } else if (!isSpace) {
                result.append(c);
            }
            lastWasSpace = isSpace;
        }
        return result.toString();
    }
}
