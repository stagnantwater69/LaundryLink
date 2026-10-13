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
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;
import javafx.util.StringConverter;

/**
 * Laundry Orders tab: the order list, the new/edit form, and order details.
 */
public class LaundryOrdersController {

    private static final String ALL_STATUSES = "All Statuses";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final String TRASH_ICON =
            "M8 1 H16 V3 H21 V6 H3 V3 H8 Z M5 7 H19 L18 23 H6 Z M9 10 V20 H10.5 V10 Z M13.5 10 V20 H15 V10 Z";
    private static final long TYPE_AHEAD_RESET_MILLIS = 1200;

    // Color style each column uses.
    private static final int STYLE_DUE_DATE = 1;
    private static final int STYLE_STATUS = 2;
    private static final int STYLE_PAYMENT = 3;

    /** Order to open next time this tab shows (e.g. from the Dashboard). */
    private static int requestedOrderId = -1;

    // Order list
    @FXML private Button newOrderButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private ComboBox<String> paymentFilterComboBox;
    @FXML private TableView<OrderSummary> ordersTable;
    @FXML private TableColumn<OrderSummary, String> orderNumberColumn;
    @FXML private TableColumn<OrderSummary, String> customerColumn;
    @FXML private TableColumn<OrderSummary, String> dueDateColumn;
    @FXML private TableColumn<OrderSummary, String> totalColumn;
    @FXML private TableColumn<OrderSummary, String> statusColumn;
    @FXML private TableColumn<OrderSummary, String> paymentColumn;
    @FXML private Label ordersPlaceholderLabel;

    // New / edit order form
    @FXML private Label formTitleLabel;
    @FXML private Label formStatusLabel;
    @FXML private ComboBox<CustomerOption> customerComboBox;
    @FXML private Button newCustomerButton;
    @FXML private ComboBox<ServiceOption> serviceComboBox;
    @FXML private Spinner<Double> quantitySpinner;
    @FXML private Label unitLabel;
    @FXML private Button addItemButton;
    @FXML private TableView<OrderItemLine> itemsTable;
    @FXML private TableColumn<OrderItemLine, String> itemNumberColumn;
    @FXML private TableColumn<OrderItemLine, String> itemServiceColumn;
    @FXML private TableColumn<OrderItemLine, String> itemQuantityColumn;
    @FXML private TableColumn<OrderItemLine, String> itemPriceColumn;
    @FXML private TableColumn<OrderItemLine, String> itemSubtotalColumn;
    @FXML private TableColumn<OrderItemLine, String> itemActionColumn;
    @FXML private DatePicker expectedDatePicker;
    @FXML private TextArea notesArea;
    @FXML private Label totalLabel;
    @FXML private Button saveButton;
    @FXML private Button clearFormButton;
    @FXML private Label lockHintLabel;
    @FXML private Label formMessageLabel;

    // Order details
    @FXML private Label detailOrderNumberLabel;
    @FXML private Label detailCustomerLabel;
    @FXML private Label detailPhoneLabel;
    @FXML private Label detailOrderDateLabel;
    @FXML private Label detailDueDateLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailPaymentLabel;
    @FXML private Label detailTotalLabel;
    @FXML private Label detailPaidLabel;
    @FXML private Label detailBalanceLabel;
    @FXML private TableView<OrderItemLine> detailItemsTable;
    @FXML private TableColumn<OrderItemLine, String> detailItemNumberColumn;
    @FXML private TableColumn<OrderItemLine, String> detailItemServiceColumn;
    @FXML private TableColumn<OrderItemLine, String> detailItemQuantityColumn;
    @FXML private TableColumn<OrderItemLine, String> detailItemPriceColumn;
    @FXML private TableColumn<OrderItemLine, String> detailItemSubtotalColumn;
    @FXML private TextArea detailNotesArea;
    @FXML private TableView<StatusChange> historyTable;
    @FXML private TableColumn<StatusChange, String> historyTimeColumn;
    @FXML private TableColumn<StatusChange, String> historyStatusColumn;
    @FXML private TableColumn<StatusChange, String> historyStaffColumn;
    @FXML private Button recordPaymentButton;
    @FXML private Button updateStatusButton;
    @FXML private Button releaseOrderButton;
    @FXML private Button editOrderButton;
    @FXML private Button cancelOrderButton;
    @FXML private Button deleteOrderButton;
    @FXML private HBox releaseMessageBox;
    @FXML private Label releaseMessageLabel;

