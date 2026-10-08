package com.laundrylink.controller;

import com.laundrylink.model.DashboardStats;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.service.DashboardService;
import com.laundrylink.service.OrderService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.View;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/**
 * Daily dashboard: selected-day totals, current workload, recent orders,
 * and the selected order's details.
 */
public class DashboardController {

    private static final String ALL_STATUSES = "All Statuses";
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    @FXML
    private DatePicker datePicker;
    @FXML
    private Label ordersReceivedLabel;
    @FXML
    private Label paymentsCollectedLabel;
    @FXML
    private Label readyForPickupLabel;
    @FXML
    private Label releasedLabel;
    @FXML
    private Label outstandingBalanceLabel;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private TableView<OrderSummary> ordersTable;
    @FXML
    private TableColumn<OrderSummary, String> orderNumberColumn;
    @FXML
    private TableColumn<OrderSummary, String> customerColumn;
    @FXML
    private TableColumn<OrderSummary, String> serviceColumn;
    @FXML
    private TableColumn<OrderSummary, String> totalColumn;
    @FXML
    private TableColumn<OrderSummary, String> statusColumn;
    @FXML
    private TableColumn<OrderSummary, String> paymentColumn;
    @FXML
    private Label ordersPlaceholderLabel;
    @FXML
    private Label detailOrderNumberLabel;
    @FXML
    private Label detailCustomerLabel;
    @FXML
    private Label detailServiceLabel;
    @FXML
    private Label detailQuantityLabel;
    @FXML
    private Label detailTotalLabel;
    @FXML
    private Label detailPaidLabel;
    @FXML
    private Label detailBalanceLabel;
    @FXML
    private Button recordPaymentButton;
    @FXML
    private Button viewOrderButton;
    @FXML
    private Button releaseOrderButton;
    @FXML
    private HBox releaseMessageBox;
    @FXML
    private Label releaseMessageLabel;
    @FXML
    private Label flowReceivedLabel;
    @FXML
    private Label flowWashingLabel;
    @FXML
    private Label flowProcessingLabel;
    @FXML
    private Label flowDryingLabel;
    @FXML
    private Label flowReadyLabel;

    private final DashboardService dashboardService = new DashboardService();
    private final OrderService orderService = new OrderService();
    private final ObservableList<OrderSummary> orders = FXCollections.observableArrayList();
    private final FilteredList<OrderSummary> filteredOrders = new FilteredList<>(orders, order -> true);

    private DashboardStats currentStats;

    @FXML
    private void initialize() {
        datePicker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(LONG_DATE);
            }

