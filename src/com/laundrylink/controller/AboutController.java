package com.laundrylink.controller;

import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.View;
import java.time.LocalDate;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * About LaundryLink: what the app does and who made it. Back goes to Welcome.
 */
public class AboutController {

    public static final String VERSION = "1.0";

    @FXML private Label versionLabel;
    @FXML private Label copyrightLabel;

    /** Shows the version number and the current year. */
    @FXML
    private void initialize() {
        versionLabel.setText("Version " + VERSION);
        copyrightLabel.setText("LaundryLink " + LocalDate.now().getYear());
    }

    /** Back button: returns to the Welcome screen. */
    @FXML
    private void handleBack() {
        SceneNavigator.showScreen(View.WELCOME);
    }
}
