package com.laundrylink.controller;

import com.laundrylink.model.Customer;
import com.laundrylink.model.CustomerOrderSummary;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PaymentStatus;
import com.laundrylink.model.Sex;
import com.laundrylink.service.CustomerService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.Sprites;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * Customer Management UI: list with search and filter, a details panel with the
 * selected customer's order history, and an Add / Edit pop-up form.
 */
public class CustomersController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private static final String SHOW_ALL = "All Customers";
    private static final String SHOW_WITH_ORDERS = "With Orders";
    private static final String SHOW_NO_ORDERS = "No Orders Yet";

    private static final String EYE_ICON = "M12 5 C6 5 2 12 2 12 C2 12 6 19 12 19 C18 19 22 12 22 12"
            + " C22 12 18 5 12 5 Z M12 8 A4 4 0 1 0 12 16 A4 4 0 1 0 12 8 Z M12 10.5 A1.5 1.5 0 1 1 12 13.5"
            + " A1.5 1.5 0 1 1 12 10.5 Z";
    private static final String PENCIL_ICON = "M3 17.5 L3 21 L6.5 21 L18 9.5 L14.5 6 Z M15.5 5 L19 8.5 L21 6.5"
            + " L17.5 3 Z";

    @FXML private Button addCustomerButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> showFilterComboBox;
    @FXML private Button refreshButton;
    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, String> idColumn;
    @FXML private TableColumn<Customer, String> fullNameColumn;
    @FXML private TableColumn<Customer, String> contactColumn;
    @FXML private TableColumn<Customer, String> ordersColumn;
    @FXML private TableColumn<Customer, String> actionsColumn;
    @FXML private Label tablePlaceholderLabel;

    @FXML private Label detailsEmptyLabel;
    @FXML private HBox detailsContent;
    @FXML private ImageView avatarImage;
    @FXML private Label detailIdLabel;
    @FXML private Label detailNameLabel;
    @FXML private Label detailContactLabel;
    @FXML private Label detailAddressLabel;
    @FXML private Label detailRegisteredLabel;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Label orderCountLabel;
    @FXML private Label detailMessageLabel;

    @FXML private Label historyTitleLabel;
    @FXML private TableView<CustomerOrderSummary> orderHistoryTable;
    @FXML private TableColumn<CustomerOrderSummary, String> orderNumberColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> servicesColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> orderTotalColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> orderStatusColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> paymentColumn;
    @FXML private Label historyPlaceholderLabel;

    @FXML private StackPane formOverlay;
    @FXML private Label formTitleLabel;
    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private ImageView formAvatarImage;
    @FXML private RadioButton maleRadio;
    @FXML private RadioButton femaleRadio;
    @FXML private RadioButton unspecifiedRadio;
    @FXML private TextField contactNumberField;
    @FXML private TextArea addressField;
    @FXML private Label formMessageLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelFormButton;
    @FXML private Button closeFormButton;

    private final CustomerService customerService = new CustomerService();
    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final FilteredList<Customer> filteredCustomers = new FilteredList<>(customers, customer -> true);
    private final ObservableList<CustomerOrderSummary> orderHistory = FXCollections.observableArrayList();
    private final ToggleGroup sexGroup = new ToggleGroup();
    /** Customer shown in the details panel, or null. */
    private Customer selectedCustomer;
    /** Customer being edited in the pop-up; -1 while adding a new one. */
    private int editingCustomerId = -1;
    /** Row to select and message to show once the next list load finishes. */
    private int pendingSelectId = -1;
    private String pendingMessage;

    @FXML
    private void initialize() {
        setUpCustomerTable();
        setUpOrderHistoryTable();

        showFilterComboBox.setItems(FXCollections.observableArrayList(SHOW_ALL, SHOW_WITH_ORDERS, SHOW_NO_ORDERS));
        showFilterComboBox.setValue(SHOW_ALL);
        showFilterComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> loadCustomers(newValue, selectedId()));
        customersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldCustomer, newCustomer) -> showDetails(newCustomer));
        maleRadio.setToggleGroup(sexGroup);
        femaleRadio.setToggleGroup(sexGroup);
        unspecifiedRadio.setToggleGroup(sexGroup);
        sexGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
                formAvatarImage.setImage(Sprites.avatar(selectedSex())));
        formOverlay.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closeForm();
                event.consume();
            }
        });

        showDetails(null);
        loadCustomers("", -1);
    }

    private void setUpCustomerTable() {
        customersTable.setItems(filteredCustomers);
        idColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getCustomerCode()));
        fullNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFullName()));
        contactColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(nullToDash(cell.getValue().getContactNumber())));
        ordersColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(cell.getValue().getOrderCount())));
        idColumn.setCellFactory(column -> styledCell(customer -> "code-cell"));
        ordersColumn.setCellFactory(column -> styledCell(customer -> "center-cell"));
        actionsColumn.setCellFactory(column -> new ActionsCell());
    }

    private void setUpOrderHistoryTable() {
        orderHistoryTable.setItems(orderHistory);
        orderNumberColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getOrderNumber()));
        servicesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(nullToDash(cell.getValue().getServices())));
        orderTotalColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getTotalAmount())));
        orderStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus().getDisplayName()));
        paymentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPaymentStatus().getDisplayName()));
        orderStatusColumn.setCellFactory(column -> styledCell(order -> statusStyle(order.getStatus())));
        paymentColumn.setCellFactory(column -> styledCell(order -> paymentStyle(order.getPaymentStatus())));
    }

    // ------------------------------------------------------------------
    // Toolbar and details actions
    // ------------------------------------------------------------------

    @FXML
    private void handleRefresh() {
        loadCustomers(searchField.getText(), selectedId());
    }

    @FXML
    private void handleAdd() {
        openForm(null);
    }

    @FXML
    private void handleEdit() {
        if (selectedCustomer != null) {
            openForm(selectedCustomer);
        }
    }

    @FXML
    private void handleDelete() {
        Customer customer = selectedCustomer;
        if (customer == null) {
            return;
        }
        if (!Dialogs.confirm("Delete Customer",
                "Delete " + customer.getFullName() + "?\n\nOnly customers with no existing orders can be deleted.")) {
            return;
        }

        int customerId = customer.getId();
        setDetailsBusy(true);
        BackgroundTask.runVoid(() -> customerService.deleteCustomer(customerId),
                () -> {
                    setDetailsBusy(false);
                    pendingMessage = customer.getFullName() + " was deleted.";
                    loadCustomers(searchField.getText(), -1);
                    AppShell.setStatus("Customer deleted.");
                },
                error -> {
                    setDetailsBusy(false);
                    Dialogs.showFailure(detailMessageLabel, "Cannot delete customer", error);
                    loadOrderCount(customerId);
                });
    }

    // ------------------------------------------------------------------
    // Add / Edit pop-up
    // ------------------------------------------------------------------

    private void openForm(Customer customer) {
        editingCustomerId = customer == null ? -1 : customer.getId();
        formTitleLabel.setText(customer == null ? "Add Customer" : "Edit Customer");
        saveButton.setText(customer == null ? "Save Customer" : "Save Changes");
        firstNameField.setText(customer == null ? "" : customer.getFirstName());
        middleNameField.setText(customer == null ? "" : nullToEmpty(customer.getMiddleName()));
        lastNameField.setText(customer == null ? "" : customer.getLastName());
        showSexInForm(customer == null ? null : customer.getSex());
        contactNumberField.setText(customer == null ? "" : nullToEmpty(customer.getContactNumber()));
        addressField.setText(customer == null ? "" : nullToEmpty(customer.getAddress()));
        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(false);

        formOverlay.setManaged(true);
        formOverlay.setVisible(true);
        firstNameField.requestFocus();
    }

    @FXML
    private void handleCancelForm() {
        closeForm();
    }

    private void closeForm() {
        formOverlay.setVisible(false);
        formOverlay.setManaged(false);
        editingCustomerId = -1;
    }

    @FXML
    private void handleSave() {
        final String first = firstNameField.getText();
        final String middle = middleNameField.getText();
        final String last = lastNameField.getText();
        final Sex sex = selectedSex();
        final String contact = contactNumberField.getText();
        final String address = addressField.getText();
        final int customerId = editingCustomerId;

        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);
        BackgroundTask.run(() -> customerId < 0
                        ? customerService.addCustomer(first, middle, last, sex, contact, address)
                        : customerService.updateCustomer(customerId, first, middle, last, sex, contact, address),
                saved -> {
                    setFormBusy(false);
                    closeForm();
                    pendingSelectId = saved.getId();
                    pendingMessage = saved.getFullName() + (customerId < 0 ? " was added." : " was updated.");
                    showFilterComboBox.setValue(SHOW_ALL);
                    // Clear the search so the saved customer is in the list; clearing it reloads the list.
                    String search = searchField.getText();
                    if (search != null && !search.trim().isEmpty()) {
                        searchField.clear();
                    } else {
                        loadCustomers("", -1);
                    }
                    AppShell.setStatus(customerId < 0 ? "Customer added." : "Customer updated.");
                },
                error -> {
                    setFormBusy(false);
                    Dialogs.showFailure(formMessageLabel, "Cannot save customer", error);
                });
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private void loadCustomers(String search, int selectId) {
        tablePlaceholderLabel.setText("Loading customers...");
        refreshButton.setDisable(true);
        BackgroundTask.run(() -> customerService.findCustomers(search),
                loaded -> {
                    refreshButton.setDisable(false);
                    customers.setAll(loaded);
                    applyFilter();
                    selectById(pendingSelectId >= 0 ? pendingSelectId : selectId);
                    pendingSelectId = -1;
                    if (pendingMessage != null) {
                        Dialogs.showSuccess(detailMessageLabel, pendingMessage);
                        pendingMessage = null;
                    }
                    AppShell.setStatus(loaded.size() + (loaded.size() == 1 ? " customer" : " customers") + " loaded.");
                },
                error -> {
                    refreshButton.setDisable(false);
                    tablePlaceholderLabel.setText("Customers could not be loaded.");
                    Dialogs.error("Cannot load customers", error);
                });
    }

    private void applyFilter() {
        String show = showFilterComboBox.getValue();
        filteredCustomers.setPredicate(customer -> SHOW_WITH_ORDERS.equals(show) ? customer.getOrderCount() > 0
                : !SHOW_NO_ORDERS.equals(show) || customer.getOrderCount() == 0);

        String search = searchField.getText();
        boolean searching = search != null && !search.trim().isEmpty();
        if (customers.isEmpty()) {
            tablePlaceholderLabel.setText(searching ? "No customers match your search." : "No customer records yet.");
        } else if (filteredCustomers.isEmpty()) {
            tablePlaceholderLabel.setText("No customers match the selected filter.");
        }
    }

    private void selectById(int id) {
        for (Customer customer : filteredCustomers) {
            if (customer.getId() == id) {
                customersTable.getSelectionModel().select(customer);
                customersTable.scrollTo(customer);
                return;
            }
        }
        customersTable.getSelectionModel().clearSelection();
        showDetails(null);
    }

    private int selectedId() {
        return selectedCustomer == null ? -1 : selectedCustomer.getId();
    }

    private void showDetails(Customer customer) {
        selectedCustomer = customer;
        boolean shown = customer != null;
        detailsContent.setVisible(shown);
        detailsContent.setManaged(shown);
        detailsEmptyLabel.setVisible(!shown);
        detailsEmptyLabel.setManaged(!shown);
        editButton.setDisable(!shown);
        deleteButton.setDisable(true);
        orderCountLabel.setText("Customers with orders cannot be deleted.");

        if (!shown) {
            orderHistory.clear();
            historyTitleLabel.setText("Order History");
            historyPlaceholderLabel.setText("Select a customer to view order history.");
            return;
        }

        Dialogs.clearMessage(detailMessageLabel);
        avatarImage.setImage(Sprites.avatar(customer.getSex()));
        detailIdLabel.setText(customer.getCustomerCode());
        detailNameLabel.setText(customer.getFullName());
        detailContactLabel.setText(nullToDash(customer.getContactNumber()));
        detailAddressLabel.setText(nullToDash(customer.getAddress()));
        detailRegisteredLabel.setText(formatDate(customer.getCreatedAt()));
        loadOrderCount(customer.getId());
        loadOrderHistory(customer);
    }

    private void loadOrderCount(int customerId) {
        BackgroundTask.run(() -> customerService.getOrderCount(customerId),
                count -> {
                    if (selectedId() != customerId) {
                        return;
                    }
                    orderCountLabel.setText(count == 0
                            ? "No orders yet, so this customer can be deleted."
                            : (count == 1 ? "1 order" : count + " orders")
                                    + " on record. Customers with orders cannot be deleted.");
                    deleteButton.setDisable(count > 0);
                },
                error -> {
                    orderCountLabel.setText("Order count unavailable.");
                    deleteButton.setDisable(true);
                });
    }

    private void loadOrderHistory(Customer customer) {
        historyTitleLabel.setText("Order History");
        historyPlaceholderLabel.setText("Loading order history...");
        BackgroundTask.run(() -> customerService.getOrderHistory(customer.getId()),
                loaded -> {
                    if (selectedId() != customer.getId()) {
                        return;
                    }
                    orderHistory.setAll(loaded);
                    historyTitleLabel.setText("Order History (" + loaded.size() + ")");
                    historyPlaceholderLabel.setText("No orders for this customer yet.");
                },
                error -> {
                    orderHistory.clear();
                    historyPlaceholderLabel.setText("Order history could not be loaded.");
                    Dialogs.error("Cannot load order history", error);
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setFormBusy(boolean busy) {
        saveButton.setDisable(busy);
        cancelFormButton.setDisable(busy);
        closeFormButton.setDisable(busy);
        firstNameField.setDisable(busy);
        middleNameField.setDisable(busy);
        lastNameField.setDisable(busy);
        maleRadio.setDisable(busy);
        femaleRadio.setDisable(busy);
        unspecifiedRadio.setDisable(busy);
        contactNumberField.setDisable(busy);
        addressField.setDisable(busy);
    }

    private void showSexInForm(Sex sex) {
        sexGroup.selectToggle(sex == Sex.MALE ? maleRadio : sex == Sex.FEMALE ? femaleRadio : unspecifiedRadio);
        formAvatarImage.setImage(Sprites.avatar(sex));
    }

    /** The sex chosen in the form, or null for Not specified. */
    private Sex selectedSex() {
        if (sexGroup.getSelectedToggle() == maleRadio) {
            return Sex.MALE;
        }
        if (sexGroup.getSelectedToggle() == femaleRadio) {
            return Sex.FEMALE;
        }
        return null;
    }

    private void setDetailsBusy(boolean busy) {
        editButton.setDisable(busy || selectedCustomer == null);
        deleteButton.setDisable(true);
        addCustomerButton.setDisable(busy);
    }

    /** View / Edit buttons on each customer row. */
    private final class ActionsCell extends TableCell<Customer, String> {

        private final HBox buttons;

        ActionsCell() {
            Button view = rowButton("View", EYE_ICON, "icon-view");
            Button edit = rowButton("Edit", PENCIL_ICON, "icon-edit");
            view.setOnAction(event -> customersTable.getSelectionModel().select(getRowCustomer()));
            edit.setOnAction(event -> {
                Customer customer = getRowCustomer();
                if (customer != null) {
                    customersTable.getSelectionModel().select(customer);
                    openForm(customer);
                }
            });
            buttons = new HBox(6.0, view, edit);
            buttons.setStyle("-fx-alignment: center;");
        }

        private Customer getRowCustomer() {
            int index = getIndex();
            return index >= 0 && index < getTableView().getItems().size() ? getTableView().getItems().get(index) : null;
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            setGraphic(empty || getRowCustomer() == null ? null : buttons);
        }
    }

    private static Button rowButton(String text, String iconPath, String iconStyle) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);
        icon.getStyleClass().add(iconStyle);
        Button button = new Button(text, icon);
        button.setMnemonicParsing(false);
        button.getStyleClass().add("row-button");
        return button;
    }

    /** A text cell that adds a style class computed from its row item. */
    private static <T> TableCell<T, String> styledCell(Function<T, String> styleFor) {
        return new TableCell<T, String>() {
            private String appliedStyle;

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (appliedStyle != null) {
                    getStyleClass().remove(appliedStyle);
                    appliedStyle = null;
                }
                int index = getIndex();
                if (empty || value == null || index < 0 || index >= getTableView().getItems().size()) {
                    setText(null);
                    return;
                }
                setText(value);
                appliedStyle = styleFor.apply(getTableView().getItems().get(index));
                if (appliedStyle != null) {
                    getStyleClass().add(appliedStyle);
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

    private static String nullToDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatDate(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE);
    }
}
