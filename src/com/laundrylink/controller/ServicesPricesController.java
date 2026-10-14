package com.laundrylink.controller;

import com.laundrylink.model.LaundryService;
import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PricingUnit;
import com.laundrylink.model.ServicePreset;
import com.laundrylink.service.ServiceCatalogService;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.SessionContext;
import com.laundrylink.util.Sprites;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

/** Service catalog and owner-only management screen. */
public class ServicesPricesController {

    private static final String ALL_STATUSES = "All";
    private static final String ACTIVE_STATUS = "Active";
    private static final String INACTIVE_STATUS = "Inactive";

    private static final String BLOCK_ICON = "M12 2 A10 10 0 1 1 12 22 A10 10 0 1 1 12 2 Z"
            + " M6.3 8.1 L15.9 17.7 A7 7 0 0 0 6.3 8.1 Z M8.1 6.3 A7 7 0 0 1 17.7 15.9 Z";
    private static final String CHECK_ICON = "M2 12 L5 9 L10 14 L19 5 L22 8 L10 20 Z";
    private static final ColorAdjust INACTIVE_SPRITE = new ColorAdjust(0, -1, 0, 0);

    @FXML
    private Label adminOnlyLabel;
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
    private TableColumn<LaundryService, String> stagesColumn;
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
    private ImageView serviceSpriteImage;
    @FXML
    private ComboBox<String> serviceNameComboBox;
    @FXML
    private CheckBox washingCheckBox;
    @FXML
    private CheckBox dryingCheckBox;
    @FXML
    private CheckBox ironingCheckBox;
    @FXML
    private ComboBox<PricingUnit> pricingUnitComboBox;
    @FXML
    private TextField priceField;
    @FXML
    private Label statusValueLabel;
    @FXML
    private Circle statusDot;
    @FXML
    private Label formHintLabel;
    @FXML
    private Label formMessageLabel;
    @FXML
    private Button newButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button statusButton;
    @FXML
    private SVGPath statusButtonIcon;

    private LaundryService selectedService;

    private final ServiceCatalogService serviceCatalogService = new ServiceCatalogService();
    private final ObservableList<LaundryService> services = FXCollections.observableArrayList();
    private final FilteredList<LaundryService> filteredServices =
            new FilteredList<>(services, service -> true);

    @FXML
    private void initialize() {
        setUpTable();
        setUpFilters();
        setUpManagementForm();
        loadServices(-1);
    }

