package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AuthService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.SessionContext;
import com.laundrylink.util.View;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Login screen for shop owners and staff.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Label messageLabel;
    @FXML private Button loginButton;
    @FXML private Button backButton;

    private final AuthService authService = new AuthService();

    /** Puts the cursor in the username box. */
    @FXML
    private void initialize() {
        usernameField.requestFocus();
    }

    /** Fills in the username after registering, so the new owner only types the password. */
    public void prefillUsername(String username) {
        usernameField.setText(username);
        passwordField.requestFocus();
    }

    /** Show Password box: switches between hidden and visible password. */
    @FXML
    private void handleShowPassword() {
        boolean show = showPasswordCheckBox.isSelected();
        if (show) {
            visiblePasswordField.setText(passwordField.getText());
        } else {
            passwordField.setText(visiblePasswordField.getText());
        }
        visiblePasswordField.setVisible(show);
        visiblePasswordField.setManaged(show);
        passwordField.setVisible(!show);
        passwordField.setManaged(!show);
    }

    /** Login button: checks the username and password, then opens the main window. */
    @FXML
    private void handleLogin() {
        final String username = usernameField.getText();
        final String password = showPasswordCheckBox.isSelected() ? visiblePasswordField.getText() : passwordField.getText();
        Dialogs.clearMessage(messageLabel);
        setBusy(true);
        // Checking a password is slow on purpose (for security), so do it in the background.
        BackgroundTask.run(() -> authService.authenticate(username, password), user -> {
            SessionContext.signIn(user);
            SceneNavigator.showScreen(View.MAIN_SHELL);
        }, error -> {
            setBusy(false);
            passwordField.clear();
            visiblePasswordField.clear();
            Dialogs.showFailure(messageLabel, "Login failed", error);
            passwordField.requestFocus();
        });
    }

    /** Disables the form while checking the password. */
    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        backButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        visiblePasswordField.setDisable(busy);
        loginButton.setText(busy ? "Logging in..." : "Login");
    }

    /** "Register your shop" link: goes to Register. */
    @FXML
    private void handleRegister() {
        SceneNavigator.showScreen(View.REGISTER);
    }

    /** Back button: goes to Welcome. */
    @FXML
    private void handleBack() {
        SceneNavigator.showScreen(View.WELCOME);
    }
}
