package com.laundrylink.controller;

import com.laundrylink.model.CustomerOption;
import com.laundrylink.model.OrderDetails;
import com.laundrylink.model.OrderItemLine;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.OrderSummary;
import com.laundrylink.model.PaymentStatus;
import com.laundrylink.model.ServiceOption;
import com.laundrylink.model.StatusChange;
import com.laundrylink.service.DashboardService;
import com.laundrylink.service.OrderService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.SessionContext;
import com.laundrylink.util.View;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import javafx.util.StringConverter;

/**
 * Laundry Order Management.
 *
 * Left: the order list (search + status filters) and the New Order form, which
 * also edits an order opened with Edit Order. Right: the selected order's
 * details, items, notes, status history and actions. Every rule is enforced
 * again by OrderService; this screen only mirrors them.
 */
public class LaundryOrdersController {

    private static final String ALL_STATUSES = "All Statuses";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a");
    private static final String TRASH_ICON =
            "M8 1 H16 V3 H21 V6 H3 V3 H8 Z M5 7 H19 L18 23 H6 Z M9 10 V20 H10.5 V10 Z M13.5 10 V20 H15 V10 Z";
    private static final long TYPE_AHEAD_RESET_MILLIS = 1200;

    /** Order to open the next time this tab is shown (set by other screens, e.g. the Dashboard). */
    private static int requestedOrderId = -1;

    // Order list
    @FXML
    private Button newOrderButton;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private ComboBox<String> paymentFilterComboBox;
    @FXML
    private TableView<OrderSummary> ordersTable;
    @FXML
    private TableColumn<OrderSummary, String> orderNumberColumn;
    @FXML
    private TableColumn<OrderSummary, String> customerColumn;
    @FXML
    private TableColumn<OrderSummary, String> dueDateColumn;
    @FXML
    private TableColumn<OrderSummary, String> totalColumn;
    @FXML
    private TableColumn<OrderSummary, String> statusColumn;
    @FXML
    private TableColumn<OrderSummary, String> paymentColumn;
    @FXML
    private Label ordersPlaceholderLabel;

    // New / edit order form
    @FXML
    private Label formTitleLabel;
    @FXML
    private Label formStatusLabel;
    @FXML
    private ComboBox<CustomerOption> customerComboBox;
    @FXML
    private Button newCustomerButton;
    @FXML
    private ComboBox<ServiceOption> serviceComboBox;
    @FXML
    private Spinner<Double> quantitySpinner;
    @FXML
    private Label unitLabel;
    @FXML
    private Button addItemButton;
    @FXML
    private TableView<OrderItemLine> itemsTable;
    @FXML
    private TableColumn<OrderItemLine, String> itemNumberColumn;
    @FXML
    private TableColumn<OrderItemLine, String> itemServiceColumn;
    @FXML
    private TableColumn<OrderItemLine, String> itemQuantityColumn;
    @FXML
    private TableColumn<OrderItemLine, String> itemPriceColumn;
    @FXML
    private TableColumn<OrderItemLine, String> itemSubtotalColumn;
    @FXML
    private TableColumn<OrderItemLine, String> itemActionColumn;
    @FXML
    private DatePicker expectedDatePicker;
    @FXML
    private TextArea notesArea;
    @FXML
    private Label totalLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button clearFormButton;
    @FXML
    private Label lockHintLabel;
    @FXML
    private Label formMessageLabel;

    // Order details
    @FXML
    private Label detailOrderNumberLabel;
    @FXML
    private Label detailCustomerLabel;
    @FXML
    private Label detailPhoneLabel;
    @FXML
    private Label detailOrderDateLabel;
    @FXML
    private Label detailDueDateLabel;
    @FXML
    private Label detailStatusLabel;
    @FXML
    private Label detailPaymentLabel;
    @FXML
    private Label detailTotalLabel;
    @FXML
    private Label detailPaidLabel;
    @FXML
    private Label detailBalanceLabel;
    @FXML
    private TableView<OrderItemLine> detailItemsTable;
    @FXML
    private TableColumn<OrderItemLine, String> detailItemNumberColumn;
    @FXML
    private TableColumn<OrderItemLine, String> detailItemServiceColumn;
    @FXML
    private TableColumn<OrderItemLine, String> detailItemQuantityColumn;
    @FXML
    private TableColumn<OrderItemLine, String> detailItemPriceColumn;
    @FXML
    private TableColumn<OrderItemLine, String> detailItemSubtotalColumn;
    @FXML
    private TextArea detailNotesArea;
    @FXML
    private TableView<StatusChange> historyTable;
    @FXML
    private TableColumn<StatusChange, String> historyTimeColumn;
    @FXML
    private TableColumn<StatusChange, String> historyStatusColumn;
    @FXML
    private TableColumn<StatusChange, String> historyStaffColumn;
    @FXML
    private Button recordPaymentButton;
    @FXML
    private Button updateStatusButton;
    @FXML
    private Button releaseOrderButton;
    @FXML
    private Button editOrderButton;
    @FXML
    private Button cancelOrderButton;
    @FXML
    private Button deleteOrderButton;
    @FXML
    private HBox releaseMessageBox;
    @FXML
    private Label releaseMessageLabel;