    private final OrderService orderService = new OrderService();

    // Every loaded order; the table only shows the ones that match the filters.
    private List<OrderSummary> allOrders = new ArrayList<OrderSummary>();
    private List<CustomerOption> customers = new ArrayList<CustomerOption>();
    // Services added to the order in the form.
    private List<OrderItemLine> formItems = new ArrayList<OrderItemLine>();

    /** Order shown in the details panel, or null. */
    private OrderDetails shownOrder;
    /** Order being edited in the form, or null for a new order. */
    private OrderDetails editingOrder;

    // Letters typed in the customer box, used to jump to a customer.
    private String typeAhead = "";
    private long lastTypeAheadMillis;

    /** Opens the Laundry Orders tab on this order. */
    public static void openOrder(int orderId) {
        requestedOrderId = orderId;
        AppShell.openTab(View.LAUNDRY_ORDERS);
    }

    /** Sets up the tables and filters, then loads the data. */
    @FXML
    private void initialize() {
        setUpFilters();
        setUpTables();

        customerComboBox.setPlaceholder(new Label("No customers yet. Use New Customer to add one."));
        customerComboBox.setTooltip(new Tooltip("Type part of a name or phone number to jump to a customer."));
        serviceComboBox.setPlaceholder(new Label("No active services. Add them in Services & Prices."));
        useQuantityFor(null);

        // Only the owner can delete orders.
        boolean admin = SessionContext.isAdmin();
        deleteOrderButton.setVisible(admin);
        deleteOrderButton.setManaged(admin);

        int openOrderId = requestedOrderId;
        requestedOrderId = -1;
        startNewOrder();
        showDetails(null);
        loadCustomers();
        loadServices();
        loadOrders(openOrderId);
    }

    // ---- Set-up ----

    /** Fills the status and payment filters. */
    private void setUpFilters() {
        List<String> statusOptions = new ArrayList<String>();
        statusOptions.add(ALL_STATUSES);
        for (OrderStatus status : OrderStatus.values()) {
            statusOptions.add(status.getDisplayName());
        }
        statusFilterComboBox.setItems(FXCollections.observableArrayList(statusOptions));
        statusFilterComboBox.setValue(ALL_STATUSES);

        List<String> paymentOptions = new ArrayList<String>();
        paymentOptions.add(ALL_STATUSES);
        for (PaymentStatus status : PaymentStatus.values()) {
            paymentOptions.add(status.getDisplayName());
        }
        paymentFilterComboBox.setItems(FXCollections.observableArrayList(paymentOptions));
        paymentFilterComboBox.setValue(ALL_STATUSES);
    }

