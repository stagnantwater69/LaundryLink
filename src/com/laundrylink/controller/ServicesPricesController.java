package com.laundrylink.controller;

import com.laundrylink.model.LaundryService;
import com.laundrylink.model.PricingUnit;
import com.laundrylink.service.ServiceCatalogService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.SessionContext;
import java.util.List;
import java.util.Locale;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Read-only service catalog for staff and the owner.
 */
public class ServicesPricesController {

    private static final String ALL_STATUSES = "All";
    private static final String ACTIVE_STATUS = "Active";
    private static final String INACTIVE_STATUS = "Inactive";

    @FXML
    private Label pageSubtitleLabel;
    @FXML
    private TextField searchField;
    @FXML
    private Label statusFilterLabel;
    @FXML
    private ComboBox<String> statusFilterComboBox;
    @FXML
    private Button refreshButton;
    @FXML
    private TableView<LaundryService> servicesTable;
    @FXML
    private TableColumn<LaundryService, String> serviceNameColumn;
    @FXML
    private TableColumn<LaundryService, String> pricingUnitColumn;
    @FXML
    private TableColumn<LaundryService, String> priceColumn;
    @FXML
    private TableColumn<LaundryService, String> statusColumn;
    @FXML
    private Label tablePlaceholderLabel;
    @FXML
    private VBox managementPane;
    @FXML
    private Label formTitleLabel;
    @FXML
    private TextField serviceNameField;
    @FXML
    private ComboBox<PricingUnit> pricingUnitComboBox;
    @FXML
    private TextField priceField;

    private final ServiceCatalogService serviceCatalogService = new ServiceCatalogService();
    private final ObservableList<LaundryService> services = FXCollections.observableArrayList();
    private final FilteredList<LaundryService> filteredServices =
            new FilteredList<>(services, service -> true);

    @FXML
    private void initialize() {
        setUpTable();
        setUpFilters();
        setUpManagementForm();
        loadServices();
    }

    private void setUpTable() {
        servicesTable.setItems(filteredServices);
        serviceNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getServiceName()));
        pricingUnitColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getPricingUnit().getDisplayName()));
        priceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getCurrentPrice())));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActive() ? ACTIVE_STATUS : INACTIVE_STATUS));

        statusColumn.setCellFactory(column -> new TableCell<LaundryService, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-active", "status-inactive");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status);
                    getStyleClass().add(ACTIVE_STATUS.equals(status)
                            ? "status-active" : "status-inactive");
                }
            }
        });
    }

    private void setUpFilters() {
        boolean owner = SessionContext.isAdmin();
        pageSubtitleLabel.setText(owner
                ? "View the catalog and select a service to inspect its management details."
                : "View laundry services currently available for new orders.");

        statusFilterComboBox.setItems(FXCollections.observableArrayList(
                ALL_STATUSES, ACTIVE_STATUS, INACTIVE_STATUS));
        statusFilterComboBox.setValue(ALL_STATUSES);
        statusFilterLabel.setVisible(owner);
        statusFilterLabel.setManaged(owner);
        statusFilterComboBox.setVisible(owner);
        statusFilterComboBox.setManaged(owner);

        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilters());
        statusFilterComboBox.valueProperty().addListener((obs, oldStatus, newStatus) -> applyFilters());
    }

    private void setUpManagementForm() {
        boolean owner = SessionContext.isAdmin();
        managementPane.setVisible(owner);
        managementPane.setManaged(owner);
        if (!owner) {
            return;
        }

        pricingUnitComboBox.setItems(FXCollections.observableArrayList(PricingUnit.values()));
        servicesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldService, newService) -> showInForm(newService));
        clearForm();
    }

    @FXML
    private void handleNew() {
        clearForm();
        serviceNameField.requestFocus();
    }

    private void clearForm() {
        servicesTable.getSelectionModel().clearSelection();
        formTitleLabel.setText("New Service");
        serviceNameField.clear();
        pricingUnitComboBox.setValue(null);
        priceField.clear();
    }

    private void showInForm(LaundryService service) {
        if (service == null) {
            return;
        }
        formTitleLabel.setText("Service Details");
        serviceNameField.setText(service.getServiceName());
        pricingUnitComboBox.setValue(service.getPricingUnit());
        priceField.setText(service.getCurrentPrice().toPlainString());
    }

    @FXML
    private void handleRefresh() {
        loadServices();
    }

    private void loadServices() {
        refreshButton.setDisable(true);
        tablePlaceholderLabel.setText("Loading services...");
        AppShell.setStatus("Loading services and prices...");
        BackgroundTask.run(serviceCatalogService::listServicesForCurrentUser,
                (List<LaundryService> loaded) -> {
                    refreshButton.setDisable(false);
                    services.setAll(loaded);
                    applyFilters();
                    AppShell.setStatus("Ready. " + loaded.size() + " service"
                            + (loaded.size() == 1 ? "" : "s") + " loaded.");
                },
                error -> {
                    refreshButton.setDisable(false);
                    tablePlaceholderLabel.setText("Services could not be loaded.");
                    AppShell.setStatus("Could not load services and prices.");
                    Dialogs.error("Cannot load services", error);
                });
    }

    private void applyFilters() {
        String query = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedStatus = statusFilterComboBox.getValue();

        filteredServices.setPredicate(service -> {
            boolean nameMatches = query.isEmpty()
                    || service.getServiceName().toLowerCase(Locale.ROOT).contains(query);
            boolean statusMatches = selectedStatus == null || ALL_STATUSES.equals(selectedStatus)
                    || (ACTIVE_STATUS.equals(selectedStatus) && service.isActive())
                    || (INACTIVE_STATUS.equals(selectedStatus) && !service.isActive());
            return nameMatches && statusMatches;
        });

        if (services.isEmpty()) {
            tablePlaceholderLabel.setText("No services are available.");
        } else if (filteredServices.isEmpty()) {
            tablePlaceholderLabel.setText("No services match the selected filters.");
        } else {
            tablePlaceholderLabel.setText("");
        }
    }
}
