package com.laundrylink.controller;

import com.laundrylink.model.DashboardStats;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.service.DashboardService;
import com.laundrylink.service.OrderService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.View;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/**
 * Dashboard tab: the day's totals, current workload, recent orders and order details.
 */
public class DashboardController {

    private static final String ALL_STATUSES = "All Statuses";
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    @FXML private DatePicker datePicker;
    @FXML private Label ordersReceivedLabel;
    @FXML private Label paymentsCollectedLabel;
    @FXML private Label readyForPickupLabel;
    @FXML private Label releasedLabel;
    @FXML private Label outstandingBalanceLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private TableView<OrderSummary> ordersTable;
    @FXML private TableColumn<OrderSummary, String> orderNumberColumn;
    @FXML private TableColumn<OrderSummary, String> customerColumn;
    @FXML private TableColumn<OrderSummary, String> serviceColumn;
    @FXML private TableColumn<OrderSummary, String> totalColumn;
    @FXML private TableColumn<OrderSummary, String> statusColumn;
    @FXML private TableColumn<OrderSummary, String> paymentColumn;
    @FXML private Label ordersPlaceholderLabel;
    @FXML private Label detailOrderNumberLabel;
    @FXML private Label detailCustomerLabel;
    @FXML private Label detailServiceLabel;
    @FXML private Label detailQuantityLabel;
    @FXML private Label detailTotalLabel;
    @FXML private Label detailPaidLabel;
    @FXML private Label detailBalanceLabel;
    @FXML private Button recordPaymentButton;
    @FXML private Button viewOrderButton;
    @FXML private Button releaseOrderButton;
    @FXML private HBox releaseMessageBox;
    @FXML private Label releaseMessageLabel;
    @FXML private Label flowReceivedLabel;
    @FXML private Label flowWashingLabel;
    @FXML private Label flowProcessingLabel;
    @FXML private Label flowDryingLabel;
    @FXML private Label flowIroningLabel;
    @FXML private Label flowReadyLabel;

    private final DashboardService dashboardService = new DashboardService();
    private final OrderService orderService = new OrderService();

    // All recent orders; the table only shows the ones that match the filters.
    private List<OrderSummary> allOrders = new ArrayList<OrderSummary>();
    private DashboardStats currentStats;