            @Override
            public LocalDate fromString(String text) {
                return text == null || text.trim().isEmpty() ? null : LocalDate.parse(text.trim(), LONG_DATE);
            }
        });
        datePicker.setValue(DashboardService.today());
        datePicker.valueProperty().addListener((obs, oldDate, newDate) -> loadStats());

        List<String> statusOptions = new ArrayList<>();
        statusOptions.add(ALL_STATUSES);
        for (OrderStatus status : OrderStatus.values()) {
            statusOptions.add(status.getDisplayName());
        }
        statusFilterComboBox.setItems(FXCollections.observableArrayList(statusOptions));
        statusFilterComboBox.setValue(ALL_STATUSES);
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter());

        setUpTable();
        showDetails(null);
        refreshAll(-1);
    }

    private void setUpTable() {
        ordersTable.setItems(filteredOrders);
        orderNumberColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getOrderNumber()));
        customerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getCustomerName()));
        serviceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getServices()));
        totalColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getTotalAmount())));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus().getDisplayName()));
        paymentColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getPaymentStatus().getDisplayName()));
        ordersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldOrder, newOrder) -> showDetails(newOrder));
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Reloads figures and the order list; re-selects the given order if it is still listed. */
    private void refreshAll(int selectOrderId) {
        loadStats();
        loadOrders(selectOrderId);
    }

    private void loadStats() {
        LocalDate day = datePicker.getValue();
        AppShell.setStatus("Loading dashboard...");
        BackgroundTask.run(() -> dashboardService.loadStats(day),
                stats -> {
                    currentStats = stats;
                    showStats(stats);
                    AppShell.setStatus("Ready.  Figures for " + day.format(LONG_DATE)
                            + ", updated " + LocalTime.now(DashboardService.SHOP_ZONE).format(TIME) + ".");
                },
                error -> {
                    AppShell.setStatus("Could not load dashboard figures.");
                    Dialogs.error("Cannot load dashboard", error);
                });
    }

    private void loadOrders(int selectOrderId) {
        ordersPlaceholderLabel.setText("Loading orders...");
        BackgroundTask.run(dashboardService::loadRecentOrders,
                (List<OrderSummary> loaded) -> {
                    ordersPlaceholderLabel.setText("No orders yet.");
                    orders.setAll(loaded);
                    applyFilter();
                    selectOrder(selectOrderId);
                },
                error -> {
                    ordersPlaceholderLabel.setText("Orders could not be loaded.");
                    Dialogs.error("Cannot load orders", error);
                });
    }

    private void showStats(DashboardStats stats) {
        ordersReceivedLabel.setText(String.valueOf(stats.getOrdersReceived()));
        paymentsCollectedLabel.setText(MoneyFormat.php(stats.getPaymentsCollected()));
        readyForPickupLabel.setText(String.valueOf(stats.getReadyForPickup()));
        releasedLabel.setText(String.valueOf(stats.getReleased()));
        outstandingBalanceLabel.setText(MoneyFormat.php(stats.getOutstandingBalance()));

        flowReceivedLabel.setText(String.valueOf(stats.getReceivedCount()));
        flowWashingLabel.setText(String.valueOf(stats.getWashingCount()));
        flowDryingLabel.setText(String.valueOf(stats.getDryingCount()));
        flowReadyLabel.setText(String.valueOf(stats.getReadyForPickup()));
        int processing = stats.getProcessingCount();
        flowProcessingLabel.setText(processing + (processing == 1 ? " order" : " orders"));
    }

    // ------------------------------------------------------------------
    // Recent orders
    // ------------------------------------------------------------------

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String statusName = statusFilterComboBox.getValue();
        filteredOrders.setPredicate(order -> {
            boolean statusMatches = statusName == null || ALL_STATUSES.equals(statusName)
                    || order.getStatus().getDisplayName().equals(statusName);
            boolean textMatches = query.isEmpty()
                    || order.getOrderNumber().toLowerCase().contains(query)
                    || containsAllWords(order.getCustomerName(), query);
            return statusMatches && textMatches;
        });
        if (filteredOrders.isEmpty() && !orders.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders match your search.");
        } else if (orders.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders yet.");
        }
    }

    /** True when every word of the query appears in the name, so "maria santos" finds "Maria Lopez Santos". */
    private static boolean containsAllWords(String name, String query) {
        String lowerName = name.toLowerCase();
        for (String word : query.split("\\s+")) {
            if (!lowerName.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private void selectOrder(int orderId) {
        for (OrderSummary order : filteredOrders) {
            if (order.getId() == orderId) {
                ordersTable.getSelectionModel().select(order);
                ordersTable.scrollTo(order);
                return;
            }
        }
        if (!filteredOrders.isEmpty()) {
            ordersTable.getSelectionModel().selectFirst();
        } else {
            showDetails(null);
        }
    }

    // ------------------------------------------------------------------
    // Order details
    // ------------------------------------------------------------------

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
        detailQuantityLabel.setText("Loading...");
        detailTotalLabel.setText(MoneyFormat.php(order.getTotalAmount()));
        detailPaidLabel.setText(MoneyFormat.php(order.getAmountPaid()));
        detailBalanceLabel.setText(MoneyFormat.php(order.getBalance()));

        boolean open = order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.RELEASED;
        recordPaymentButton.setDisable(!open || order.getBalance().signum() <= 0);
        viewOrderButton.setDisable(false);
        releaseOrderButton.setDisable(!order.isReleasable());
        showReleaseMessage(releaseBlockReason(order));

        int orderId = order.getId();
        BackgroundTask.run(() -> dashboardService.loadOrderItems(orderId),
                (List<OrderItemLine> items) -> {
                    OrderSummary selected = ordersTable.getSelectionModel().getSelectedItem();
                    if (selected != null && selected.getId() == orderId) {
                        showItems(items);
                    }
                },
                error -> detailQuantityLabel.setText("Could not load items."));
    }

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

    /** Why the selected order cannot be released yet, or null when there is nothing to warn about. */
    private String releaseBlockReason(OrderSummary order) {
        switch (order.getStatus()) {
            case RELEASED:
            case CANCELLED:
                return null;
            case READY_FOR_PICKUP:
                return order.getBalance().signum() > 0 ? "Full payment required before release." : null;
            default:
                return order.getBalance().signum() > 0
                        ? "Must be Ready for Pickup and fully paid before release."
                        : "Must be Ready for Pickup before release.";
        }
    }

    private void showReleaseMessage(String message) {
        boolean show = message != null;
        releaseMessageLabel.setText(show ? message : "");
        releaseMessageBox.setVisible(show);
        releaseMessageBox.setManaged(show);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

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
                + "   Washing / Drying:     " + currentStats.getWashingCount()
                + " / " + currentStats.getDryingCount() + "\n"
                + "   Ready for pickup:     " + currentStats.getReadyForPickup() + "\n"
                + "   Outstanding balance:  " + MoneyFormat.php(currentStats.getOutstandingBalance()) + "\n\n"
                + "Payments collected include payments for orders received on earlier days.");
    }

    @FXML
    private void handleNewOrder() {
        AppShell.openTab(View.LAUNDRY_ORDERS);
    }

    @FXML
    private void handleRecordPayment() {
        AppShell.openTab(View.PAYMENTS);
    }

    @FXML
    private void handleViewOrder() {
        OrderSummary order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null) {
            AppShell.openTab(View.LAUNDRY_ORDERS);
        } else {
            LaundryOrdersController.openOrder(order.getId());
        }
    }

    @FXML
    private void handleReleaseOrder() {
        OrderSummary order = ordersTable.getSelectionModel().getSelectedItem();
        if (order == null || !Dialogs.confirm("Release order",
                "Release " + order.getOrderNumber() + " to " + order.getCustomerName() + "?")) {
            return;
        }
        releaseOrderButton.setDisable(true);
        BackgroundTask.runVoid(() -> orderService.releaseOrder(order.getId()),
                () -> {
                    Dialogs.info("Order released", order.getOrderNumber() + " has been released to "
                            + order.getCustomerName() + ".");
                    refreshAll(order.getId());
                },
                error -> {
                    Dialogs.error("Cannot release order", error);
                    refreshAll(order.getId());
                });
    }
}
