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

/**
 * Welcome screen. "Staff / Owner Login" opens the first-run Owner Setup when no
 * owner account exists yet, otherwise the normal Login screen.
 */
public class WelcomeController {

    @FXML
    private Button loginButton;
    @FXML
    private Label loginButtonLabel;

    private final AccountService accountService = new AccountService();

    @FXML
    private void handleLogin() {
        setBusy(true);
        BackgroundTask.run(accountService::isOwnerSetupRequired,
                setupRequired -> SceneNavigator.showScreen(setupRequired ? View.OWNER_SETUP : View.LOGIN),
                error -> {
                    setBusy(false);
                    Dialogs.error("Cannot open login", error);
                });
    }

    @FXML
    private void handleAbout() {
        Dialogs.info("About LaundryLink",
                "LaundryLink - Laundry Shop Management\nVersion 1.0\n\n"
                + "A desktop application for a single laundry shop: customer records, "
                + "laundry orders and status tracking, cash payments, and daily summaries.\n\n"
                + "For shop staff and the shop owner only.");
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        loginButtonLabel.setText(busy ? "Please wait..." : "Staff / Owner Login");
    }
}
