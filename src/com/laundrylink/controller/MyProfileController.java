package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AccountService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SessionContext;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * My Profile: users edit their own name, username and password. The ID card shows the saved account.
 */
public class MyProfileController {

    private static final DateTimeFormatter MEMBER_SINCE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    @FXML private Label monogramLabel;
    @FXML private Label fullNameLabel;
    @FXML private Label roleBadgeLabel;
    @FXML private Label usernameBadgeLabel;
    @FXML private Label statusLabel;
    @FXML private Label accessLabel;
    @FXML private Label memberSinceLabel;
    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField usernameField;
    @FXML private Label profileMessageLabel;
    @FXML private Label unsavedLabel;
    @FXML private Button resetProfileButton;
    @FXML private Button saveProfileButton;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label lengthRuleLabel;
    @FXML private Label matchRuleLabel;
    @FXML private Label differentRuleLabel;
    @FXML private Label passwordMessageLabel;
    @FXML private Button changePasswordButton;

    private final AccountService accountService = new AccountService();

    /** The account as last saved, used to spot unsaved edits. */
    private User savedUser;

    /** Shows the user's account and the password rules. */
    @FXML
    private void initialize() {
        showUser(SessionContext.getCurrentUser());
        showPasswordRules();
    }

    // ---- Display ----

    /** Fills the ID card and the form. */
    private void showUser(User user) {
        if (user == null) {
            return;
        }
        savedUser = user;
        showIdCard(user);
        firstNameField.setText(user.getFirstName());
        middleNameField.setText(user.getMiddleNameText());
        lastNameField.setText(user.getLastName());
        usernameField.setText(user.getUsername());
        showProfileEditState();
    }

    /** Fills the ID card at the top. */
    private void showIdCard(User user) {
        monogramLabel.setText(initials(user));
        fullNameLabel.setText(user.getFullName());
        roleBadgeLabel.setText(user.getRole().getDisplayName().toUpperCase());
        roleBadgeLabel.getStyleClass().removeAll("owner", "staff");
        roleBadgeLabel.getStyleClass().add(user.isAdmin() ? "owner" : "staff");
        usernameBadgeLabel.setText("@" + user.getUsername());
        statusLabel.setText(user.getStatusText());
        accessLabel.setText(user.isAdmin() ? "All modules and staff accounts" : "Daily shop operations");
        memberSinceLabel.setText(user.getCreatedAt() == null ? "—" : user.getCreatedAt().format(MEMBER_SINCE));
    }

    /** The user is typing in the profile form. */
    @FXML
    private void handleProfileTyped() {
        showProfileEditState();
    }

    /** Save and Reset only work when something was changed. */
    private void showProfileEditState() {
        boolean edited = savedUser != null && (
                !same(firstNameField.getText(), savedUser.getFirstName())
                || !same(middleNameField.getText(), savedUser.getMiddleName())
                || !same(lastNameField.getText(), savedUser.getLastName())
                || !same(usernameField.getText(), savedUser.getUsername()));
        saveProfileButton.setDisable(!edited);
        resetProfileButton.setDisable(!edited);
        unsavedLabel.setVisible(edited);
        unsavedLabel.setManaged(edited);
        if (edited) {
            Dialogs.clearMessage(profileMessageLabel);
        }
    }

    /** The user is typing a password. */
    @FXML
    private void handlePasswordTyped() {
        showPasswordRules();
    }

    /** Ticks each password rule as it's met (checked again on save). */
    private void showPasswordRules() {
        String current = currentPasswordField.getText();
        String password = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();
        boolean longEnough = password.length() >= AccountService.MIN_PASSWORD_LENGTH;
        boolean matches = !password.isEmpty() && password.equals(confirm);
        boolean different = !password.isEmpty() && !current.isEmpty() && !password.equals(current);

        showRule(lengthRuleLabel, longEnough, !password.isEmpty());
        showRule(matchRuleLabel, matches, !confirm.isEmpty());
        showRule(differentRuleLabel, different, !password.isEmpty() && !current.isEmpty());
        changePasswordButton.setDisable(current.isEmpty() || !longEnough || !matches || !different);
        if (!password.isEmpty() || !confirm.isEmpty()) {
            Dialogs.clearMessage(passwordMessageLabel);
        }
    }

