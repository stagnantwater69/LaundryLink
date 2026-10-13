package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AccountService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.View;
import java.sql.SQLException;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Register Your Shop: a new owner creates their shop and account here.
 * Each shop's data is kept apart from the others.
 */
public class RegisterController {

    @FXML private TextField shopNameField;
    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField visibleConfirmField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Label messageLabel;
    @FXML private Button createButton;
    @FXML private Button backButton;

    private final AccountService accountService = new AccountService();

    /** Puts the cursor in the Shop Name box. */
    @FXML
    private void initialize() {
        shopNameField.requestFocus();
    }

    /** Show Password box: switches between hidden and visible password. */
    @FXML
    private void handleShowPassword() {
        boolean show = showPasswordCheckBox.isSelected();
        if (show) {
            visiblePasswordField.setText(passwordField.getText());
            visibleConfirmField.setText(confirmPasswordField.getText());
        } else {
            passwordField.setText(visiblePasswordField.getText());
            confirmPasswordField.setText(visibleConfirmField.getText());
        }
        setShown(visiblePasswordField, show);
        setShown(visibleConfirmField, show);
        setShown(passwordField, !show);
        setShown(confirmPasswordField, !show);
    }

    /** Register Shop button: creates the shop and owner, then opens Login. */
    @FXML
    private void handleRegister() {
        Dialogs.clearMessage(messageLabel);
        final String password = showPasswordCheckBox.isSelected() ? visiblePasswordField.getText() : passwordField.getText();
        final String confirm = showPasswordCheckBox.isSelected() ? visibleConfirmField.getText() : confirmPasswordField.getText();

        final String shopName = shopNameField.getText();
        final String firstName = firstNameField.getText();
        final String middleName = middleNameField.getText();
        final String lastName = lastNameField.getText();
        final String username = usernameField.getText();
        setBusy(true);
        // Hashing is slow on purpose (for security), so do it in the background.
        BackgroundTask.run(() -> accountService.registerShopOwner(shopName, firstName, middleName, lastName, username,
                password, confirm), owner -> {
            Dialogs.info("Shop registered",
                    "Welcome, " + owner.getFirstName() + "!\n\n"
                    + owner.getShopName() + " is ready. Log in to start managing your shop.");
            LoginController login = (LoginController) SceneNavigator.showScreen(View.LOGIN);
            login.prefillUsername(owner.getUsername());
        }, error -> {
            setBusy(false);
            clearPasswords();
            Dialogs.showFailure(messageLabel, "Registration failed", error);
        });
    }

    /** Login link: goes to Login. */
    @FXML
    private void handleGoToLogin() {
        SceneNavigator.showScreen(View.LOGIN);
    }

    /** Back button: goes to Welcome. */
    @FXML
    private void handleBack() {
        SceneNavigator.showScreen(View.WELCOME);
    }

    /** Clears the password boxes. */
    private void clearPasswords() {
        passwordField.clear();
        confirmPasswordField.clear();
        visiblePasswordField.clear();
        visibleConfirmField.clear();
    }

    /** Disables the buttons while registering. */
    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        backButton.setDisable(busy);
        if (busy) {
            createButton.setText("Registering...");
        } else {
            createButton.setText("Register Shop");
        }
    }

    /** Shows or hides a text box. */
    private static void setShown(TextField field, boolean shown) {
        field.setVisible(shown);
        field.setManaged(shown);
    }
}
