package com.laundrylink.controller;

import com.laundrylink.service.AccountService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.View;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * First-run Owner Setup: creates the one owner account.
 */
public class OwnerSetupController {

    @FXML
    private TextField firstNameField;
    @FXML
    private TextField middleNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label messageLabel;
    @FXML
    private Button createButton;
    @FXML
    private Button backButton;

    private final AccountService accountService = new AccountService();

    @FXML
    private void initialize() {
        Platform.runLater(firstNameField::requestFocus);
    }

    @FXML
    private void handleCreateOwner() {
        String firstName = firstNameField.getText();
        String middleName = middleNameField.getText();
        String lastName = lastNameField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        Dialogs.clearMessage(messageLabel);
        setBusy(true);

        BackgroundTask.run(() -> accountService.registerOwner(firstName, middleName, lastName, username, password, confirmPassword),
                owner -> {
                    Dialogs.info("Owner account created",
                            "Welcome, " + owner.getFirstName() + "!\n\n"
                            + "Your owner account is ready. Log in to start using LaundryLink.");
                    LoginController login = SceneNavigator.showScreen(View.LOGIN);
                    login.prefillUsername(owner.getUsername());
                },
                error -> {
                    setBusy(false);
                    passwordField.clear();
                    confirmPasswordField.clear();
                    Dialogs.showFailure(messageLabel, "Owner setup failed", error);
                });
    }

    @FXML
    private void handleBack() {
        SceneNavigator.showScreen(View.WELCOME);
    }

    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        backButton.setDisable(busy);
        createButton.setText(busy ? "Creating account..." : "Create Owner Account");
    }
}