    /** Green tick when met, red cross after a failed try, otherwise a plain bullet. */
    private static void showRule(Label rule, boolean met, boolean attempted) {
        rule.getStyleClass().removeAll("met", "failed");
        String text = rule.getText().substring(2);
        if (met) {
            rule.getStyleClass().add("met");
            rule.setText("✓ " + text);
        } else if (attempted) {
            rule.getStyleClass().add("failed");
            rule.setText("✗ " + text);
        } else {
            rule.setText("• " + text);
        }
    }

    // ---- Actions ----

    /** Reset button: puts the saved values back. */
    @FXML
    private void handleResetProfile() {
        Dialogs.clearMessage(profileMessageLabel);
        showUser(savedUser);
    }

    /** Save Profile button: saves the name and username. */
    @FXML
    private void handleSaveProfile() {
        final String firstName = firstNameField.getText();
        final String middleName = middleNameField.getText();
        final String lastName = lastNameField.getText();
        final String username = usernameField.getText();
        Dialogs.clearMessage(profileMessageLabel);
        saveProfileButton.setDisable(true);
        resetProfileButton.setDisable(true);
        saveProfileButton.setText("Saving...");
        BackgroundTask.run(() -> accountService.updateOwnProfile(firstName, middleName, lastName, username), updated -> {
            saveProfileButton.setText("Save Profile");
            SessionContext.signIn(updated);
            AppShell.refreshUserBadge();
            showUser(updated);
            Dialogs.showSuccess(profileMessageLabel, "Profile saved.");
        }, error -> {
            saveProfileButton.setText("Save Profile");
            showProfileEditState();
            Dialogs.showFailure(profileMessageLabel, "Cannot save profile", error);
        });
    }

    /** Change Password button: checks the current password, then saves the new one. */
    @FXML
    private void handleChangePassword() {
        final String currentPassword = currentPasswordField.getText();
        final String newPassword = newPasswordField.getText();
        final String confirmPassword = confirmPasswordField.getText();
        Dialogs.clearMessage(passwordMessageLabel);
        changePasswordButton.setDisable(true);
        changePasswordButton.setText("Changing...");
        // Hashing is slow on purpose (for security), so do it in the background.
        BackgroundTask.runVoid(() -> accountService.changeOwnPassword(currentPassword, newPassword, confirmPassword), () -> {
            changePasswordButton.setText("Change Password");
            clearPasswordFields();
            Dialogs.showSuccess(passwordMessageLabel, "Password changed. Use the new password next time you log in.");
        }, error -> {
            changePasswordButton.setText("Change Password");
            clearPasswordFields();
            Dialogs.showFailure(passwordMessageLabel, "Cannot change password", error);
        });
    }

    // ---- Helpers ----

    /** Clears the password boxes and resets the rule ticks. */
    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        showPasswordRules();
    }

    /** Initials of the first and last name, e.g. "JC" for Juan Cruz. */
    private static String initials(User user) {
        String first = user.getFirstName() == null || user.getFirstName().isEmpty() ? "" : user.getFirstName().substring(0, 1);
        String last = user.getLastName() == null || user.getLastName().isEmpty() ? "" : user.getLastName().substring(0, 1);
        String initials = (first + last).toUpperCase();
        return initials.isEmpty() ? "?" : initials;
    }

    /** Compares typed and saved values, ignoring spaces (blank counts as null). */
    private static boolean same(String typed, String saved) {
        String a = typed == null ? "" : typed.trim();
        String b = saved == null ? "" : saved.trim();
        return a.equals(b);
    }
}
