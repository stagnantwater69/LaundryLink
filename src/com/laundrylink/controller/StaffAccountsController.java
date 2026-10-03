package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AccountService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Owner screen for creating, editing, and activating/deactivating staff accounts.
 * New accounts are always Staff; the owner cannot create another owner.
 */
public class StaffAccountsController {

    @FXML
    private TextField searchField;
    @FXML
    private Button refreshButton;
    @FXML
    private TableView<User> accountsTable;
    @FXML
    private TableColumn<User, String> lastNameColumn;
    @FXML
    private TableColumn<User, String> firstNameColumn;
    @FXML
    private TableColumn<User, String> middleNameColumn;
    @FXML
    private TableColumn<User, String> usernameColumn;
    @FXML
    private TableColumn<User, String> statusColumn;
    @FXML
    private Label tablePlaceholderLabel;
    @FXML
    private Label formTitleLabel;
    @FXML
    private TextField firstNameField;
    @FXML
    private TextField middleNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField usernameField;
    @FXML
    private Label passwordLabel;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label passwordHintLabel;
    @FXML
    private Label formMessageLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button newButton;
    @FXML
    private Button toggleActiveButton;

    private final AccountService accountService = new AccountService();
    private final ObservableList<User> accounts = FXCollections.observableArrayList();
    private final FilteredList<User> filteredAccounts = new FilteredList<>(accounts, user -> true);

    @FXML
    private void initialize() {
        setUpTable();
        searchField.textProperty().addListener((obs, oldText, newText) -> applySearch(newText));
        accountsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldUser, newUser) -> showInForm(newUser));

        clearForm();
        loadAccounts(-1);
    }

    private void setUpTable() {
        accountsTable.setItems(filteredAccounts);
        lastNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getLastName()));
        firstNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getFirstName()));
        middleNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getMiddleName() == null ? "" : cell.getValue().getMiddleName()));
        usernameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getUsername()));
        statusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().isActive() ? "Active" : "Inactive"));

        statusColumn.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-active", "status-inactive");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status);
                    getStyleClass().add("Active".equals(status) ? "status-active" : "status-inactive");
                }
            }
        });
    }

    private void applySearch(String text) {
        String query = text == null ? "" : text.trim().toLowerCase();
        filteredAccounts.setPredicate(user -> query.isEmpty()
                || user.getFullName().toLowerCase().contains(query)
                || user.getSortableName().toLowerCase().contains(query)
                || user.getUsername().toLowerCase().contains(query));
    }

    /** Reloads the list; selects the account with the given ID afterwards (if any). */
    private void loadAccounts(int selectUserId) {
        refreshButton.setDisable(true);
        tablePlaceholderLabel.setText("Loading accounts...");
        BackgroundTask.run(accountService::listStaffAccounts,
                (List<User> users) -> {
                    refreshButton.setDisable(false);
                    tablePlaceholderLabel.setText("No staff accounts yet. Add one with the form on the right.");
                    accounts.setAll(users);
                    selectById(selectUserId);
                },
                error -> {
                    refreshButton.setDisable(false);
                    tablePlaceholderLabel.setText("Accounts could not be loaded.");
                    Dialogs.error("Cannot load accounts", error);
                });
    }

    private void selectById(int userId) {
        for (User user : filteredAccounts) {
            if (user.getId() == userId) {
                accountsTable.getSelectionModel().select(user);
                accountsTable.scrollTo(user);
                return;
            }
        }
        accountsTable.getSelectionModel().clearSelection();
    }

    private void showInForm(User user) {
        Dialogs.clearMessage(formMessageLabel);
        passwordField.clear();
        confirmPasswordField.clear();
        if (user == null) {
            clearForm();
            return;
        }
        formTitleLabel.setText("Edit Account");
        firstNameField.setText(user.getFirstName());
        middleNameField.setText(user.getMiddleName() == null ? "" : user.getMiddleName());
        lastNameField.setText(user.getLastName());
        usernameField.setText(user.getUsername());
        passwordLabel.setText("New Password");
        passwordHintLabel.setText("Leave both password fields blank to keep the current password.");

        toggleActiveButton.setText(user.isActive() ? "Deactivate Account" : "Activate Account");
        toggleActiveButton.setDisable(false);
    }

    private void clearForm() {
        formTitleLabel.setText("New Staff Account");
        firstNameField.clear();
        middleNameField.clear();
        lastNameField.clear();
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
        passwordLabel.setText("Password");
        passwordHintLabel.setText("Required for new accounts (at least "
                + AccountService.MIN_PASSWORD_LENGTH + " characters).");
        toggleActiveButton.setText("Deactivate Account");
        toggleActiveButton.setDisable(true);
    }

    @FXML
    private void handleNew() {
        accountsTable.getSelectionModel().clearSelection();
        clearForm();
        Dialogs.clearMessage(formMessageLabel);
        firstNameField.requestFocus();
    }

    @FXML
    private void handleRefresh() {
        User selected = accountsTable.getSelectionModel().getSelectedItem();
        loadAccounts(selected == null ? -1 : selected.getId());
    }

    @FXML
    private void handleSave() {
        User selected = accountsTable.getSelectionModel().getSelectedItem();
        String firstName = firstNameField.getText();
        String middleName = middleNameField.getText();
        String lastName = lastNameField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);

        BackgroundTask.run(() -> selected == null
                ? accountService.createStaffAccount(firstName, middleName, lastName, username,
                        password, confirmPassword)
                : accountService.updateAccount(selected.getId(), firstName, middleName, lastName, username,
                        password, confirmPassword),
                saved -> {
                    setFormBusy(false);
                    loadAccounts(saved.getId());
                    Dialogs.info(selected == null ? "Account created" : "Account updated",
                            saved.getFullName() + " (" + saved.getUsername() + ") was saved.");
                },
                error -> {
                    setFormBusy(false);
                    Dialogs.showFailure(formMessageLabel, "Cannot save account", error);
                });
    }

    @FXML
    private void handleToggleActive() {
        User selected = accountsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean activate = !selected.isActive();
        String action = activate ? "Activate" : "Deactivate";
        String message = activate
                ? "Allow " + selected.getFullName() + " to log in again?"
                : selected.getFullName() + " will no longer be able to log in. "
                + "Their records stay in the system.\n\nDeactivate this account?";
        if (!Dialogs.confirm(action + " account", message)) {
            return;
        }

        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);
        BackgroundTask.runVoid(() -> accountService.setAccountActive(selected.getId(), activate),
                () -> {
                    setFormBusy(false);
                    loadAccounts(selected.getId());
                    Dialogs.info("Account " + (activate ? "activated" : "deactivated"),
                            selected.getFullName() + " is now " + (activate ? "active." : "inactive."));
                },
                error -> {
                    setFormBusy(false);
                    Dialogs.showFailure(formMessageLabel, "Cannot change account status", error);
                });
    }

    private void setFormBusy(boolean busy) {
        saveButton.setDisable(busy);
        newButton.setDisable(busy);
        accountsTable.setDisable(busy);
        if (busy) {
            toggleActiveButton.setDisable(true);
        } else {
            showToggleState();
        }
        saveButton.setText(busy ? "Saving..." : "Save Account");
    }

    private void showToggleState() {
        User selected = accountsTable.getSelectionModel().getSelectedItem();
        toggleActiveButton.setDisable(selected == null);
    }
}