    /** Sets up the date, filter and table, then loads today's data. */
    @FXML
    private void initialize() {
        datePicker.setConverter(new LongDateConverter());
        datePicker.setValue(DashboardService.today());

        List<String> statusOptions = new ArrayList<String>();
        statusOptions.add(ALL_STATUSES);
        for (OrderStatus status : OrderStatus.values()) {
            statusOptions.add(status.getDisplayName());
        }
        statusFilterComboBox.setItems(FXCollections.observableArrayList(statusOptions));
        statusFilterComboBox.setValue(ALL_STATUSES);

        orderNumberColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("orderNumber"));
        customerColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("customerName"));
        serviceColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("services"));
        totalColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("totalText"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("statusText"));
        paymentColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("paymentText"));

        showDetails(null);
        refreshAll(-1);
    }

    // ---- Loading ----

    /** Reloads everything, then re-selects the given order if it's still listed. */
    private void refreshAll(int selectOrderId) {
        loadStats();
        loadOrders(selectOrderId);
    }

    /** Another date was picked. */
    @FXML
    private void handleDateChanged() {
        loadStats();
    }

    /** Loads the totals for the picked date. */
    private void loadStats() {
        final LocalDate day = datePicker.getValue();
        AppShell.setStatus("Loading dashboard...");
        BackgroundTask.run(() -> dashboardService.loadStats(day), stats -> {
            if (!day.equals(datePicker.getValue())) {
                return;     // the user picked another date meanwhile
            }
            currentStats = stats;
            showStats(stats);
            AppShell.setStatus("Ready.  Figures for " + day.format(LONG_DATE)
                    + ", updated " + LocalTime.now(DashboardService.SHOP_ZONE).format(TIME) + ".");
        }, error -> {
            AppShell.setStatus("Could not load dashboard figures.");
            Dialogs.error("Cannot load dashboard", error);
        });
    }

    /** Loads recent orders and shows the ones that match the filter. */
    private void loadOrders(final int selectOrderId) {
        ordersPlaceholderLabel.setText("Loading orders...");
        BackgroundTask.run(() -> dashboardService.loadRecentOrders(), loaded -> {
            allOrders = loaded;
            applyFilter();
            selectOrder(selectOrderId);
        }, error -> {
            ordersPlaceholderLabel.setText("Orders could not be loaded.");
            Dialogs.error("Cannot load orders", error);
        });
    }

    /** Puts the day's figures and workload counts on the cards. */
    private void showStats(DashboardStats stats) {
        ordersReceivedLabel.setText(String.valueOf(stats.getOrdersReceived()));
        paymentsCollectedLabel.setText(MoneyFormat.php(stats.getPaymentsCollected()));
        readyForPickupLabel.setText(String.valueOf(stats.getReadyForPickup()));
        releasedLabel.setText(String.valueOf(stats.getReleased()));
        outstandingBalanceLabel.setText(MoneyFormat.php(stats.getOutstandingBalance()));

        flowReceivedLabel.setText(String.valueOf(stats.getReceivedCount()));
        flowWashingLabel.setText(String.valueOf(stats.getWashingCount()));
        flowDryingLabel.setText(String.valueOf(stats.getDryingCount()));
        flowIroningLabel.setText(String.valueOf(stats.getIroningCount()));
        flowReadyLabel.setText(String.valueOf(stats.getReadyForPickup()));
        int processing = stats.getProcessingCount();
        flowProcessingLabel.setText(processing + (processing == 1 ? " order" : " orders"));
    }

    // ---- Recent orders ----

    /** Search text or status filter changed. */
    @FXML
    private void handleFilter() {
        applyFilter();
    }

    /** Shows only the orders that match the search and filter. */
    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String statusName = statusFilterComboBox.getValue();

        List<OrderSummary> shown = new ArrayList<OrderSummary>();
        for (OrderSummary order : allOrders) {
            boolean statusMatches = statusName == null || ALL_STATUSES.equals(statusName)
                    || order.getStatusText().equals(statusName);
            boolean textMatches = query.isEmpty()
                    || order.getOrderNumber().toLowerCase().contains(query)
                    || containsAllWords(order.getCustomerName(), query);
            if (statusMatches && textMatches) {
                shown.add(order);
            }
        }
        ordersTable.setItems(FXCollections.observableArrayList(shown));

        if (allOrders.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders yet.");
        } else if (shown.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders match your search.");
        }
    }

    /** True if every word of the query is in the name ("maria santos" finds "Maria Lopez Santos"). */
    private static boolean containsAllWords(String name, String query) {
        String lowerName = name.toLowerCase();
        String[] words = query.split(" ");
        for (String word : words) {
            if (!word.isEmpty() && !lowerName.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Selects this order, or the first one if it isn't listed. */
    private void selectOrder(int orderId) {
        for (OrderSummary order : ordersTable.getItems()) {
            if (order.getId() == orderId) {
                ordersTable.getSelectionModel().select(order);
                ordersTable.scrollTo(order);
                showDetails(order);
                return;
            }
        }
        if (ordersTable.getItems().isEmpty()) {
            showDetails(null);
        } else {
            ordersTable.getSelectionModel().selectFirst();
            showDetails(ordersTable.getSelectionModel().getSelectedItem());
        }
    }

    /** A row in the order list was picked. */
    @FXML
    private void handleOrderSelected() {
        showDetails(ordersTable.getSelectionModel().getSelectedItem());
    }

    // ---- Order details ----

    /** Fills the order panel, or clears it when order is null. */
    private void showDetails(OrderSummary order) {
        if (order == null) {
            detailOrderNumberLabel.setText("—");
            detailCustomerLabel.setText("Select an order");
            detailServiceLabel.setText("—");
            detailQuantityLabel.setText("—");
            detailTotalLabel.setText("—");
            detailPaidLabel.setText("—");
            detailBalanceLabel.setText("—");
            recordPaymentButton.setDisable(true);
            viewOrderButton.setDisable(true);
            releaseOrderButton.setDisable(true);
            showReleaseMessage(null);
            return;
        }

        detailOrderNumberLabel.setText(order.getOrderNumber());
        detailCustomerLabel.setText(order.getCustomerName());
        detailServiceLabel.setText(order.getServices().isEmpty() ? "—" : order.getServices());
        detailTotalLabel.setText(MoneyFormat.php(order.getTotalAmount()));
        detailPaidLabel.setText(MoneyFormat.php(order.getAmountPaid()));
        detailBalanceLabel.setText(MoneyFormat.php(order.getBalance()));

        boolean open = order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RELEASED;
        recordPaymentButton.setDisable(!open || order.getBalance().signum() <= 0);
        viewOrderButton.setDisable(false);
        releaseOrderButton.setDisable(!order.isReleasable());
        showReleaseMessage(releaseBlockReason(order));

        // Load the items in the background; skip them if another order was picked meanwhile.
        final int orderId = order.getId();
        detailQuantityLabel.setText("Loading...");
        BackgroundTask.run(() -> dashboardService.loadOrderItems(orderId), items -> {
            OrderSummary selected = ordersTable.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getId() == orderId) {
                showItems(items);
            }
        }, error -> detailQuantityLabel.setText("Could not load items."));
    }

    /** Shows each item as "4 kg x PHP 65.00", one per line. */
    private void showItems(List<OrderItemLine> items) {
        if (items.isEmpty()) {
            detailQuantityLabel.setText("No items");
            return;
        }
        StringBuilder lines = new StringBuilder();
        for (OrderItemLine item : items) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            if (items.size() > 1) {
                lines.append(item.getServiceName()).append(": ");
            }
            lines.append(MoneyFormat.quantityTimesPrice(item.getQuantity(), item.getPricingUnit(), item.getUnitPrice()));
        }
        detailQuantityLabel.setText(lines.toString());
    }

    /** Why the order can't be released yet, or null if it can. */
    private String releaseBlockReason(OrderSummary order) {
        boolean unpaid = order.getBalance().signum() > 0;
        switch (order.getStatus()) {
            case RELEASED:
            case CANCELLED:
                return null;
            case READY_FOR_PICKUP:
                return unpaid ? "Full payment required before release." : null;
            default:
                return unpaid
                        ? "Must be Ready for Pickup and fully paid before release."
                        : "Must be Ready for Pickup before release.";
        }
    }

    /** Shows or hides the red release warning. */
    private void showReleaseMessage(String message) {
        boolean show = message != null;
        releaseMessageLabel.setText(show ? message : "");
        releaseMessageBox.setVisible(show);
        releaseMessageBox.setManaged(show);
    }

    // ---- Actions ----

    /** View Daily Summary button: shows the day's figures in a pop-up. */
    @FXML
    private void handleViewDailySummary() {
        if (currentStats == null) {
            return;
        }
        Dialogs.info("Daily Summary - " + datePicker.getValue().format(LONG_DATE),
                "Selected day\n"
                + "   Orders received:      " + currentStats.getOrdersReceived() + "\n"
                + "   Payments collected:   " + MoneyFormat.php(currentStats.getPaymentsCollected()) + "\n"
                + "   Orders released:      " + currentStats.getReleased() + "\n\n"
                + "Current (as of now)\n"
                + "   Received:             " + currentStats.getReceivedCount() + "\n"
                + "   Washing:              " + currentStats.getWashingCount() + "\n"
                + "   Drying:               " + currentStats.getDryingCount() + "\n"
                + "   Ironing:              " + currentStats.getIroningCount() + "\n"
                + "   Ready for pickup:     " + currentStats.getReadyForPickup() + "\n"
                + "   Outstanding balance:  " + MoneyFormat.php(currentStats.getOutstandingBalance()) + "\n\n"
                + "Payments collected include payments for orders received on earlier days.");
    }

    /** New Order button: opens the Laundry Orders tab. */
    @FXML
    private void handleNewOrder() {
        AppShell.openTab(View.LAUNDRY_ORDERS);
    }

    /** Record Payment button: opens Payments for the selected order. */
    @FXML
    private void handleRecordPayment() {
        OrderSummary order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            AppShell.openTab(View.PAYMENTS);
        } else {
            PaymentsController.openForOrder(order.getId());
        }
    }

    /** View Order button: opens the selected order in Laundry Orders. */
    @FXML
    private void handleViewOrder() {
        OrderSummary order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            AppShell.openTab(View.LAUNDRY_ORDERS);
        } else {
            LaundryOrdersController.openOrder(order.getId());
        }
    }

    /** Release Order button: hands the order back to the customer. */
    @FXML
    private void handleReleaseOrder() {
        OrderSummary order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null || !Dialogs.confirm("Release order",
                "Release " + order.getOrderNumber() + " to " + order.getCustomerName() + "?")) {
            return;
        }
        releaseOrderButton.setDisable(true);
        final int orderId = order.getId();
        BackgroundTask.runVoid(() -> orderService.releaseOrder(orderId), () -> {
            Dialogs.info("Order released", order.getOrderNumber() + " has been released to "
                    + order.getCustomerName() + ".");
            refreshAll(orderId);
        }, error -> {
            Dialogs.error("Cannot release order", error);
            refreshAll(orderId);
        });
    }

    /** Shows dates in the date picker as "October 2, 2026". */
    private static class LongDateConverter extends StringConverter<LocalDate> {
        /** Date to text. */
        @Override
        public String toString(LocalDate date) {
            return date == null ? "" : date.format(LONG_DATE);
        }

        /** Text to date (null if it isn't valid). */
        @Override
        public LocalDate fromString(String text) {
            if (text == null || text.trim().isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(text.trim(), LONG_DATE);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
