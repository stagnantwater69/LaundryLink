package com.laundrylink.controller;

import com.laundrylink.model.Customer;
import com.laundrylink.model.CustomerOrderSummary;
import com.laundrylink.service.CustomerService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/** Customer Management UI: CRUD, search, and selected customer order history. */
public class CustomersController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    @FXML private TextField searchField;
    @FXML private Button refreshButton;
    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, String> lastNameColumn;
    @FXML private TableColumn<Customer, String> firstNameColumn;
    @FXML private TableColumn<Customer, String> middleNameColumn;
    @FXML private TableColumn<Customer, String> contactColumn;
    @FXML private TableColumn<Customer, String> addressColumn;
    @FXML private Label tablePlaceholderLabel;

    @FXML private Label formTitleLabel;
    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField contactNumberField;
    @FXML private TextArea addressField;
    @FXML private Label formMessageLabel;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button deleteButton;
    @FXML private Label orderCountLabel;

    @FXML private Label historyTitleLabel;
    @FXML private TableView<CustomerOrderSummary> orderHistoryTable;
    @FXML private TableColumn<CustomerOrderSummary, String> orderNumberColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> orderDateColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> servicesColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> orderStatusColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> orderTotalColumn;
    @FXML private TableColumn<CustomerOrderSummary, String> paymentColumn;
    @FXML private Label historyPlaceholderLabel;

    private final CustomerService customerService = new CustomerService();
    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final ObservableList<CustomerOrderSummary> orderHistory = FXCollections.observableArrayList();
    private int editingCustomerId = -1;
    private int selectedCustomerId = -1;

    @FXML
    private void initialize() {
        setUpCustomerTable();
        setUpOrderHistoryTable();
        searchField.textProperty().addListener((obs, oldValue, newValue) -> loadCustomers(newValue));
        customersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldCustomer, newCustomer) -> selectCustomer(newCustomer));
        clearForm();
        loadCustomers("");
    }

    private void setUpCustomerTable() {
        customersTable.setItems(customers);
        lastNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getLastName()));
        firstNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFirstName()));
        middleNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(nullToDash(cell.getValue().getMiddleName())));
        contactColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(nullToDash(cell.getValue().getContactNumber())));
        addressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(nullToDash(cell.getValue().getAddress())));
    }

    private void setUpOrderHistoryTable() {
        orderHistoryTable.setItems(orderHistory);
        orderNumberColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getOrderNumber()));
        orderDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatDate(cell.getValue().getReceivedAt())));
        servicesColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getServices()));
        orderStatusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getStatus().getDisplayName()));
        orderTotalColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getTotalAmount())));
        paymentColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPaymentStatus().getDisplayName()));
    }

    @FXML
    private void handleRefresh() {
        loadCustomers(searchField.getText());
    }

    @FXML
    private void handleNew() {
        customersTable.getSelectionModel().clearSelection();
        clearForm();
    }

    @FXML
    private void handleSave() {
        final String first = firstNameField.getText();
        final String middle = middleNameField.getText();
        final String last = lastNameField.getText();
        final String contact = contactNumberField.getText();
        final String address = addressField.getText();
        final int customerId = editingCustomerId;

        setBusy(true);
        BackgroundTask.run(() -> customerId < 0
                        ? customerService.addCustomer(first, middle, last, contact, address)
                        : customerService.updateCustomer(customerId, first, middle, last, contact, address),
                saved -> {
                    setBusy(false);
                    Dialogs.showSuccess(formMessageLabel,
                            customerId < 0 ? "Customer added." : "Customer updated.");
                    int savedId = saved.getId();
                    loadCustomers(searchField.getText(), savedId);
                },
                error -> {
                    setBusy(false);
                    Dialogs.showFailure(formMessageLabel, "Cannot save customer", error);
                });
    }

    @FXML
    private void handleDelete() {
        Customer customer = customersTable.getSelectionModel().getSelectedItem();
        if (customer == null || editingCustomerId < 0) {
            return;
        }
        if (!Dialogs.confirm("Delete Customer",
                "Delete " + customer.getFullName() + "?\n\nOnly customers with no existing orders can be deleted.")) {
            return;
        }

        int customerId = customer.getId();
        setBusy(true);
        BackgroundTask.runVoid(() -> customerService.deleteCustomer(customerId),
                () -> {
                    setBusy(false);
                    clearForm();
                    loadCustomers(searchField.getText());
                    AppShell.setStatus("Customer deleted.");
                },
                error -> {
                    setBusy(false);
                    Dialogs.showFailure(formMessageLabel, "Cannot delete customer", error);
                    loadOrderCount(customerId);
                });
    }

    private void loadCustomers(String search) {
        loadCustomers(search, selectedCustomerId);
    }

    private void loadCustomers(String search, int selectId) {
        tablePlaceholderLabel.setText("Loading customers...");
        BackgroundTask.run(() -> customerService.findCustomers(search),
                loaded -> {
                    customers.setAll(loaded);
                    tablePlaceholderLabel.setText(loaded.isEmpty()
                            ? (search == null || search.trim().isEmpty() ? "No customer records yet." : "No customers match search.")
                            : "");
                    selectCustomerById(selectId);
                    AppShell.setStatus(loaded.size() + (loaded.size() == 1 ? " customer" : " customers") + " loaded.");
                },
                error -> {
                    tablePlaceholderLabel.setText("Customers could not be loaded.");
                    Dialogs.error("Cannot load customers", error);
                });
    }

    private void selectCustomer(Customer customer) {
        if (customer == null) {
            return;
        }
        selectedCustomerId = customer.getId();
        editingCustomerId = customer.getId();
        formTitleLabel.setText("Edit Customer");
        saveButton.setText("Update Customer");
        firstNameField.setText(customer.getFirstName());
        middleNameField.setText(nullToEmpty(customer.getMiddleName()));
        lastNameField.setText(customer.getLastName());
        contactNumberField.setText(nullToEmpty(customer.getContactNumber()));
        addressField.setText(nullToEmpty(customer.getAddress()));
        Dialogs.clearMessage(formMessageLabel);
        loadOrderCount(customer.getId());
        loadOrderHistory(customer);
    }

    private void selectCustomerById(int id) {
        if (id < 0) {
            return;
        }
        for (Customer customer : customers) {
            if (customer.getId() == id) {
                customersTable.getSelectionModel().select(customer);
                customersTable.scrollTo(customer);
                return;
            }
        }
        if (customersTable.getSelectionModel().getSelectedItem() == null) {
            clearForm();
        }
    }

    private void loadOrderCount(int customerId) {
        BackgroundTask.run(() -> customerService.getOrderCount(customerId),
                count -> {
                    orderCountLabel.setText(count + (count == 1 ? " existing order" : " existing orders"));
                    deleteButton.setDisable(count > 0 || editingCustomerId < 0);
                },
                error -> {
                    orderCountLabel.setText("Order count unavailable");
                    deleteButton.setDisable(true);
                });
    }

    private void loadOrderHistory(Customer customer) {
        historyTitleLabel.setText("Order History — " + customer.getFullName());
        historyPlaceholderLabel.setText("Loading order history...");
        BackgroundTask.run(() -> customerService.getOrderHistory(customer.getId()),
                loaded -> {
                    orderHistory.setAll(loaded);
                    historyPlaceholderLabel.setText(loaded.isEmpty() ? "No orders for this customer." : "");
                },
                error -> {
                    orderHistory.clear();
                    historyPlaceholderLabel.setText("Order history could not be loaded.");
                    Dialogs.error("Cannot load order history", error);
                });
    }

    private void clearForm() {
        editingCustomerId = -1;
        selectedCustomerId = -1;
        formTitleLabel.setText("New Customer");
        saveButton.setText("Add Customer");
        firstNameField.clear();
        middleNameField.clear();
        lastNameField.clear();
        contactNumberField.clear();
        addressField.clear();
        orderCountLabel.setText("Select customer");
        orderHistory.clear();
        historyTitleLabel.setText("Order History");
        historyPlaceholderLabel.setText("Select a customer to view order history.");
        deleteButton.setDisable(true);
        Dialogs.clearMessage(formMessageLabel);
    }

    private void setBusy(boolean busy) {
        saveButton.setDisable(busy);
        newButton.setDisable(busy);
        deleteButton.setDisable(busy || editingCustomerId < 0);
        refreshButton.setDisable(busy);
        searchField.setDisable(busy);
    }

    private static String nullToDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatDate(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_TIME);
    }
}
