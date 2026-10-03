package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AuthService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.SessionContext;
import com.laundrylink.util.View;
import java.util.Arrays;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * Application shell after login: header with the logged-in user, top tab bar,
 * a content area where module screens are shown, and a status bar.
 */
public class MainShellController {

    @FXML
    private Hyperlink userNameLink;
    @FXML
    private Label userRoleLabel;
    @FXML
    private Button dashboardTab;
    @FXML
    private Button customersTab;
    @FXML
    private Button laundryOrdersTab;
    @FXML
    private Button paymentsTab;
    @FXML
    private Button servicesTab;
    @FXML
    private Button staffAccountsTab;
    @FXML
    private StackPane contentArea;
    @FXML
    private Label statusLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        if (!SessionContext.isLoggedIn()) {
            Platform.runLater(() -> SceneNavigator.showScreen(View.LOGIN));
            return;
        }

        userNameLink.textProperty().bind(Bindings.createStringBinding(() -> {
            User user = SessionContext.getCurrentUser();
            return user == null ? "" : user.getFullName();
        }, SessionContext.currentUserProperty()));
        userRoleLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            User user = SessionContext.getCurrentUser();
            return user == null ? "" : user.getRole().getDisplayName();
        }, SessionContext.currentUserProperty()));
        statusLabel.textProperty().bind(AppShell.statusProperty());

        // Staff never see owner-only tabs; the service layer also rejects them.
        boolean admin = SessionContext.isAdmin();
        staffAccountsTab.setVisible(admin);
        staffAccountsTab.setManaged(admin);

        AppShell.setTabOpener(this::showTab);
        AppShell.setStatus("Ready.");
        showTab(View.DASHBOARD);
    }

    @FXML
    private void handleShowDashboard() {
        showTab(View.DASHBOARD);
    }

    @FXML
    private void handleShowCustomers() {
        showTab(View.CUSTOMERS);
    }

    @FXML
    private void handleShowLaundryOrders() {
        showTab(View.LAUNDRY_ORDERS);
    }

    @FXML
    private void handleShowPayments() {
        showTab(View.PAYMENTS);
    }

    @FXML
    private void handleShowServices() {
        showTab(View.SERVICES_PRICES);
    }

    @FXML
    private void handleShowStaffAccounts() {
        showTab(View.STAFF_ACCOUNTS);
    }

    @FXML
    private void handleShowProfile() {
        showTab(View.MY_PROFILE);
    }

    @FXML
    private void handleLogout() {
        if (Dialogs.confirm("Log out", "Log out of LaundryLink?")) {
            authService.logout();
            SceneNavigator.showScreen(View.LOGIN);
        }
    }

    private void showTab(View view) {
        if (view == View.STAFF_ACCOUNTS && !SessionContext.isAdmin()) {
            return;
        }
        AppShell.setStatus("Ready.");
        contentArea.getChildren().setAll(SceneNavigator.load(view).getRoot());

        List<Button> tabs = Arrays.asList(dashboardTab, customersTab, laundryOrdersTab, paymentsTab,
                servicesTab, staffAccountsTab);
        for (Button tab : tabs) {
            tab.getStyleClass().remove("active");
        }
        Button activeTab = tabFor(view);
        if (activeTab != null) {
            activeTab.getStyleClass().add("active");
        }
    }

    private Button tabFor(View view) {
        switch (view) {
            case DASHBOARD:
                return dashboardTab;
            case CUSTOMERS:
                return customersTab;
            case LAUNDRY_ORDERS:
                return laundryOrdersTab;
            case PAYMENTS:
                return paymentsTab;
            case SERVICES_PRICES:
                return servicesTab;
            case STAFF_ACCOUNTS:
                return staffAccountsTab;
            default:
                return null; // My Profile has no tab; it opens from the user badge.
        }
    }
}
