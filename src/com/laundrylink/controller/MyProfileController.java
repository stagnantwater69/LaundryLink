package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AccountService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SessionContext;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * My Profile: every user edits only their own name/username and password.
 * The ID card at the top shows the saved account; the forms below edit it.
 */
public class MyProfileController {

    private static final DateTimeFormatter MEMBER_SINCE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    @FXML
    private Label monogramLabel;
    @FXML
    private Label fullNameLabel;
    @FXML
    private Label roleBadgeLabel;
    @FXML
    private Label usernameBadgeLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label accessLabel;
    @FXML
    private Label memberSinceLabel;
    @FXML
    private TextField firstNameField;
    @FXML
    private TextField middleNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private Label profileMessageLabel;
    @FXML
    private Label unsavedLabel;
    @FXML
    private Button resetProfileButton;
    @FXML
    private Button saveProfileButton;
    @FXML
    private PasswordField currentPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label lengthRuleLabel;
    @FXML
    private Label matchRuleLabel;
    @FXML
    private Label differentRuleLabel;
    @FXML
    private Label passwordMessageLabel;
    @FXML
    private Button changePasswordButton;

    private final AccountService accountService = new AccountService();

    /** The account as last saved; the form is compared against it to detect edits. */
    private User savedUser;
    private boolean saving;
    private boolean changingPassword;

    @FXML
    private void initialize() {
        ChangeListener<String> profileEdited = (obs, oldText, newText) -> showProfileEditState();
        firstNameField.textProperty().addListener(profileEdited);
        middleNameField.textProperty().addListener(profileEdited);
        lastNameField.textProperty().addListener(profileEdited);
        usernameField.textProperty().addListener(profileEdited);

        ChangeListener<String> passwordEdited = (obs, oldText, newText) -> showPasswordRules();
        currentPasswordField.textProperty().addListener(passwordEdited);
        newPasswordField.textProperty().addListener(passwordEdited);
        confirmPasswordField.textProperty().addListener(passwordEdited);

        showUser(SessionContext.getCurrentUser());
        showPasswordRules();
    }

    // ------------------------------------------------------------------
    // Display
    // ------------------------------------------------------------------

    private void showUser(User user) {
        if (user == null) {
            return;
        }
        savedUser = user;
        showIdCard(user);
        firstNameField.setText(user.getFirstName());
        middleNameField.setText(user.getMiddleName() == null ? "" : user.getMiddleName());
        lastNameField.setText(user.getLastName());
        usernameField.setText(user.getUsername());
        showProfileEditState();
    }

    private void showIdCard(User user) {
        monogramLabel.setText(initials(user));
        fullNameLabel.setText(user.getFullName());
        roleBadgeLabel.setText(user.getRole().getDisplayName().toUpperCase());
        roleBadgeLabel.getStyleClass().removeAll("owner", "staff");
        roleBadgeLabel.getStyleClass().add(user.isAdmin() ? "owner" : "staff");
        usernameBadgeLabel.setText("@" + user.getUsername());
        statusLabel.setText(user.isActive() ? "Active" : "Inactive");
        accessLabel.setText(user.isAdmin() ? "All modules and staff accounts" : "Daily shop operations");
        memberSinceLabel.setText(user.getCreatedAt() == null ? "—" : user.getCreatedAt().format(MEMBER_SINCE));
    }

    /** Save and Reset are enabled only when the form differs from the saved account. */
    private void showProfileEditState() {
        boolean edited = savedUser != null && (
                !same(firstNameField.getText(), savedUser.getFirstName())
                || !same(middleNameField.getText(), savedUser.getMiddleName())
                || !same(lastNameField.getText(), savedUser.getLastName())
                || !same(usernameField.getText(), savedUser.getUsername()));
        saveProfileButton.setDisable(saving || !edited);
        resetProfileButton.setDisable(saving || !edited);
        unsavedLabel.setVisible(edited);
        unsavedLabel.setManaged(edited);
        if (edited) {
            Dialogs.clearMessage(profileMessageLabel);
        }
    }

    /** Ticks each rule as it is met; AccountService checks the same rules again on save. */
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
        changePasswordButton.setDisable(changingPassword || current.isEmpty() || !longEnough || !matches || !different);
        if (!password.isEmpty() || !confirm.isEmpty()) {
            Dialogs.clearMessage(passwordMessageLabel);
        }
    }

    /** met: green tick; failed after typing: red cross; untouched: neutral bullet. */
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

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @FXML
    private void handleResetProfile() {
        Dialogs.clearMessage(profileMessageLabel);
        showUser(savedUser);
    }

    @FXML
    private void handleSaveProfile() {
        String firstName = firstNameField.getText();
        String middleName = middleNameField.getText();
        String lastName = lastNameField.getText();
        String username = usernameField.getText();
        Dialogs.clearMessage(profileMessageLabel);
        setSaving(true);

        BackgroundTask.run(() -> accountService.updateOwnProfile(firstName, middleName, lastName, username),
                updated -> {
                    setSaving(false);
                    SessionContext.signIn(updated);
                    showUser(updated);
                    Dialogs.showSuccess(profileMessageLabel, "Profile saved.");
                },
                error -> {
                    setSaving(false);
                    Dialogs.showFailure(profileMessageLabel, "Cannot save profile", error);
                });
    }

    @FXML
    private void handleChangePassword() {
        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        Dialogs.clearMessage(passwordMessageLabel);
        setChangingPassword(true);

        BackgroundTask.runVoid(() -> accountService.changeOwnPassword(currentPassword, newPassword, confirmPassword),
                () -> {
                    setChangingPassword(false);
                    clearPasswordFields();
                    Dialogs.showSuccess(passwordMessageLabel, "Password changed. Use the new password next time you log in.");
                },
                error -> {
                    setChangingPassword(false);
                    clearPasswordFields();
                    Dialogs.showFailure(passwordMessageLabel, "Cannot change password", error);
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setSaving(boolean busy) {
        saving = busy;
        saveProfileButton.setText(busy ? "Saving..." : "Save Profile");
        showProfileEditState();
    }

    private void setChangingPassword(boolean busy) {
        changingPassword = busy;
        changePasswordButton.setText(busy ? "Changing..." : "Change Password");
        showPasswordRules();
    }

    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }

    /** "JC" for Juan Cruz. */
    private static String initials(User user) {
        String first = user.getFirstName() == null || user.getFirstName().isEmpty() ? "" : user.getFirstName().substring(0, 1);
        String last = user.getLastName() == null || user.getLastName().isEmpty() ? "" : user.getLastName().substring(0, 1);
        String initials = (first + last).toUpperCase();
        return initials.isEmpty() ? "?" : initials;
    }

    /** Compares a typed value with a saved one, ignoring surrounding spaces; blank equals null. */
    private static boolean same(String typed, String saved) {
        String a = typed == null ? "" : typed.trim();
        String b = saved == null ? "" : saved.trim();
        return Objects.equals(a, b);
    }
}
