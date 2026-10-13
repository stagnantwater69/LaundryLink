package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AuthService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.SessionContext;
import com.laundrylink.util.View;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * The main window after login: header, tab bar, tab content and status bar.
 */
public class MainShellController {

    @FXML private Hyperlink userNameLink;
    @FXML private Label userRoleLabel;
    @FXML private Button dashboardTab;
    @FXML private Button customersTab;
    @FXML private Button laundryOrdersTab;
    @FXML private Button paymentsTab;
    @FXML private Button servicesTab;
    @FXML private Button staffAccountsTab;
    @FXML private StackPane contentArea;
    @FXML private Label statusLabel;

    private final AuthService authService = new AuthService();

    /** Shows the user, hides owner-only tabs from staff, and opens the Dashboard. */
    @FXML
    private void initialize() {
        AppShell.setShell(this, statusLabel);
        showUserBadge();

        // Staff don't see owner-only tabs (the services block them too).
        boolean admin = SessionContext.isAdmin();
        staffAccountsTab.setVisible(admin);
        staffAccountsTab.setManaged(admin);

        AppShell.setStatus("Ready.");
        showTab(View.DASHBOARD);
    }

    /** Shows the user's name, role and shop at the top right. */
    public void showUserBadge() {
        User user = SessionContext.getCurrentUser();
        if (user == null) {
            userNameLink.setText("");
            userRoleLabel.setText("");
        } else {
            userNameLink.setText(user.getFullName());
            userRoleLabel.setText(user.getRole().getDisplayName() + " - " + user.getShopName());
        }
    }

    /** Dashboard tab button. */
    @FXML
    private void handleShowDashboard() {
        showTab(View.DASHBOARD);
    }

    /** Customers tab button. */
    @FXML
    private void handleShowCustomers() {
        showTab(View.CUSTOMERS);
    }

    /** Laundry Orders tab button. */
    @FXML
    private void handleShowLaundryOrders() {
        showTab(View.LAUNDRY_ORDERS);
    }

    /** Payments tab button. */
    @FXML
    private void handleShowPayments() {
        showTab(View.PAYMENTS);
    }

    /** Services & Prices tab button. */
    @FXML
    private void handleShowServices() {
        showTab(View.SERVICES_PRICES);
    }

    /** Staff Accounts tab button. */
    @FXML
    private void handleShowStaffAccounts() {
        showTab(View.STAFF_ACCOUNTS);
    }

    /** The user's name opens My Profile. */
    @FXML
    private void handleShowProfile() {
        showTab(View.MY_PROFILE);
    }

    /** Logout link: asks first, then goes back to Login. */
    @FXML
    private void handleLogout() {
        if (Dialogs.confirm("Log out", "Log out of LaundryLink?")) {
            authService.logout();
            SceneNavigator.showScreen(View.LOGIN);
        }
    }

    /** Loads a tab into the content area and highlights its button. */
    public void showTab(View view) {
        if (view == View.STAFF_ACCOUNTS && !SessionContext.isAdmin()) {
            return;
        }
        AppShell.setStatus("Ready.");
        contentArea.getChildren().setAll(SceneNavigator.loadView(view));

        Button[] tabs = {dashboardTab, customersTab, laundryOrdersTab, paymentsTab, servicesTab, staffAccountsTab};
        for (Button tab : tabs) {
            tab.getStyleClass().remove("active");
        }
        Button activeTab = tabFor(view);
        if (activeTab != null) {
            activeTab.getStyleClass().add("active");
        }
    }

    /** The tab button for a view (My Profile has none; it opens from the user's name). */
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
                return null;
        }
    }
}