    /** Links each column to its data and colors. */
    private void setUpTables() {
        orderNumberColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("orderNumber"));
        customerColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("customerName"));
        dueDateColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("dueDateText"));
        totalColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("totalText"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("statusText"));
        paymentColumn.setCellValueFactory(new PropertyValueFactory<OrderSummary, String>("paymentText"));
        dueDateColumn.setCellFactory(new OrderStyleCellFactory(STYLE_DUE_DATE));
        statusColumn.setCellFactory(new OrderStyleCellFactory(STYLE_STATUS));
        paymentColumn.setCellFactory(new OrderStyleCellFactory(STYLE_PAYMENT));

        setUpItemColumns(itemNumberColumn, itemServiceColumn, itemQuantityColumn, itemPriceColumn, itemSubtotalColumn);
        itemActionColumn.setCellFactory(new RemoveItemCellFactory());
        setUpItemColumns(detailItemNumberColumn, detailItemServiceColumn, detailItemQuantityColumn,
                detailItemPriceColumn, detailItemSubtotalColumn);

        historyTimeColumn.setCellValueFactory(new PropertyValueFactory<StatusChange, String>("changedAtText"));
        historyStatusColumn.setCellValueFactory(new PropertyValueFactory<StatusChange, String>("statusText"));
        historyStaffColumn.setCellValueFactory(new PropertyValueFactory<StatusChange, String>("changedByName"));
    }

    /** Item columns shared by the form and details tables. */
    private void setUpItemColumns(TableColumn<OrderItemLine, String> number, TableColumn<OrderItemLine, String> service,
            TableColumn<OrderItemLine, String> quantity, TableColumn<OrderItemLine, String> price,
            TableColumn<OrderItemLine, String> subtotal) {
        number.setCellFactory(new RowNumberCellFactory());
        service.setCellValueFactory(new PropertyValueFactory<OrderItemLine, String>("serviceName"));
        quantity.setCellValueFactory(new PropertyValueFactory<OrderItemLine, String>("quantityText"));
        price.setCellValueFactory(new PropertyValueFactory<OrderItemLine, String>("unitPriceText"));
        subtotal.setCellValueFactory(new PropertyValueFactory<OrderItemLine, String>("subtotalText"));
    }

    // ---- Loading ----

    /** Reloads the order list, then selects the given order if any. */
    private void loadOrders(final int selectOrderId) {
        ordersPlaceholderLabel.setText("Loading orders...");
        BackgroundTask.run(() -> orderService.loadOrders(), loaded -> {
            allOrders = loaded;
            applyFilter();
            selectOrder(selectOrderId);
        }, error -> {
            ordersPlaceholderLabel.setText("Orders could not be loaded.");
            Dialogs.error("Cannot load orders", error);
        });
    }

    /** Loads customers into the customer box, keeping the current pick. */
    private void loadCustomers() {
        BackgroundTask.run(() -> orderService.loadCustomers(), loaded -> {
            CustomerOption selected = customerComboBox.getValue();
            customers = loaded;
            customerComboBox.setItems(FXCollections.observableArrayList(customers));
            customerComboBox.setValue(null);
            if (selected != null) {
                selectCustomer(selected.getId(), selected.getName());
            }
        }, error -> {
            customerComboBox.setPlaceholder(new Label("Customers could not be loaded."));
            AppShell.setStatus("Could not load customers: " + Dialogs.friendlyMessage(error));
        });
    }

    /** Loads active services into the service box. */
    private void loadServices() {
        BackgroundTask.run(() -> orderService.loadActiveServices(), loaded -> {
            serviceComboBox.setItems(FXCollections.observableArrayList(loaded));
            serviceComboBox.setValue(null);
        }, error -> {
            serviceComboBox.setPlaceholder(new Label("Services could not be loaded."));
            AppShell.setStatus("Could not load services: " + Dialogs.friendlyMessage(error));
        });
    }

    /** Loads one order and shows it in the details panel. */
    private void openOrderDetails(final int orderId) {
        detailCustomerLabel.setText("Loading order...");
        BackgroundTask.run(() -> orderService.loadOrder(orderId), details -> {
            OrderSummary selected = ordersTable.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getId() == orderId) {
                showDetails(details);
            }
        }, error -> {
            showDetails(null);
            Dialogs.error("Cannot open order", error);
        });
    }

    // ---- Order list ----

    /** Search text or a filter changed. */
    @FXML
    private void handleFilter() {
        applyFilter();
    }

    /** Shows only the orders that match the search and filters. */
    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String digits = digitsOnly(query);
        String statusName = statusFilterComboBox.getValue();
        String paymentName = paymentFilterComboBox.getValue();

        List<OrderSummary> shown = new ArrayList<OrderSummary>();
        for (OrderSummary order : allOrders) {
            boolean statusMatches = statusName == null || ALL_STATUSES.equals(statusName)
                    || order.getStatusText().equals(statusName);
            boolean paymentMatches = paymentName == null || ALL_STATUSES.equals(paymentName)
                    || order.getPaymentText().equals(paymentName);
            String phone = digitsOnly(order.getCustomerPhone());
            boolean textMatches = query.isEmpty()
                    || order.getOrderNumber().toLowerCase().contains(query)
                    || containsAllWords(order.getCustomerName(), query)
                    || (digits.length() >= 3 && phone.contains(digits));
            if (statusMatches && paymentMatches && textMatches) {
                shown.add(order);
            }
        }
        ordersTable.setItems(FXCollections.observableArrayList(shown));

        if (allOrders.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders yet. Create one with the New Order form below.");
        } else if (shown.isEmpty()) {
            ordersPlaceholderLabel.setText("No orders match your search and filters.");
        }
    }

    /** A row in the order list was picked. */
    @FXML
    private void handleOrderSelected() {
        OrderSummary selected = ordersTable.getSelectionModel().getSelectedItem();
        if (selected != null && (shownOrder == null || shownOrder.getId() != selected.getId())) {
            openOrderDetails(selected.getId());
        }
    }

    /** Selects this order in the list, or opens it even if it's filtered out. */
    private void selectOrder(int orderId) {
        if (orderId <= 0) {
            return;
        }
        for (OrderSummary order : ordersTable.getItems()) {
            if (order.getId() == orderId) {
                ordersTable.getSelectionModel().select(order);
                ordersTable.scrollTo(order);
                openOrderDetails(orderId);
                return;
            }
        }
        if (shownOrder == null || shownOrder.getId() != orderId) {
            openOrderDetails(orderId);
        }
    }

    // ---- Order details ----

    /** Fills the details panel, or clears it when details is null. */
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
            detailItemsTable.setItems(FXCollections.<OrderItemLine>observableArrayList());
            detailNotesArea.clear();
            historyTable.setItems(FXCollections.<StatusChange>observableArrayList());
            showDetailActions();
            return;
        }

        OrderSummary summary = details.getSummary();
        String phone = summary.getCustomerPhone();
        detailOrderNumberLabel.setText(summary.getOrderNumber());
        detailCustomerLabel.setText(summary.getCustomerName());
        detailPhoneLabel.setText(phone == null || phone.isEmpty() ? "—" : phone);
        detailOrderDateLabel.setText(summary.getReceivedAt().format(DATE));
        String overdue = summary.isOverdue(DashboardService.today()) ? "  (overdue)" : "";
        detailDueDateLabel.setText(summary.getDueDateText() + overdue);
        showPill(detailStatusLabel, summary.getStatusText(), statusStyle(summary.getStatus()));
        showPill(detailPaymentLabel, summary.getPaymentText(), paymentStyle(summary.getPaymentStatus()));
        detailTotalLabel.setText(MoneyFormat.php(summary.getTotalAmount()));
        detailPaidLabel.setText(MoneyFormat.php(summary.getAmountPaid()));
        detailBalanceLabel.setText(MoneyFormat.php(summary.getBalance()));
        detailItemsTable.setItems(FXCollections.observableArrayList(details.getItems()));
        detailNotesArea.setText(details.getNotes() == null ? "" : details.getNotes());
        historyTable.setItems(FXCollections.observableArrayList(details.getStatusHistory()));
        if (!details.getStatusHistory().isEmpty()) {
            historyTable.scrollTo(details.getStatusHistory().size() - 1);
        }
        showDetailActions();
    }

    /** Enables only the actions this order allows right now. */
    private void showDetailActions() {
        if (shownOrder == null) {
            recordPaymentButton.setDisable(true);
            updateStatusButton.setDisable(true);
            releaseOrderButton.setDisable(true);
            editOrderButton.setDisable(true);
            cancelOrderButton.setDisable(true);
            deleteOrderButton.setDisable(true);
            showReleaseMessage(null);
            return;
        }
        OrderSummary summary = shownOrder.getSummary();
        OrderStatus next = shownOrder.getNextStatus();
        recordPaymentButton.setDisable(!shownOrder.isOpen() || summary.getBalance().signum() <= 0);
        updateStatusButton.setDisable(next == null);
        updateStatusButton.setTooltip(new Tooltip(next == null ? "No further stage." : "Move to " + next.getDisplayName()));
        releaseOrderButton.setDisable(!summary.isReleasable());
        editOrderButton.setDisable(!shownOrder.isOpen());
        cancelOrderButton.setDisable(!shownOrder.isCancellable());
        deleteOrderButton.setDisable(!shownOrder.isItemEditable());
        showReleaseMessage(releaseBlockReason(summary));
    }

    /** Why the order can't be released yet, or null if it can. */
    private static String releaseBlockReason(OrderSummary order) {
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

    // ---- New / edit order form ----

    /** Clears the form for a new order. */
    private void startNewOrder() {
        editingOrder = null;
        formTitleLabel.setText("New Order");
        formStatusLabel.setText("Order number is assigned when you save.");
        customerComboBox.setValue(null);
        serviceComboBox.setValue(null);
        formItems = new ArrayList<OrderItemLine>();
        showFormItems();
        expectedDatePicker.setValue(DashboardService.today().plusDays(1));
        notesArea.clear();
        Dialogs.clearMessage(formMessageLabel);
        applyFormState();
    }

    /** Loads an existing order into the form for editing. */
    private void loadIntoForm(OrderDetails order) {
        editingOrder = order;
        OrderSummary summary = order.getSummary();
        formTitleLabel.setText("Edit Order " + summary.getOrderNumber());
        formStatusLabel.setText(summary.getStatusText() + " · " + summary.getPaymentText());
        selectCustomer(order.getCustomerId(), summary.getCustomerName());
        serviceComboBox.setValue(null);
        formItems = new ArrayList<OrderItemLine>(order.getItems());
        showFormItems();
        expectedDatePicker.setValue(order.getExpectedCompletionDate());
        notesArea.setText(order.getNotes() == null ? "" : order.getNotes());
        Dialogs.clearMessage(formMessageLabel);
        applyFormState();
    }

    /** Refreshes the form's items table and total. */
    private void showFormItems() {
        itemsTable.setItems(FXCollections.observableArrayList(formItems));
        totalLabel.setText(MoneyFormat.php(OrderService.total(formItems)));
    }

    /** True while the customer and services can still change. */
    private boolean formItemsEditable() {
        return editingOrder == null || editingOrder.isItemEditable();
    }

    /** Enables only the form fields this order allows. */
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

        String hint = null;
        if (editingOrder != null && !itemsEditable) {
            String reason = editingOrder.getSummary().getStatus() != OrderStatus.RECEIVED
                    ? "Processing has started" : "A payment has been recorded";
            hint = reason + ", so the customer and services can no longer be changed. You can still update the "
                    + "expected completion date and notes.";
        }
        lockHintLabel.setText(hint == null ? "" : hint);
        lockHintLabel.setVisible(hint != null);
        lockHintLabel.setManaged(hint != null);
    }

    /** Picks this customer in the box (shows the name until the list loads). */
    private void selectCustomer(int customerId, String name) {
        for (CustomerOption customer : customers) {
            if (customer.getId() == customerId) {
                customerComboBox.setValue(customer);
                return;
            }
        }
        customerComboBox.setValue(new CustomerOption(customerId, name, null));
    }

    /** Typing in the customer box jumps to a matching name or phone. */
    @FXML
    private void handleCustomerTyped(KeyEvent event) {
        String typed = event.getCharacter();
        if (typed == null || typed.isEmpty() || Character.isISOControl(typed.charAt(0))) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTypeAheadMillis > TYPE_AHEAD_RESET_MILLIS) {
            typeAhead = "";
        }
        lastTypeAheadMillis = now;
        typeAhead = typeAhead + typed.toLowerCase();
        String digits = digitsOnly(typeAhead);
        for (CustomerOption customer : customers) {
            String phone = customer.getContactNumber() == null ? "" : customer.getContactNumber();
            if (containsAllWords(customer.getName(), typeAhead) || (!digits.isEmpty() && phone.contains(digits))) {
                customerComboBox.setValue(customer);
                break;
            }
        }
        event.consume();
    }

    /** A service was picked: switch the quantity box to kilos or pieces. */
    @FXML
    private void handleServiceChosen() {
        useQuantityFor(serviceComboBox.getValue());
    }

    /** Kilos go up by 0.5, pieces by 1. */
    private void useQuantityFor(ServiceOption service) {
        boolean perPiece = service != null && !service.isPerKilogram();
        SpinnerValueFactory.DoubleSpinnerValueFactory factory;
        if (perPiece) {
            factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 9999, 1, 1);
        } else {
            factory = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.25, 9999.99, 1, 0.5);
        }
        DecimalFormat format = new DecimalFormat(perPiece ? "#0" : "#0.##");
        factory.setConverter(new QuantityConverter(format, factory));
        quantitySpinner.setValueFactory(factory);
        quantitySpinner.getEditor().setText(format.format(factory.getValue()));
        if (service == null) {
            unitLabel.setText("kg / pcs");
        } else {
            unitLabel.setText(perPiece ? "pcs" : "kg");
        }
    }

    // ---- Form actions ----

    /** New Order button. */
    @FXML
    private void handleNew() {
        startNewOrder();
        customerComboBox.requestFocus();
    }

    /** Clear button. */
    @FXML
    private void handleClearForm() {
        startNewOrder();
    }

    /** New Customer button: goes to the Customers tab. */
    @FXML
    private void handleNewCustomer() {
        AppShell.openTab(View.CUSTOMERS);
    }

    /** Add button: adds the service, or adds to its quantity if it's already listed. */
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
            showFormItems();
            Dialogs.clearMessage(formMessageLabel);
            serviceComboBox.setValue(null);
            serviceComboBox.requestFocus();
        } catch (ServiceException e) {
            Dialogs.showError(formMessageLabel, e.getMessage());
            quantitySpinner.requestFocus();
        }
    }

    /** Removes one service line from the form. */
    private void removeFormItem(OrderItemLine item) {
        if (item != null && formItemsEditable()) {
            formItems.remove(item);
            showFormItems();
        }
    }

    /** Save button: creates or updates the order. */
    @FXML
    private void handleSave() {
        final OrderDetails editing = editingOrder;
        CustomerOption customer = customerComboBox.getValue();
        final int customerId = customer == null ? 0 : customer.getId();
        final List<OrderItemLine> items = new ArrayList<OrderItemLine>(formItems);
        final LocalDate expectedDate = expectedDatePicker.getValue();
        final String notes = notesArea.getText();
        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);

        BackgroundTask.run(() -> {
            if (editing == null) {
                return orderService.createOrder(customerId, items, expectedDate, notes);
            } else if (editing.isItemEditable()) {
                return orderService.updateOrder(editing.getId(), customerId, items, expectedDate, notes);
            } else {
                return orderService.updateOrderSchedule(editing.getId(), expectedDate, notes);
            }
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

    /** Locks the form while saving so the order isn't sent twice. */
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

    // ---- Detail actions ----

    /** Edit Order button. */
    @FXML
    private void handleEditOrder() {
        if (shownOrder != null && shownOrder.isOpen()) {
            loadIntoForm(shownOrder);
        }
    }

    /** Record Payment button: opens Payments for this order. */
    @FXML
    private void handleRecordPayment() {
        if (shownOrder == null) {
            AppShell.openTab(View.PAYMENTS);
        } else {
            PaymentsController.openForOrder(shownOrder.getId());
        }
    }

    /** Update Status button: moves the order to its next stage. */
    @FXML
    private void handleUpdateStatus() {
        OrderDetails order = shownOrder;
        OrderStatus next = order == null ? null : order.getNextStatus();
        if (next == null) {
            return;
        }
        String number = order.getSummary().getOrderNumber();
        if (!Dialogs.confirm("Update status", "Move " + number + " from "
                + order.getSummary().getStatusText() + " to " + next.getDisplayName() + "?")) {
            return;
        }
        final int orderId = order.getId();
        setDetailsBusy(true);
        BackgroundTask.run(() -> orderService.advanceStatus(orderId),
                updated -> afterDetailAction(updated, "Status updated", number + " is now " + next.getDisplayName() + "."),
                error -> afterDetailFailure(orderId, "Cannot update status", error));
    }

    /** Release Order button: hands the laundry back to the customer. */
    @FXML
    private void handleReleaseOrder() {
        OrderDetails order = shownOrder;
        if (order == null) {
            return;
        }
        String number = order.getSummary().getOrderNumber();
        String customer = order.getSummary().getCustomerName();
        if (!Dialogs.confirm("Release order", "Release " + number + " to " + customer + "?")) {
            return;
        }
        final int orderId = order.getId();
        setDetailsBusy(true);
        BackgroundTask.run(() -> {
            orderService.releaseOrder(orderId);
            return orderService.loadOrder(orderId);
        }, updated -> afterDetailAction(updated, "Order released", number + " has been released to " + customer + "."),
                error -> afterDetailFailure(orderId, "Cannot release order", error));
    }

    /** Cancel Order button: only for unpaid orders that haven't started. */
    @FXML
    private void handleCancelOrder() {
        OrderDetails order = shownOrder;
        if (order == null || !order.isCancellable()) {
            return;
        }
        String number = order.getSummary().getOrderNumber();
        if (!Dialogs.confirm("Cancel order", "Cancel " + number + " for " + order.getSummary().getCustomerName()
                + "?\n\nThe order stays in the records with the status Cancelled. This cannot be undone.")) {
            return;
        }
        final int orderId = order.getId();
        setDetailsBusy(true);
        BackgroundTask.run(() -> orderService.cancelOrder(orderId),
                updated -> afterDetailAction(updated, "Order cancelled", number + " has been cancelled."),
                error -> afterDetailFailure(orderId, "Cannot cancel order", error));
    }

    /** Delete Order button (owner only): for orders entered by mistake. */
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
        final int orderId = order.getId();
        setDetailsBusy(true);
        BackgroundTask.runVoid(() -> orderService.deleteOrder(orderId), () -> {
            setDetailsBusy(false);
            if (editingOrder != null && editingOrder.getId() == orderId) {
                startNewOrder();
            }
            showDetails(null);
            loadOrders(-1);
            AppShell.setStatus(number + " deleted.");
            Dialogs.info("Order deleted", number + " has been deleted.");
        }, error -> afterDetailFailure(orderId, "Cannot delete order", error));
    }

    /** Refreshes everything after a change, then shows a message. */
    private void afterDetailAction(OrderDetails updated, String title, String message) {
        setDetailsBusy(false);
        showDetails(updated);
        if (editingOrder != null && editingOrder.getId() == updated.getId()) {
            if (updated.isOpen()) {
                loadIntoForm(updated);
            } else {
                startNewOrder();
            }
        }
        loadOrders(updated.getId());
        AppShell.setStatus(message);
        Dialogs.info(title, message);
    }

    /** Shows the error and reloads the order to show its real state. */
    private void afterDetailFailure(int orderId, String title, Throwable error) {
        setDetailsBusy(false);
        Dialogs.error(title, error);
        openOrderDetails(orderId);
    }

    /** Disables the action buttons while saving. */
    private void setDetailsBusy(boolean busy) {
        if (busy) {
            recordPaymentButton.setDisable(true);
            updateStatusButton.setDisable(true);
            releaseOrderButton.setDisable(true);
            editOrderButton.setDisable(true);
            cancelOrderButton.setDisable(true);
            deleteOrderButton.setDisable(true);
            ordersTable.setDisable(true);
        } else {
            ordersTable.setDisable(false);
            showDetailActions();
        }
    }

    // ---- Helpers ----

    /** Index of the service in the form, or -1 if it isn't there. */
    private int indexOfService(int serviceId) {
        for (int i = 0; i < formItems.size(); i++) {
            if (formItems.get(i).getServiceId() == serviceId) {
                return i;
            }
        }
        return -1;
    }

    /** Color style for an order stage, or null. */
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

    /** Color style for a payment status. */
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

    /** Sets a status pill's text and color. */
    private static void showPill(Label pill, String text, String style) {
        pill.setText(text);
        List<String> oldStyles = new ArrayList<String>();
        for (String styleClass : pill.getStyleClass()) {
            if (styleClass.startsWith("status-") || styleClass.startsWith("pay-")) {
                oldStyles.add(styleClass);
            }
        }
        pill.getStyleClass().removeAll(oldStyles);
        if (style != null) {
            pill.getStyleClass().add(style);
        }
    }

    /** Keeps only the digits of the text, e.g. "0917-123" becomes "0917123". */
    private static String digitsOnly(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.toString();
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

    // ---- Table cells ----

    /** Order list cell colored by due date, stage or payment. */
    private static class OrderStyleCell extends TableCell<OrderSummary, String> {
        private final int styleKind;
        private String appliedStyle;

        OrderStyleCell(int styleKind) {
            this.styleKind = styleKind;
        }

        /** Sets the text and color. */
        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (appliedStyle != null) {
                getStyleClass().remove(appliedStyle);
                appliedStyle = null;
            }
            setText(empty ? null : value);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                return;
            }
            OrderSummary order = (OrderSummary) getTableRow().getItem();
            switch (styleKind) {
                case STYLE_DUE_DATE:
                    appliedStyle = order.isOverdue(DashboardService.today()) ? "cell-overdue" : null;
                    break;
                case STYLE_STATUS:
                    appliedStyle = statusStyle(order.getStatus());
                    break;
                default:
                    appliedStyle = paymentStyle(order.getPaymentStatus());
                    break;
            }
            if (appliedStyle != null) {
                getStyleClass().add(appliedStyle);
            }
        }
    }

    /** Makes an OrderStyleCell for each row. */
    private static class OrderStyleCellFactory
            implements Callback<TableColumn<OrderSummary, String>, TableCell<OrderSummary, String>> {
        private final int styleKind;

        OrderStyleCellFactory(int styleKind) {
            this.styleKind = styleKind;
        }

        /** Makes a new cell. */
        @Override
        public TableCell<OrderSummary, String> call(TableColumn<OrderSummary, String> column) {
            return new OrderStyleCell(styleKind);
        }
    }

    /** Shows the row number in the # column. */
    private static class RowNumberCell extends TableCell<OrderItemLine, String> {
        /** Shows 1, 2, 3... on filled rows only. */
        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            boolean hasRow = !empty && getTableRow() != null && getTableRow().getItem() != null;
            setText(hasRow ? String.valueOf(getIndex() + 1) : null);
        }
    }

    /** Makes a RowNumberCell for each row. */
    private static class RowNumberCellFactory
            implements Callback<TableColumn<OrderItemLine, String>, TableCell<OrderItemLine, String>> {
        /** Makes a new cell. */
        @Override
        public TableCell<OrderItemLine, String> call(TableColumn<OrderItemLine, String> column) {
            return new RowNumberCell();
        }
    }

    /** Cell with a trash button that removes the item from the form. */
    private class RemoveItemCell extends TableCell<OrderItemLine, String> implements EventHandler<ActionEvent> {
        private final Button removeButton = new Button();

        RemoveItemCell() {
            SVGPath trash = new SVGPath();
            trash.setContent(TRASH_ICON);
            trash.setScaleX(0.6);
            trash.setScaleY(0.6);
            trash.getStyleClass().add("icon-delete");
            removeButton.setGraphic(trash);
            removeButton.getStyleClass().add("icon-button");
            removeButton.setTooltip(new Tooltip("Remove this service"));
            removeButton.setOnAction(this);
        }

        /** Trash button clicked. */
        @Override
        public void handle(ActionEvent event) {
            removeFormItem((OrderItemLine) getTableRow().getItem());
        }

        /** Shows the trash button on filled rows; disabled once the order is locked. */
        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            boolean show = !empty && getTableRow() != null && getTableRow().getItem() != null;
            setGraphic(show ? removeButton : null);
            removeButton.setDisable(!formItemsEditable());
        }
    }

    /** Makes a RemoveItemCell for each row. */
    private class RemoveItemCellFactory
            implements Callback<TableColumn<OrderItemLine, String>, TableCell<OrderItemLine, String>> {
        /** Makes a new cell. */
        @Override
        public TableCell<OrderItemLine, String> call(TableColumn<OrderItemLine, String> column) {
            return new RemoveItemCell();
        }
    }

    /** Converts the quantity box text to a number and back. */
    private static class QuantityConverter extends StringConverter<Double> {
        private final DecimalFormat format;
        private final SpinnerValueFactory.DoubleSpinnerValueFactory factory;

        QuantityConverter(DecimalFormat format, SpinnerValueFactory.DoubleSpinnerValueFactory factory) {
            this.format = format;
            this.factory = factory;
        }

        /** Number to text. */
        @Override
        public String toString(Double value) {
            return value == null ? "" : format.format(value);
        }

        /** Text to number; keeps the old number if the text isn't valid. */
        @Override
        public Double fromString(String text) {
            try {
                return Double.valueOf(text.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return factory.getValue();
            } catch (NullPointerException e) {
                return factory.getValue();
            }
        }
    }
}
