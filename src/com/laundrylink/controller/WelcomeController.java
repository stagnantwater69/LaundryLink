package com.laundrylink.controller;

import com.laundrylink.service.AccountService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.View;
import java.sql.SQLException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Welcome screen: Login (or Register when no shop exists yet), About, and Exit.
 */
public class WelcomeController {

    @FXML private Button loginButton;
    @FXML private Label loginButtonLabel;

    private final AccountService accountService = new AccountService();

    /** Login button: opens Register if there's no shop yet, otherwise Login. */
    @FXML
    private void handleLogin() {
        loginButton.setDisable(true);
        loginButtonLabel.setText("Please wait...");
        BackgroundTask.run(() -> accountService.isShopRegistrationRequired(), registrationRequired -> {
            if (registrationRequired) {
                SceneNavigator.showScreen(View.REGISTER);
            } else {
                SceneNavigator.showScreen(View.LOGIN);
            }
        }, error -> {
            loginButton.setDisable(false);
            loginButtonLabel.setText("Staff / Owner Login");
            Dialogs.error("Cannot open login", error);
        });
    }

    /** About LaundryLink button: opens the About page. */
    @FXML
    private void handleAbout() {
        SceneNavigator.showScreen(View.ABOUT);
    }

    /** Exit Application button: closes the program. */
    @FXML
    private void handleExit() {
        Platform.exit();
    }
}
