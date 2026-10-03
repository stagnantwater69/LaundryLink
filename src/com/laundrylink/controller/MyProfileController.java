package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AccountService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SessionContext;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * My Profile: every user edits only their own name/username and password.
 */
public class MyProfileController {

    @FXML
    private TextField firstNameField;
    @FXML
    private TextField middleNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private Label roleLabel;
    @FXML
    private Label profileMessageLabel;
    @FXML
    private Button saveProfileButton;
    @FXML
    private PasswordField currentPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label passwordMessageLabel;
    @FXML
    private Button changePasswordButton;

    private final AccountService accountService = new AccountService();

    @FXML
    private void initialize() {
        showUser(SessionContext.getCurrentUser());
    }

    private void showUser(User user) {
        if (user == null) {
            return;
        }
        firstNameField.setText(user.getFirstName());
        middleNameField.setText(user.getMiddleName() == null ? "" : user.getMiddleName());
        lastNameField.setText(user.getLastName());
        usernameField.setText(user.getUsername());
        roleLabel.setText(user.getRole().getDisplayName());
    }

    @FXML
    private void handleSaveProfile() {
        String firstName = firstNameField.getText();
        String middleName = middleNameField.getText();
        String lastName = lastNameField.getText();
        String username = usernameField.getText();
        Dialogs.clearMessage(profileMessageLabel);
        saveProfileButton.setDisable(true);

        BackgroundTask.run(() -> accountService.updateOwnProfile(firstName, middleName, lastName, username),
                updated -> {
                    saveProfileButton.setDisable(false);
                    SessionContext.signIn(updated);
                    showUser(updated);
                    Dialogs.showSuccess(profileMessageLabel, "Profile saved.");
                },
                error -> {
                    saveProfileButton.setDisable(false);
                    Dialogs.showFailure(profileMessageLabel, "Cannot save profile", error);
                });
    }

    @FXML
    private void handleChangePassword() {
        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        Dialogs.clearMessage(passwordMessageLabel);
        changePasswordButton.setDisable(true);

        BackgroundTask.runVoid(() -> accountService.changeOwnPassword(currentPassword, newPassword, confirmPassword),
                () -> {
                    changePasswordButton.setDisable(false);
                    clearPasswordFields();
                    Dialogs.showSuccess(passwordMessageLabel, "Password changed. Use the new password next time you log in.");
                },
                error -> {
                    changePasswordButton.setDisable(false);
                    clearPasswordFields();
                    Dialogs.showFailure(passwordMessageLabel, "Cannot change password", error);
                });
    }

    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
    }
}