    private final OrderService orderService = new OrderService();
    private final ObservableList<OrderSummary> orders = FXCollections.observableArrayList();
    private final FilteredList<OrderSummary> filteredOrders = new FilteredList<>(orders, order -> true);
    private final ObservableList<CustomerOption> customers = FXCollections.observableArrayList();
    private final ObservableList<ServiceOption> services = FXCollections.observableArrayList();
    private final ObservableList<OrderItemLine> formItems = FXCollections.observableArrayList();
    private final ObservableList<OrderItemLine> detailItems = FXCollections.observableArrayList();
    private final ObservableList<StatusChange> history = FXCollections.observableArrayList();

    /** The order shown in Order Details, or null when nothing is selected. */
    private OrderDetails shownOrder;
    /** The order loaded into the form by Edit Order, or null while entering a new order. */
    private OrderDetails editingOrder;

    private final StringBuilder typeAhead = new StringBuilder();
    private long lastTypeAheadMillis;

    /** Switches to the Laundry Orders tab and opens the given order. */
    public static void openOrder(int orderId) {
        requestedOrderId = orderId;
        AppShell.openTab(View.LAUNDRY_ORDERS);
    }

    @FXML
    private void initialize() {
        setUpFilters();
        setUpOrdersTable();
        setUpForm();
        setUpDetails();

        int openOrderId = requestedOrderId;
        requestedOrderId = -1;
        startNewOrder();
        showDetails(null);
        loadCustomers();
        loadServices();
        loadOrders(openOrderId);
    }

    // ------------------------------------------------------------------
    // Set-up
    // ------------------------------------------------------------------

