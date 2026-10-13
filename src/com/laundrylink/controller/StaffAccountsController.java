package com.laundrylink.controller;

import com.laundrylink.model.User;
import com.laundrylink.service.AccountService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

/**
 * Staff Accounts tab (owner only): add, edit, and turn staff logins on or off.
 */
public class StaffAccountsController {

    @FXML private TextField searchField;
    @FXML private Button refreshButton;
    @FXML private TableView<User> accountsTable;
    @FXML private TableColumn<User, String> lastNameColumn;
    @FXML private TableColumn<User, String> firstNameColumn;
    @FXML private TableColumn<User, String> middleNameColumn;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private Label tablePlaceholderLabel;
    @FXML private Label formTitleLabel;
    @FXML private TextField firstNameField;
    @FXML private TextField middleNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField usernameField;
    @FXML private Label passwordLabel;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordHintLabel;
    @FXML private Label formMessageLabel;
    @FXML private Button saveButton;
    @FXML private Button newButton;
    @FXML private Button toggleActiveButton;

    private final AccountService accountService = new AccountService();

    // All staff accounts; the table only shows the ones that match the search.
    private List<User> allAccounts = new ArrayList<User>();
    // Account being edited, or null for a new one.
    private User selectedUser;

    /** Sets up the table and loads the accounts. */
    @FXML
    private void initialize() {
        lastNameColumn.setCellValueFactory(new PropertyValueFactory<User, String>("lastName"));
        firstNameColumn.setCellValueFactory(new PropertyValueFactory<User, String>("firstName"));
        middleNameColumn.setCellValueFactory(new PropertyValueFactory<User, String>("middleNameText"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<User, String>("username"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<User, String>("statusText"));
        statusColumn.setCellFactory(new StatusCellFactory());

        clearForm();
        loadAccounts(-1);
    }

    /** Reloads the accounts, then selects the given one (if any). */
    private void loadAccounts(final int selectUserId) {
        refreshButton.setDisable(true);
        tablePlaceholderLabel.setText("Loading accounts...");
        BackgroundTask.run(() -> accountService.listStaffAccounts(), users -> {
            refreshButton.setDisable(false);
            allAccounts = users;
            tablePlaceholderLabel.setText("No staff accounts yet. Add one with the form on the right.");
            applySearch();
            selectById(selectUserId);
        }, error -> {
            refreshButton.setDisable(false);
            tablePlaceholderLabel.setText("Accounts could not be loaded.");
            Dialogs.error("Cannot load accounts", error);
        });
    }

    /** Search text changed. */
    @FXML
    private void handleSearch() {
        applySearch();
    }

    /** Shows only accounts whose name or username matches the search. */
    private void applySearch() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        List<User> shown = new ArrayList<User>();
        for (User user : allAccounts) {
            boolean matches = query.isEmpty()
                    || user.getFullName().toLowerCase().contains(query)
                    || user.getSortableName().toLowerCase().contains(query)
                    || user.getUsername().toLowerCase().contains(query);
            if (matches) {
                shown.add(user);
            }
        }
        accountsTable.setItems(FXCollections.observableArrayList(shown));
    }

    /** Selects this account, or clears the selection if it isn't listed. */
    private void selectById(int userId) {
        for (User user : accountsTable.getItems()) {
            if (user.getId() == userId) {
                accountsTable.getSelectionModel().select(user);
                accountsTable.scrollTo(user);
                showInForm(user);
                return;
            }
        }
        accountsTable.getSelectionModel().clearSelection();
        showInForm(null);
    }

    /** A row in the table was picked. */
    @FXML
    private void handleAccountSelected() {
        User user = accountsTable.getSelectionModel().getSelectedItem();
        if (user != null && user != selectedUser) {
            showInForm(user);
        }
    }

    /** Loads the account into the form, or clears it when user is null. */
    private void showInForm(User user) {
        selectedUser = user;
        Dialogs.clearMessage(formMessageLabel);
        passwordField.clear();
        confirmPasswordField.clear();
        if (user == null) {
            clearForm();
            return;
        }
        formTitleLabel.setText("Edit Account");
        firstNameField.setText(user.getFirstName());
        middleNameField.setText(user.getMiddleNameText());
        lastNameField.setText(user.getLastName());
        usernameField.setText(user.getUsername());
        passwordLabel.setText("New Password");
        passwordHintLabel.setText("Leave both password fields blank to keep the current password.");
        toggleActiveButton.setText(user.isActive() ? "Deactivate Account" : "Activate Account");
        toggleActiveButton.setDisable(false);
    }

    /** Clears the form for a new account. */
    private void clearForm() {
        selectedUser = null;
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

    /** New button. */
    @FXML
    private void handleNew() {
        accountsTable.getSelectionModel().clearSelection();
        clearForm();
        Dialogs.clearMessage(formMessageLabel);
        firstNameField.requestFocus();
    }

    /** Refresh button: reloads and keeps the selection. */
    @FXML
    private void handleRefresh() {
        loadAccounts(selectedUser == null ? -1 : selectedUser.getId());
    }

    /** Save button: adds a new staff account or saves the edited one. */
    @FXML
    private void handleSave() {
        final User editing = selectedUser;
        final String firstName = firstNameField.getText();
        final String middleName = middleNameField.getText();
        final String lastName = lastNameField.getText();
        final String username = usernameField.getText();
        final String password = passwordField.getText();
        final String confirmPassword = confirmPasswordField.getText();
        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);

        BackgroundTask.run(() -> {
            if (editing == null) {
                return accountService.createStaffAccount(firstName, middleName, lastName, username,
                        password, confirmPassword);
            }
            return accountService.updateAccount(editing.getId(), firstName, middleName, lastName, username,
                    password, confirmPassword);
        }, saved -> {
            setFormBusy(false);
            loadAccounts(saved.getId());
            Dialogs.info(editing == null ? "Account created" : "Account updated",
                    saved.getFullName() + " (" + saved.getUsername() + ") was saved.");
        }, error -> {
            setFormBusy(false);
            Dialogs.showFailure(formMessageLabel, "Cannot save account", error);
        });
    }

    /** Activate/Deactivate button: turns a staff login on or off after asking. */
    @FXML
    private void handleToggleActive() {
        User user = selectedUser;
        if (user == null) {
            return;
        }
        boolean activate = !user.isActive();
        String message;
        if (activate) {
            message = "Allow " + user.getFullName() + " to log in again?";
        } else {
            message = user.getFullName() + " will no longer be able to log in. "
                    + "Their records stay in the system.\n\nDeactivate this account?";
        }
        if (!Dialogs.confirm((activate ? "Activate" : "Deactivate") + " account", message)) {
            return;
        }

        Dialogs.clearMessage(formMessageLabel);
        setFormBusy(true);
        BackgroundTask.runVoid(() -> accountService.setAccountActive(user.getId(), activate), () -> {
            setFormBusy(false);
            loadAccounts(user.getId());
            Dialogs.info("Account " + (activate ? "activated" : "deactivated"),
                    user.getFullName() + " is now " + (activate ? "active." : "inactive."));
        }, error -> {
            setFormBusy(false);
            Dialogs.showFailure(formMessageLabel, "Cannot change account status", error);
        });
    }

    /** Disables the buttons while saving. */
    private void setFormBusy(boolean busy) {
        saveButton.setText(busy ? "Saving..." : "Save Account");
        saveButton.setDisable(busy);
        newButton.setDisable(busy);
        accountsTable.setDisable(busy);
        toggleActiveButton.setDisable(busy || selectedUser == null);
    }

    /** Status cell: green for Active, grey for Inactive. */
    private static class StatusCell extends TableCell<User, String> {
        /** Sets the text and color. */
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
    }

    /** Makes a StatusCell for each row. */
    private static class StatusCellFactory implements Callback<TableColumn<User, String>, TableCell<User, String>> {
        /** Makes a new cell. */
        @Override
        public TableCell<User, String> call(TableColumn<User, String> column) {
            return new StatusCell();
        }
    }
}
