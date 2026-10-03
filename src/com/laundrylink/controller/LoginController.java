package com.laundrylink.controller;

import com.laundrylink.service.AuthService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.SessionContext;
import com.laundrylink.util.View;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Staff / Owner login screen.
 */
public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField visiblePasswordField;
    @FXML
    private CheckBox showPasswordCheckBox;
    @FXML
    private Label messageLabel;
    @FXML
    private Button loginButton;
    @FXML
    private Button backButton;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        // The plain text field mirrors the password field when "Show Password" is ticked.
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.visibleProperty().bind(showPasswordCheckBox.selectedProperty());
        visiblePasswordField.managedProperty().bind(showPasswordCheckBox.selectedProperty());
        passwordField.visibleProperty().bind(showPasswordCheckBox.selectedProperty().not());
        passwordField.managedProperty().bind(showPasswordCheckBox.selectedProperty().not());

        Platform.runLater(usernameField::requestFocus);
    }

    /** Used after first-run owner setup so the new owner only types the password. */
    public void prefillUsername(String username) {
        usernameField.setText(username);
        Platform.runLater(passwordField::requestFocus);
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        Dialogs.clearMessage(messageLabel);
        setBusy(true);

        BackgroundTask.run(() -> authService.authenticate(username, password),
                user -> {
                    SessionContext.signIn(user);
                    SceneNavigator.showScreen(View.MAIN_SHELL);
                },
                error -> {
                    setBusy(false);
                    passwordField.clear();
                    Dialogs.showFailure(messageLabel, "Login failed", error);
                    passwordField.requestFocus();
                });
    }

    @FXML
    private void handleBack() {
        SceneNavigator.showScreen(View.WELCOME);
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        backButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        visiblePasswordField.setDisable(busy);
        loginButton.setText(busy ? "Logging in..." : "Login");
    }
}
