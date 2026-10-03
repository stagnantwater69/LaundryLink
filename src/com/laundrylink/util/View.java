package com.laundrylink.util;

/**
 * Every FXML screen in the application. FXML files live in com/laundrylink/view.
 */
public enum View {
    // Full-window screens
    WELCOME("welcome.fxml"),
    LOGIN("login.fxml"),
    OWNER_SETUP("owner-setup.fxml"),
    MAIN_SHELL("main-shell.fxml"),

    // Tabs inside the main shell (same order as the tab bar)
    DASHBOARD("dashboard.fxml"),
    CUSTOMERS("customers.fxml"),
    LAUNDRY_ORDERS("laundry-orders.fxml"),
    PAYMENTS("payments.fxml"),
    SERVICES_PRICES("services-prices.fxml"),
    STAFF_ACCOUNTS("staff-accounts.fxml"),
    MY_PROFILE("my-profile.fxml");

    private static final String VIEW_FOLDER = "/com/laundrylink/view/";

    private final String fileName;

    View(String fileName) {
        this.fileName = fileName;
    }

    public String getPath() {
        return VIEW_FOLDER + fileName;
    }
}