    private void setUpTable() {
        servicesTable.setItems(filteredServices);
        serviceNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getServiceName()));
        pricingUnitColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getPricingUnit().getDisplayName()));
        stagesColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getStagesText()));
        priceColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(MoneyFormat.php(cell.getValue().getCurrentPrice())));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isActive() ? ACTIVE_STATUS : INACTIVE_STATUS));

        serviceNameColumn.setCellFactory(column -> new TableCell<LaundryService, String>() {
            private final ImageView icon = new ImageView();

            {
                icon.setFitWidth(34.0);
                icon.setFitHeight(34.0);
                icon.setPreserveRatio(true);
                icon.setSmooth(false);
                getStyleClass().add("service-name-cell");
            }

            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                LaundryService service = rowService(this);
                if (empty || name == null || service == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(name);
                    icon.setImage(Sprites.service(name));
                    // Inactive services show a faded, gray sprite.
                    icon.setEffect(service.isActive() ? null : INACTIVE_SPRITE);
                    icon.setOpacity(service.isActive() ? 1.0 : 0.6);
                    setGraphic(icon);
                }
            }
        });
        pricingUnitColumn.setCellFactory(column -> centeredCell());
        stagesColumn.setCellFactory(column -> centeredCell());
        priceColumn.setCellFactory(column -> centeredCell());

        statusColumn.setCellFactory(column -> new TableCell<LaundryService, String>() {
            private final Circle dot = new Circle(6.0);

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-active", "status-inactive");
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    boolean active = ACTIVE_STATUS.equals(status);
                    setText(status);
                    getStyleClass().add(active ? "status-active" : "status-inactive");
                    showStatusDot(dot, active);
                    setGraphic(dot);
                    setGraphicTextGap(8.0);
                }
            }
        });
    }

    private static TableCell<LaundryService, String> centeredCell() {
        TableCell<LaundryService, String> cell = new TableCell<LaundryService, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
            }
        };
        cell.getStyleClass().add("center-cell");
        return cell;
    }

    private static LaundryService rowService(TableCell<LaundryService, ?> cell) {
        int index = cell.getIndex();
        TableView<LaundryService> table = cell.getTableView();
        return table == null || index < 0 || index >= table.getItems().size() ? null : table.getItems().get(index);
    }

    private static void showStatusDot(Circle dot, boolean active) {
        dot.getStyleClass().removeAll("status-dot", "active", "inactive");
        dot.getStyleClass().addAll("status-dot", active ? "active" : "inactive");
    }

    private void setUpFilters() {
        boolean owner = SessionContext.isAdmin();
        pageSubtitleLabel.setText(owner
                ? "You can add, edit, and deactivate services."
                : "Laundry services currently available for new orders.");
        adminOnlyLabel.setVisible(owner);
        adminOnlyLabel.setManaged(owner);

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
        newButton.setVisible(owner);
        newButton.setManaged(owner);
        if (!owner) {
            return;
        }

        pricingUnitComboBox.setItems(FXCollections.observableArrayList(PricingUnit.values()));
        // Preview the sprite the service will get, picked from words in its name.
        serviceNameComboBox.getEditor().textProperty().addListener((obs, oldName, newName) ->
                serviceSpriteImage.setImage(Sprites.service(newName)));
        // Picking a ready-made service fills in its unit and stages; the owner adds the price.
        serviceNameComboBox.valueProperty().addListener((obs, oldName, newName) -> applyPreset(newName));
        serviceSpriteImage.setImage(Sprites.service(""));
        servicesTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldService, newService) -> showInForm(newService));
        clearForm();
    }

    @FXML
    private void handleNew() {
        clearForm();
        serviceNameComboBox.requestFocus();
    }

    /** Ready-made services the shop doesn't have yet (plus the one being edited). */
    private void showPresetChoices(LaundryService editing) {
        List<String> names = new ArrayList<>();
        for (ServicePreset preset : ServicePreset.ALL) {
            boolean taken = false;
            for (LaundryService service : services) {
                if (service != editing && service.getServiceName().equalsIgnoreCase(preset.getName())) {
                    taken = true;
                }
            }
            if (!taken) {
                names.add(preset.getName());
            }
        }
        String typed = serviceNameText();
        serviceNameComboBox.setItems(FXCollections.observableArrayList(names));
        serviceNameComboBox.getEditor().setText(typed);
    }

    /** Fills the unit and stages when a ready-made service is picked (only for new services). */
    private void applyPreset(String name) {
        ServicePreset preset = ServicePreset.find(name);
        if (preset == null || selectedService != null) {
            return;
        }
        pricingUnitComboBox.setValue(preset.getPricingUnit());
        showStages(preset.getStages());
        // Move to Price only after the dropdown finishes updating. Moving focus right away
        // makes the editable dropdown save its old text, so a second pick snapped back to the first.
        Platform.runLater(priceField::requestFocus);
    }

    /** Ticks the stage boxes. */
    private void showStages(List<OrderStatus> stages) {
        washingCheckBox.setSelected(stages.contains(OrderStatus.WASHING));
        dryingCheckBox.setSelected(stages.contains(OrderStatus.DRYING));
        ironingCheckBox.setSelected(stages.contains(OrderStatus.IRONING));
    }

    /** The ticked stages. */
    private List<OrderStatus> selectedStages() {
        List<OrderStatus> stages = new ArrayList<>();
        if (washingCheckBox.isSelected()) {
            stages.add(OrderStatus.WASHING);
        }
        if (dryingCheckBox.isSelected()) {
            stages.add(OrderStatus.DRYING);
        }
        if (ironingCheckBox.isSelected()) {
            stages.add(OrderStatus.IRONING);
        }
        return stages;
    }

    /** The typed or picked service name. */
    private String serviceNameText() {
        return serviceNameComboBox.getEditor().getText();
    }

    private void clearForm() {
        selectedService = null;
        servicesTable.getSelectionModel().clearSelection();
        formTitleLabel.setText("New Service");
        showPresetChoices(null);
        serviceNameComboBox.setValue(null);
        serviceNameComboBox.getEditor().clear();
        pricingUnitComboBox.setValue(null);
        showStages(new ArrayList<OrderStatus>());
        priceField.clear();
        formHintLabel.setText("New services are active by default and can be used in new orders right away.");
        showFormStatus(true, "Active by default (available for new orders)");
        saveButton.setText("Add Service");
        saveButton.setDisable(false);
        statusButton.setVisible(false);
        statusButton.setManaged(false);
        Dialogs.clearMessage(formMessageLabel);
    }

    private void showInForm(LaundryService service) {
        if (service == null) {
            return;
        }
        selectedService = service;
        formTitleLabel.setText("Edit Service");
        showPresetChoices(service);
        serviceNameComboBox.setValue(service.getServiceName());
        serviceNameComboBox.getEditor().setText(service.getServiceName());
        pricingUnitComboBox.setValue(service.getPricingUnit());
        showStages(service.getStages());
        priceField.setText(service.getCurrentPrice().toPlainString());
        formHintLabel.setText("Price changes apply to new orders. Existing orders retain their original prices.");
        showFormStatus(service.isActive(), service.isActive()
                ? "Active (available for new orders)"
                : "Inactive (hidden from new orders)");
        saveButton.setText("Save Changes");
        saveButton.setDisable(false);
        showStatusButton(service.isActive());
        statusButton.setVisible(true);
        statusButton.setManaged(true);
        Dialogs.clearMessage(formMessageLabel);
    }

    @FXML
    private void handleSave() {
        BigDecimal price;
        try {
            String priceText = priceField.getText() == null ? "" : priceField.getText().trim();
            price = priceText.isEmpty() ? null : new BigDecimal(priceText);
        } catch (NumberFormatException e) {
            Dialogs.showError(formMessageLabel, "Enter a valid price, such as 65.00.");
            priceField.requestFocus();
            return;
        }

        LaundryService serviceToUpdate = selectedService;
        PricingUnit selectedUnit = pricingUnitComboBox.getValue();
        if (serviceToUpdate != null
                && selectedUnit != null
                && selectedUnit != serviceToUpdate.getPricingUnit()
                && !Dialogs.confirm("Change pricing unit",
                        "Change " + serviceToUpdate.getServiceName() + " from "
                        + serviceToUpdate.getPricingUnit().getDisplayName() + " to "
                        + selectedUnit.getDisplayName() + "?\n\n"
                        + "This affects new orders only. Existing orders keep their original unit and price.")) {
            return;
        }

        String name = serviceNameText();
        List<OrderStatus> stages = selectedStages();
        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);
        BackgroundTask.run(() -> serviceToUpdate == null
                ? serviceCatalogService.createService(name, selectedUnit, price, stages)
                : serviceCatalogService.updateService(
                        serviceToUpdate.getId(), name, selectedUnit, price, stages),
                saved -> {
                    setFormBusy(false);
                    Dialogs.showSuccess(formMessageLabel,
                            saved.getServiceName() + (serviceToUpdate == null
                                    ? " was added successfully." : " was updated successfully."));
                    loadServices(saved.getId());
                },
                error -> {
                    setFormBusy(false);
                    Dialogs.showFailure(formMessageLabel,
                            serviceToUpdate == null ? "Cannot add service" : "Cannot update service",
                            error);
                });
    }

    @FXML
    private void handleStatusChange() {
        LaundryService service = selectedService;
        if (service == null) {
            return;
        }

        boolean activate = !service.isActive();
        if (!activate && !Dialogs.confirm("Deactivate service",
                "Deactivate " + service.getServiceName() + "?\n\n"
                + "It will no longer be available for new orders. Existing orders will not change.")) {
            return;
        }

        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);
        statusButton.setText(activate ? "Activating..." : "Deactivating...");
        BackgroundTask.run(() -> serviceCatalogService.setServiceActive(service.getId(), activate),
                saved -> {
                    setFormBusy(false);
                    clearForm();
                    loadServices(-1);
                    AppShell.setStatus(saved.getServiceName()
                            + (saved.isActive() ? " was activated." : " was deactivated."));
                },
                error -> {
                    setFormBusy(false);
                    showStatusButton(service.isActive());
                    Dialogs.showFailure(formMessageLabel,
                            activate ? "Cannot activate service" : "Cannot deactivate service",
                            error);
                });
    }

    private void showFormStatus(boolean active, String text) {
        statusValueLabel.setText(text);
        showStatusDot(statusDot, active);
    }

    /** Deactivate (red block icon) for active services, Activate (green check) for inactive ones. */
    private void showStatusButton(boolean serviceActive) {
        statusButton.setText(serviceActive ? "Deactivate Service" : "Activate Service");
        statusButtonIcon.setContent(serviceActive ? BLOCK_ICON : CHECK_ICON);
        statusButtonIcon.getStyleClass().removeAll("icon-block", "icon-check");
        statusButtonIcon.getStyleClass().add(serviceActive ? "icon-block" : "icon-check");
    }

    private void setFormBusy(boolean busy) {
        newButton.setDisable(busy);
        cancelButton.setDisable(busy);
        saveButton.setDisable(busy);
        statusButton.setDisable(busy);
        serviceNameComboBox.setDisable(busy);
        pricingUnitComboBox.setDisable(busy);
        washingCheckBox.setDisable(busy);
        dryingCheckBox.setDisable(busy);
        ironingCheckBox.setDisable(busy);
        priceField.setDisable(busy);
        saveButton.setText(busy
                ? (selectedService == null ? "Adding..." : "Saving...")
                : (selectedService == null ? "Add Service" : "Save Changes"));
    }

    @FXML
    private void handleRefresh() {
        loadServices(selectedService == null ? -1 : selectedService.getId());
    }

    private void loadServices(int selectServiceId) {
        refreshButton.setDisable(true);
        tablePlaceholderLabel.setText("Loading services...");
        AppShell.setStatus("Loading services and prices...");
        BackgroundTask.run(serviceCatalogService::listServicesForCurrentUser,
                (List<LaundryService> loaded) -> {
                    refreshButton.setDisable(false);
                    services.setAll(loaded);
                    if (SessionContext.isAdmin()) {
                        showPresetChoices(selectedService);
                    }
                    applyFilters();
                    selectById(selectServiceId);
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

    private void selectById(int serviceId) {
        for (LaundryService service : filteredServices) {
            if (service.getId() == serviceId) {
                servicesTable.getSelectionModel().select(service);
                servicesTable.scrollTo(service);
                return;
            }
        }
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