    private void setUpFilters() {
        List<String> statusOptions = new ArrayList<>();
        statusOptions.add(ALL_STATUSES);
        for (OrderStatus status : OrderStatus.values()) {
            statusOptions.add(status.getDisplayName());
        }
        statusFilterComboBox.setItems(FXCollections.observableArrayList(statusOptions));
        statusFilterComboBox.setValue(ALL_STATUSES);

        List<String> paymentOptions = new ArrayList<>();
        paymentOptions.add(ALL_STATUSES);
        for (PaymentStatus status : PaymentStatus.values()) {
            paymentOptions.add(status.getDisplayName());
        }
        paymentFilterComboBox.setItems(FXCollections.observableArrayList(paymentOptions));
        paymentFilterComboBox.setValue(ALL_STATUSES);

        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter());
        statusFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        paymentFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    }

    private void setUpOrdersTable() {
        ordersTable.setItems(filteredOrders);
        orderNumberColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getOrderNumber()));
        customerColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getCustomerName()));
        dueDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatDate(cell.getValue().getDueDate())));
        totalColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getTotalAmount())));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus().getDisplayName()));
        paymentColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getPaymentStatus().getDisplayName()));

        dueDateColumn.setCellFactory(column -> styledCell(order ->
                order.isOverdue(DashboardService.today()) ? "cell-overdue" : null));
        statusColumn.setCellFactory(column -> styledCell(order -> statusStyle(order.getStatus())));
        paymentColumn.setCellFactory(column -> styledCell(order -> paymentStyle(order.getPaymentStatus())));

        ordersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldOrder, newOrder) -> {
            if (newOrder != null && (shownOrder == null || shownOrder.getId() != newOrder.getId())) {
                openOrderDetails(newOrder.getId());
            }
        });
    }

    private void setUpForm() {
        customerComboBox.setItems(customers);
        customerComboBox.setPlaceholder(new Label("No customers yet. Use New Customer to add one."));
        customerComboBox.addEventFilter(KeyEvent.KEY_TYPED, this::typeAheadCustomer);
        customerComboBox.setTooltip(new Tooltip("Type part of a name or phone number to jump to a customer."));

        serviceComboBox.setItems(services);
        serviceComboBox.setPlaceholder(new Label("No active services. Add them in Services & Prices."));
        serviceComboBox.valueProperty().addListener((obs, oldService, newService) -> useQuantityFor(newService));

        useQuantityFor(null);
        quantitySpinner.getEditor().addEventHandler(ActionEvent.ACTION, event -> handleAddItem());

        itemsTable.setItems(formItems);
        setUpItemColumns(itemNumberColumn, itemServiceColumn, itemQuantityColumn, itemPriceColumn, itemSubtotalColumn);
        itemActionColumn.setCellFactory(column -> new TableCell<OrderItemLine, String>() {
            private final Button removeButton = new Button();

            {
                SVGPath trash = new SVGPath();
                trash.setContent(TRASH_ICON);
                trash.setScaleX(0.6);
                trash.setScaleY(0.6);
                trash.getStyleClass().add("icon-delete");
                removeButton.setGraphic(trash);
                removeButton.getStyleClass().add("icon-button");
                removeButton.setTooltip(new Tooltip("Remove this service"));
                removeButton.setOnAction(event -> formItems.remove((OrderItemLine) getTableRow().getItem()));
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                boolean show = !empty && getTableRow() != null && getTableRow().getItem() != null;
                setGraphic(show ? removeButton : null);
                removeButton.setDisable(!formItemsEditable());
            }
        });
        formItems.addListener((ListChangeListener<OrderItemLine>) change ->
                totalLabel.setText(MoneyFormat.php(OrderService.total(formItems))));

        useDateFormat(expectedDatePicker);
        expectedDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(earliestExpectedDate()));
            }
        });

        notesArea.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= OrderService.MAX_NOTES_LENGTH ? change : null));
    }

    private void setUpDetails() {
        detailItemsTable.setItems(detailItems);
        setUpItemColumns(detailItemNumberColumn, detailItemServiceColumn, detailItemQuantityColumn,
                detailItemPriceColumn, detailItemSubtotalColumn);

        historyTable.setItems(history);
        historyTimeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getChangedAt().format(DATE_TIME)));
        historyStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus().getDisplayName()));
        historyStaffColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getChangedByName()));

        // Staff never see the owner-only delete; the service layer also rejects it.
        boolean admin = SessionContext.isAdmin();
        deleteOrderButton.setVisible(admin);
        deleteOrderButton.setManaged(admin);
    }

    /** #, Service, Quantity, Unit Price, Subtotal - shared by the form and the details items tables. */
    private void setUpItemColumns(TableColumn<OrderItemLine, String> number, TableColumn<OrderItemLine, String> service,
            TableColumn<OrderItemLine, String> quantity, TableColumn<OrderItemLine, String> price,
            TableColumn<OrderItemLine, String> subtotal) {
        number.setCellFactory(column -> new TableCell<OrderItemLine, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || getTableRow() == null || getTableRow().getItem() == null
                        ? null : String.valueOf(getIndex() + 1));
            }
        });
        service.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getServiceName()));
        quantity.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                formatQuantity(cell.getValue().getQuantity(), cell.getValue().getPricingUnit())));
        price.setCellValueFactory(cell -> new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getUnitPrice())));
        subtotal.setCellValueFactory(cell -> new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getSubtotal())));
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Reloads the list; afterwards selects (and opens) the order with the given ID, if any. */
    private void loadOrders(int selectOrderId) {
        ordersPlaceholderLabel.setText("Loading orders...");
        BackgroundTask.run(orderService::loadOrders,
                (List<OrderSummary> loaded) -> {
                    orders.setAll(loaded);
                    applyFilter();
                    selectOrder(selectOrderId);
                },
                error -> {
                    ordersPlaceholderLabel.setText("Orders could not be loaded.");
                    Dialogs.error("Cannot load orders", error);
                });
    }

    private void loadCustomers() {
        BackgroundTask.run(orderService::loadCustomers,
                (List<CustomerOption> loaded) -> {
                    CustomerOption selected = customerComboBox.getValue();
                    customers.setAll(loaded);
                    customerComboBox.setValue(null);
                    if (selected != null) {
                        selectCustomer(selected.getId(), selected.getName());
                    }
                },
                error -> {
                    customerComboBox.setPlaceholder(new Label("Customers could not be loaded."));
                    AppShell.setStatus("Could not load customers: " + Dialogs.friendlyMessage(error));
                });
    }

    private void loadServices() {
        BackgroundTask.run(orderService::loadActiveServices,
                (List<ServiceOption> loaded) -> {
                    ServiceOption selected = serviceComboBox.getValue();
                    services.setAll(loaded);
                    serviceComboBox.setValue(null);
                    if (selected != null) {
                        for (ServiceOption service : services) {
                            if (service.getId() == selected.getId()) {
                                serviceComboBox.setValue(service);
                                break;
                            }
                        }
                    }
                },
                error -> {
                    serviceComboBox.setPlaceholder(new Label("Services could not be loaded."));
                    AppShell.setStatus("Could not load services: " + Dialogs.friendlyMessage(error));
                });
    }

    private void openOrderDetails(int orderId) {
        detailCustomerLabel.setText("Loading order...");
        BackgroundTask.run(() -> orderService.loadOrder(orderId),
                details -> {
                    OrderSummary selected = ordersTable.getSelectionModel().getSelectedItem();
                    if (selected == null || selected.getId() == orderId) {
                        showDetails(details);
                    }
                },
                error -> {
                    showDetails(null);
                    Dialogs.error("Cannot open order", error);
                });
    }

    // ------------------------------------------------------------------
    // Order list
    // ------------------------------------------------------------------

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String digits = query.replaceAll("[^0-9]", "");
        String statusName = statusFilterComboBox.getValue();
        String paymentName = paymentFilterComboBox.getValue();

        filteredOrders.setPredicate(order -> {
            boolean statusMatches = statusName == null || ALL_STATUSES.equals(statusName)
                    || order.getStatus().getDisplayName().equals(statusName);
            boolean paymentMatches = paymentName == null || ALL_STATUSES.equals(paymentName)
                    || order.getPaymentStatus().getDisplayName().equals(paymentName);
            String phone = order.getCustomerPhone() == null ? "" : order.getCustomerPhone().replaceAll("[^0-9]", "");
            boolean textMatches = query.isEmpty()
                    || order.getOrderNumber().toLowerCase().contains(query)
                    || containsAllWords(order.getCustomerName(), query)
                    || (digits.length() >= 3 && phone.contains(digits));
            return statusMatches && paymentMatches && textMatches;
        });

        if (orders.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders yet. Create one with the New Order form below.");
        } else if (filteredOrders.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders match your search and filters.");
        }
    }

    private void selectOrder(int orderId) {
        if (orderId <= 0) {
            return;
        }
        for (OrderSummary order : filteredOrders) {
            if (order.getId() == orderId) {
                ordersTable.getSelectionModel().select(order);
                ordersTable.scrollTo(order);
                return;
            }
        }
        // Hidden by the filters (or beyond the list limit): still show its details.
        if (shownOrder == null || shownOrder.getId() != orderId) {
            openOrderDetails(orderId);
        }
    }

    // ------------------------------------------------------------------
    // Order details
    // ------------------------------------------------------------------

    private void showDetails(OrderDetails details) {
        shownOrder = details;
        if (details == null) {
            detailOrderNumberLabel.setText("—");
            detailCustomerLabel.setText("Select an order");
            detailPhoneLabel.setText("—");
            detailOrderDateLabel.setText("—");
            detailDueDateLabel.setText("—");
            showPill(detailStatusLabel, "—", null);
            showPill(detailPaymentLabel, "—", null);
            detailTotalLabel.setText("—");
            detailPaidLabel.setText("—");
            detailBalanceLabel.setText("—");
            detailItems.clear();
            detailNotesArea.clear();
            history.clear();
            showDetailActions();
            return;
        }

        OrderSummary summary = details.getSummary();
        detailOrderNumberLabel.setText(summary.getOrderNumber());
        detailCustomerLabel.setText(summary.getCustomerName());
        detailPhoneLabel.setText(summary.getCustomerPhone() == null || summary.getCustomerPhone().isEmpty()
                ? "—" : summary.getCustomerPhone());
        detailOrderDateLabel.setText(summary.getReceivedAt().format(DATE));
        detailDueDateLabel.setText(formatDate(details.getExpectedCompletionDate())
                + (summary.isOverdue(DashboardService.today()) ? "  (overdue)" : ""));
        showPill(detailStatusLabel, summary.getStatus().getDisplayName(), statusStyle(summary.getStatus()));
        showPill(detailPaymentLabel, summary.getPaymentStatus().getDisplayName(), paymentStyle(summary.getPaymentStatus()));
        detailTotalLabel.setText(MoneyFormat.php(summary.getTotalAmount()));
        detailPaidLabel.setText(MoneyFormat.php(summary.getAmountPaid()));
        detailBalanceLabel.setText(MoneyFormat.php(summary.getBalance()));
        detailItems.setAll(details.getItems());
        detailNotesArea.setText(details.getNotes() == null ? "" : details.getNotes());
        history.setAll(details.getStatusHistory());
        if (!history.isEmpty()) {
            historyTable.scrollTo(history.size() - 1);
        }
        showDetailActions();
    }

    private Button[] detailActionButtons() {
        return new Button[] {recordPaymentButton, updateStatusButton, releaseOrderButton,
            editOrderButton, cancelOrderButton, deleteOrderButton};
    }

    /** Enables only the actions the shown order's state allows (mirrors the OrderService rules). */
    private void showDetailActions() {
        OrderDetails order = shownOrder;
        if (order == null) {
            for (Button button : detailActionButtons()) {
                button.setDisable(true);
            }
            showReleaseMessage(null);
            return;
        }
        OrderSummary summary = order.getSummary();
        recordPaymentButton.setDisable(!order.isOpen() || summary.getBalance().signum() <= 0);
        OrderStatus next = order.getNextStatus();
        updateStatusButton.setDisable(next == null);
        updateStatusButton.setTooltip(new Tooltip(next == null ? "No further stage." : "Move to " + next.getDisplayName()));
        releaseOrderButton.setDisable(!summary.isReleasable());
        editOrderButton.setDisable(!order.isOpen());
        cancelOrderButton.setDisable(!order.isCancellable());
        deleteOrderButton.setDisable(!order.isItemEditable());
        showReleaseMessage(releaseBlockReason(summary));
    }

    /** Why the order cannot be released yet, or null when there is nothing to warn about. */
    private static String releaseBlockReason(OrderSummary order) {
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
    // New / edit order form
    // ------------------------------------------------------------------

    private void startNewOrder() {
        editingOrder = null;
        formTitleLabel.setText("New Order");
        formStatusLabel.setText("Order number is assigned when you save.");
        customerComboBox.setValue(null);
        serviceComboBox.setValue(null);
        formItems.clear();
        expectedDatePicker.setValue(DashboardService.today().plusDays(1));
        notesArea.clear();
        Dialogs.clearMessage(formMessageLabel);
        applyFormState();
    }

    private void loadIntoForm(OrderDetails order) {
        editingOrder = order;
        OrderSummary summary = order.getSummary();
        formTitleLabel.setText("Edit Order " + summary.getOrderNumber());
        formStatusLabel.setText(summary.getStatus().getDisplayName() + " · " + summary.getPaymentStatus().getDisplayName());
        selectCustomer(order.getCustomerId(), summary.getCustomerName());
        serviceComboBox.setValue(null);
        formItems.setAll(order.getItems());
        expectedDatePicker.setValue(order.getExpectedCompletionDate());
        notesArea.setText(order.getNotes() == null ? "" : order.getNotes());
        Dialogs.clearMessage(formMessageLabel);
        applyFormState();
    }

    private boolean formItemsEditable() {
        return editingOrder == null || editingOrder.isItemEditable();
    }

    /** Enables only what the edited order's state allows. */
    private void applyFormState() {
        boolean itemsEditable = formItemsEditable();
        customerComboBox.setDisable(!itemsEditable);
        newCustomerButton.setDisable(!itemsEditable);
        serviceComboBox.setDisable(!itemsEditable);
        quantitySpinner.setDisable(!itemsEditable);
        addItemButton.setDisable(!itemsEditable);
        itemsTable.refresh();
        saveButton.setDisable(false);
        clearFormButton.setDisable(false);
        newOrderButton.setDisable(false);
        ordersTable.setDisable(false);
        saveButton.setText(itemsEditable ? "Save Order" : "Save Date and Notes");

        String hint = editingOrder == null || itemsEditable ? null
                : (editingOrder.getSummary().getStatus() != OrderStatus.RECEIVED
                        ? "Processing has started" : "A payment has been recorded")
                + ", so the customer and services can no longer be changed. You can still update the "
                + "expected completion date and notes.";
        lockHintLabel.setText(hint == null ? "" : hint);
        lockHintLabel.setVisible(hint != null);
        lockHintLabel.setManaged(hint != null);
    }

    private void setFormBusy(boolean busy) {
        if (!busy) {
            applyFormState();
            return;
        }
        saveButton.setDisable(true);
        saveButton.setText("Saving...");
        clearFormButton.setDisable(true);
        newOrderButton.setDisable(true);
        ordersTable.setDisable(true);
    }

    private void setDetailsBusy(boolean busy) {
        if (!busy) {
            showDetailActions();
            ordersTable.setDisable(false);
            return;
        }
        for (Button button : detailActionButtons()) {
            button.setDisable(true);
        }
        ordersTable.setDisable(true);
    }

    private void selectCustomer(int customerId, String name) {
        for (CustomerOption customer : customers) {
            if (customer.getId() == customerId) {
                customerComboBox.setValue(customer);
                return;
            }
        }
        // Customer list not loaded yet: show the name until it arrives.
        customerComboBox.setValue(new CustomerOption(customerId, name, null));
    }

    /** Typing on the customer box jumps to the first customer whose name or phone contains the typed text. */
    private void typeAheadCustomer(KeyEvent event) {
        String typed = event.getCharacter();
        if (typed == null || typed.isEmpty() || Character.isISOControl(typed.charAt(0))) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTypeAheadMillis > TYPE_AHEAD_RESET_MILLIS) {
            typeAhead.setLength(0);
        }
        lastTypeAheadMillis = now;
        typeAhead.append(typed.toLowerCase());
        String query = typeAhead.toString();
        String digits = query.replaceAll("[^0-9]", "");
        for (CustomerOption customer : customers) {
            String phone = customer.getContactNumber() == null ? "" : customer.getContactNumber();
            if (containsAllWords(customer.getName(), query) || (!digits.isEmpty() && phone.contains(digits))) {
                customerComboBox.setValue(customer);
                break;
            }
        }
        event.consume();
    }

    /** Kilograms step by 0.5 and allow decimals; pieces step by 1. */
    private void useQuantityFor(ServiceOption service) {
        boolean perPiece = service != null && !service.isPerKilogram();
        SpinnerValueFactory.DoubleSpinnerValueFactory factory = perPiece
                ? new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 9999, 1, 1)
                : new SpinnerValueFactory.DoubleSpinnerValueFactory(0.25, 9999.99, 1, 0.5);
        DecimalFormat format = new DecimalFormat(perPiece ? "#0" : "#0.##");
        factory.setConverter(new StringConverter<Double>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : format.format(value);
            }

            @Override
            public Double fromString(String text) {
                try {
                    return Double.valueOf(text.trim().replace(",", ""));
                } catch (NumberFormatException | NullPointerException e) {
                    return factory.getValue();
                }
            }
        });
        quantitySpinner.setValueFactory(factory);
        quantitySpinner.getEditor().setText(format.format(factory.getValue()));
        unitLabel.setText(service == null ? "kg / pcs" : (perPiece ? "pcs" : "kg"));
    }

    /** Orders cannot be due before the day they were received. */
    private LocalDate earliestExpectedDate() {
        return editingOrder == null
                ? DashboardService.today()
                : editingOrder.getSummary().getReceivedAt().toLocalDate();
    }

    // ------------------------------------------------------------------
    // Form actions
    // ------------------------------------------------------------------

    @FXML
    private void handleNew() {
        startNewOrder();
        customerComboBox.requestFocus();
    }

    @FXML
    private void handleClearForm() {
        startNewOrder();
    }

    @FXML
    private void handleNewCustomer() {
        AppShell.openTab(View.CUSTOMERS);
    }

    @FXML
    private void handleAddItem() {
        if (!formItemsEditable()) {
            return;
        }
        ServiceOption service = serviceComboBox.getValue();
        if (service == null) {
            Dialogs.showError(formMessageLabel, "Select a service to add.");
            serviceComboBox.requestFocus();
            return;
        }
        try {
            BigDecimal quantity = OrderService.parseQuantity(quantitySpinner.getEditor().getText(),
                    service.getPricingUnit());
            int index = indexOfService(service.getId());
            if (index >= 0) {
                // Same service again: add to the existing line instead of listing it twice.
                OrderItemLine existing = formItems.get(index);
                BigDecimal combined = existing.getQuantity().add(quantity);
                OrderService.validateQuantity(combined, existing.getPricingUnit());
                formItems.set(index, new OrderItemLine(existing.getItemId(), existing.getServiceId(),
                        existing.getServiceName(), existing.getPricingUnit(), combined, existing.getUnitPrice(),
                        OrderService.subtotal(combined, existing.getUnitPrice())));
            } else {
                formItems.add(new OrderItemLine(0, service.getId(), service.getName(), service.getPricingUnit(),
                        quantity, service.getCurrentPrice(), OrderService.subtotal(quantity, service.getCurrentPrice())));
            }
            Dialogs.clearMessage(formMessageLabel);
            serviceComboBox.setValue(null);
            serviceComboBox.requestFocus();
        } catch (ServiceException e) {
            Dialogs.showError(formMessageLabel, e.getMessage());
            quantitySpinner.requestFocus();
        }
    }

    @FXML
    private void handleSave() {
        commitTypedDate(expectedDatePicker);
        OrderDetails editing = editingOrder;
        CustomerOption customer = customerComboBox.getValue();
        int customerId = customer == null ? 0 : customer.getId();
        List<OrderItemLine> items = new ArrayList<>(formItems);
        LocalDate expectedDate = expectedDatePicker.getValue();
        String notes = notesArea.getText();
        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);

        BackgroundTask.run(() -> {
            if (editing == null) {
                return orderService.createOrder(customerId, items, expectedDate, notes);
            }
            if (editing.isItemEditable()) {
                return orderService.updateOrder(editing.getId(), customerId, items, expectedDate, notes);
            }
            return orderService.updateOrderSchedule(editing.getId(), expectedDate, notes);
        }, saved -> {
            OrderSummary summary = saved.getSummary();
            startNewOrder();
            showDetails(saved);
            loadOrders(saved.getId());
            AppShell.setStatus(summary.getOrderNumber() + " saved.");
            Dialogs.info(editing == null ? "Order created" : "Order updated",
                    summary.getOrderNumber() + " for " + summary.getCustomerName() + " was saved.\n"
                    + "Total: " + MoneyFormat.php(summary.getTotalAmount()));
        }, error -> {
            setFormBusy(false);
            Dialogs.showFailure(formMessageLabel, "Cannot save order", error);
        });
    }

    // ------------------------------------------------------------------
    // Detail actions
    // ------------------------------------------------------------------

    @FXML
    private void handleEditOrder() {
        if (shownOrder != null && shownOrder.isOpen()) {
            loadIntoForm(shownOrder);
        }
    }

    @FXML
    private void handleRecordPayment() {
        AppShell.openTab(View.PAYMENTS);
    }

    @FXML
    private void handleUpdateStatus() {
        OrderDetails order = shownOrder;
        OrderStatus next = order == null ? null : order.getNextStatus();
        if (next == null || !Dialogs.confirm("Update status", "Move " + order.getSummary().getOrderNumber()
                + " from " + order.getSummary().getStatus().getDisplayName() + " to " + next.getDisplayName() + "?")) {
            return;
        }
        runDetailAction(() -> orderService.advanceStatus(order.getId()), "Status updated",
                order.getSummary().getOrderNumber() + " is now " + next.getDisplayName() + ".",
                "Cannot update status");
    }

    @FXML
    private void handleReleaseOrder() {
        OrderDetails order = shownOrder;
        if (order == null || !Dialogs.confirm("Release order", "Release " + order.getSummary().getOrderNumber()
                + " to " + order.getSummary().getCustomerName() + "?")) {
            return;
        }
        runDetailAction(() -> {
            orderService.releaseOrder(order.getId());
            return orderService.loadOrder(order.getId());
        }, "Order released",
                order.getSummary().getOrderNumber() + " has been released to " + order.getSummary().getCustomerName() + ".",
                "Cannot release order");
    }

    @FXML
    private void handleCancelOrder() {
        OrderDetails order = shownOrder;
        if (order == null || !order.isCancellable()) {
            return;
        }
        if (!Dialogs.confirm("Cancel order", "Cancel " + order.getSummary().getOrderNumber() + " for "
                + order.getSummary().getCustomerName()
                + "?\n\nThe order stays in the records with the status Cancelled. This cannot be undone.")) {
            return;
        }
        runDetailAction(() -> orderService.cancelOrder(order.getId()), "Order cancelled",
                order.getSummary().getOrderNumber() + " has been cancelled.", "Cannot cancel order");
    }

    @FXML
    private void handleDeleteOrder() {
        OrderDetails order = shownOrder;
        if (order == null || !order.isItemEditable()) {
            return;
        }
        String number = order.getSummary().getOrderNumber();
        if (!Dialogs.confirm("Delete order", "Permanently delete " + number + " for "
                + order.getSummary().getCustomerName() + "?\n\nUse this only for an order entered by mistake. "
                + "The order and its services will be removed and cannot be recovered. "
                + "To keep a record, use Cancel Order instead.")) {
            return;
        }
        setDetailsBusy(true);
        BackgroundTask.runVoid(() -> orderService.deleteOrder(order.getId()),
                () -> {
                    if (editingOrder != null && editingOrder.getId() == order.getId()) {
                        startNewOrder();
                    }
                    showDetails(null);
                    loadOrders(-1);
                    setDetailsBusy(false);
                    AppShell.setStatus(number + " deleted.");
                    Dialogs.info("Order deleted", number + " has been deleted.");
                },
                error -> {
                    setDetailsBusy(false);
                    Dialogs.error("Cannot delete order", error);
                });
    }

    /** Runs a change to the shown order, then refreshes its details, the list, and the form if it was open there. */
    private void runDetailAction(BackgroundTask.Work<OrderDetails> work, String successTitle, String successMessage,
            String errorTitle) {
        int orderId = shownOrder.getId();
        setDetailsBusy(true);
        BackgroundTask.run(work,
                updated -> {
                    showDetails(updated);
                    if (editingOrder != null && editingOrder.getId() == orderId) {
                        if (updated.isOpen()) {
                            loadIntoForm(updated);
                        } else {
                            startNewOrder();
                        }
                    }
                    loadOrders(orderId);
                    setDetailsBusy(false);
                    AppShell.setStatus(successMessage);
                    Dialogs.info(successTitle, successMessage);
                },
                error -> {
                    setDetailsBusy(false);
                    Dialogs.error(errorTitle, error);
                    openOrderDetails(orderId);
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private int indexOfService(int serviceId) {
        for (int i = 0; i < formItems.size(); i++) {
            if (formItems.get(i).getServiceId() == serviceId) {
                return i;
            }
        }
        return -1;
    }

    /** A text cell that adds one style class chosen from its row's order (or none). */
    private static TableCell<OrderSummary, String> styledCell(Function<OrderSummary, String> styleFor) {
        return new TableCell<OrderSummary, String>() {
            private String applied;

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (applied != null) {
                    getStyleClass().remove(applied);
                    applied = null;
                }
                OrderSummary order = empty || getTableRow() == null ? null : (OrderSummary) getTableRow().getItem();
                setText(empty ? null : value);
                if (order != null) {
                    applied = styleFor.apply(order);
                    if (applied != null) {
                        getStyleClass().add(applied);
                    }
                }
            }
        };
    }

    private static String statusStyle(OrderStatus status) {
        switch (status) {
            case READY_FOR_PICKUP:
                return "status-ready";
            case RELEASED:
                return "status-released";
            case CANCELLED:
                return "status-cancelled";
            default:
                return null;
        }
    }

    private static String paymentStyle(PaymentStatus status) {
        switch (status) {
            case PAID:
                return "pay-paid";
            case PARTIALLY_PAID:
                return "pay-partial";
            default:
                return "pay-unpaid";
        }
    }

    /** Shows a status pill; the style is one of the status-/pay- classes, or null for a plain pill. */
    private static void showPill(Label pill, String text, String style) {
        pill.setText(text);
        pill.getStyleClass().removeIf(styleClass -> styleClass.startsWith("status-") || styleClass.startsWith("pay-"));
        if (style != null) {
            pill.getStyleClass().add(style);
        }
    }

    /** "4 kg", "1 pc", "3 pcs". */
    private static String formatQuantity(BigDecimal quantity, String pricingUnit) {
        String number = new DecimalFormat("#,##0.##").format(quantity);
        if (ServiceOption.PER_KILOGRAM.equals(pricingUnit)) {
            return number + " kg";
        }
        return number + (quantity.compareTo(BigDecimal.ONE) == 0 ? " pc" : " pcs");
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "—" : date.format(DATE);
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

    private static void useDateFormat(DatePicker picker) {
        picker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(DATE);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.trim().isEmpty()) {
                    return null;
                }
                try {
                    return LocalDate.parse(text.trim(), DATE);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
    }

    /** A date typed into the picker is only applied on Enter; apply it before reading the value. */
    private static void commitTypedDate(DatePicker picker) {
        if (picker.isEditable()) {
            picker.setValue(picker.getConverter().fromString(picker.getEditor().getText()));
        }
    }
}
