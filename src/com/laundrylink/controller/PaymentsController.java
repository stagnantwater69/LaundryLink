package com.laundrylink.controller;

import com.laundrylink.model.OrderStatus;
import com.laundrylink.model.PayableOrder;
import com.laundrylink.model.Payment;
import com.laundrylink.model.PaymentStatus;
import com.laundrylink.service.PaymentService;
import com.laundrylink.service.ServiceException;
import com.laundrylink.util.AppShell;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import com.laundrylink.util.MoneyFormat;
import com.laundrylink.util.View;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.Callback;

/**
 * Payments tab: daily totals, the day's payments, one order's history,
 * and the cash Record Payment form.
 */
public class PaymentsController {

    private static final String[] STATUS_OPTIONS = {"All Statuses", "Paid", "Partial"};
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /** Order to open when another tab sends the user here (0 = none). */
    private static int pendingOrderId = 0;

    @FXML private DatePicker datePicker;
    @FXML private Label cashCollectedLabel;
    @FXML private Label pendingPaymentsLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterComboBox;
    @FXML private TableView<Payment> recordsTable;
    @FXML private TableColumn<Payment, String> recordStatusColumn;
    @FXML private Label recordsPlaceholderLabel;

    @FXML private Label historyTitleLabel;
    @FXML private TableView<Payment> historyTable;
    @FXML private TableColumn<Payment, String> historyStatusColumn;
    @FXML private Label historyPlaceholderLabel;

    @FXML private ComboBox<PayableOrder> orderComboBox;
    @FXML private Label customerLabel;
    @FXML private Label serviceLabel;
    @FXML private Label quantityLabel;
    @FXML private Label totalLabel;
    @FXML private Label paidLabel;
    @FXML private Label balanceLabel;
    @FXML private TextField amountField;
    @FXML private TextField cashField;
    @FXML private TextField changeField;
    @FXML private TextField newBalanceField;
    @FXML private Label formMessageLabel;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;

    private final PaymentService paymentService = new PaymentService();

    /** All of the day's payments; the table shows the ones that match the search. */
    private List<Payment> dayPayments = new ArrayList<>();
    private List<PayableOrder> payableOrders = new ArrayList<>();
    private PayableOrder currentOrder;
    private BigDecimal[] dailyTotals = {BigDecimal.ZERO, BigDecimal.ZERO};

    /** True while the code (not the user) changes the order box. */
    private boolean updatingOrderCombo = false;

    /** Opens Payments with this order ready in Record Payment. */
    public static void openForOrder(int orderId) {
        pendingOrderId = orderId;
        AppShell.openTab(View.PAYMENTS);
    }

    /** Sets up filters and tables, then loads today's payments and unpaid orders. */
    @FXML
    private void initialize() {
        statusFilterComboBox.setItems(FXCollections.observableArrayList(STATUS_OPTIONS));
        statusFilterComboBox.setValue(STATUS_OPTIONS[0]);
        recordStatusColumn.setCellFactory(new StatusCellFactory());
        historyStatusColumn.setCellFactory(new StatusCellFactory());

        datePicker.setValue(LocalDate.now());
        clearForm();
        loadDay();
        int openOrderId = pendingOrderId;
        pendingOrderId = 0;
        loadPayableOrders(openOrderId, null);
    }

    // ---- Loading data ----

    /** Loads the day's payments and totals in the background. */
    private void loadDay() {
        LocalDate picked = datePicker.getValue();
        final LocalDate day = picked == null ? LocalDate.now() : picked;
        recordsPlaceholderLabel.setText("Loading payments...");
        BackgroundTask.run(() -> {
            DayData data = new DayData();
            data.payments = paymentService.getPaymentsForDay(day);
            data.totals = paymentService.getDailyTotals(day);
            return data;
        }, data -> {
            if (!day.equals(datePicker.getValue())) {
                return;     // the user picked another date meanwhile
            }
            dayPayments = data.payments;
            dailyTotals = data.totals;
            recordsPlaceholderLabel.setText("No payments recorded on this date.");
            showDay();
        }, error -> {
            dayPayments = new ArrayList<Payment>();
            dailyTotals = new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO};
            recordsPlaceholderLabel.setText("Payments could not be loaded.");
            showDay();
            Dialogs.error("Load payments", error);
        });
    }

    /** Shows the day's totals and payments. */
    private void showDay() {
        cashCollectedLabel.setText(MoneyFormat.php(dailyTotals[0]));
        pendingPaymentsLabel.setText(MoneyFormat.php(dailyTotals[1]));
        applyFilter();
    }

    /** Loads orders that still owe money, then opens the given order (if any). */
    private void loadPayableOrders(final int thenOpenOrderId, final String thenMessage) {
        BackgroundTask.run(() -> paymentService.getPayableOrders(), orders -> {
            payableOrders = orders;
            updatingOrderCombo = true;
            orderComboBox.setItems(FXCollections.observableArrayList(payableOrders));
            updatingOrderCombo = false;
            if (thenOpenOrderId > 0) {
                selectOrder(thenOpenOrderId, thenMessage);
            }
        }, error -> {
            payableOrders = new ArrayList<PayableOrder>();
            Dialogs.error("Load orders", error);
        });
    }

    /** Loads an order and its payment history, then shows the message (if any). */
    private void selectOrder(final int orderId, final String thenMessage) {
        customerLabel.setText("Loading...");
        BackgroundTask.run(() -> {
            OrderData data = new OrderData();
            data.order = paymentService.getOrder(orderId);
            data.history = paymentService.getPaymentHistory(orderId);
            return data;
        }, data -> {
            currentOrder = data.order;
            historyTable.setItems(FXCollections.observableArrayList(data.history));
            historyPlaceholderLabel.setText("No payments yet for this order.");
            showOrder(currentOrder);
            if (thenMessage != null) {
                Dialogs.showSuccess(formMessageLabel, thenMessage);
            }
        }, error -> {
            clearForm();
            Dialogs.showFailure(formMessageLabel, "Load order", error);
        });
    }

    // ---- Payment Records ----

    /** Search text or status filter changed. */
    @FXML
    private void handleSearch() {
        applyFilter();
    }

    /** Shows only the payments that match the search and filter. */
    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String status = statusFilterComboBox.getValue();

        List<Payment> shown = new ArrayList<>();
        for (Payment payment : dayPayments) {
            boolean statusMatches = status == null || status.equals(STATUS_OPTIONS[0])
                    || status.equals(payment.getStatusText());
            boolean textMatches = search.isEmpty()
                    || payment.getPaymentCode().toLowerCase().contains(search)
                    || payment.getOrderNumber().toLowerCase().contains(search)
                    || payment.getCustomerName().toLowerCase().contains(search);
            if (statusMatches && textMatches) {
                shown.add(payment);
            }
        }
        recordsTable.setItems(FXCollections.observableArrayList(shown));
    }

    /** A payment row was clicked: open its order in Record Payment. */
    @FXML
    private void handleRecordSelected() {
        Payment selected = recordsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (currentOrder != null && currentOrder.getId() == selected.getOrderId()) {
            return; // already shown
        }
        selectOrder(selected.getOrderId(), null);
    }

    /** Another date was picked. */
    @FXML
    private void handleDateChanged() {
        loadDay();
    }

    // ---- Record Payment form ----

    /** An order was picked in the Order No. box. */
    @FXML
    private void handleOrderChosen() {
        if (updatingOrderCombo) {
            return;
        }
        PayableOrder chosen = orderComboBox.getValue();
        if (chosen != null) {
            selectOrder(chosen.getId(), null);
        }
    }

    /** Updates the change and new balance while typing. */
    @FXML
    private void handleAmountTyped() {
        Dialogs.clearMessage(formMessageLabel);
        updateChangeAndBalance();
    }

    /** Confirm Payment button: checks the amounts, asks, then saves. */
    @FXML
    private void handleConfirmPayment() {
        if (currentOrder == null) {
            Dialogs.showError(formMessageLabel, "Select an order first.");
            return;
        }

        // Check the inputs first so mistakes show before the confirm box.
        BigDecimal amount;
        BigDecimal change;
        try {
            amount = PaymentService.parseMoney(amountField.getText(), "payment amount");
            BigDecimal cash = PaymentService.parseMoney(cashField.getText(), "cash received");
            if (amount.compareTo(currentOrder.getBalance()) > 0) {
                throw new ServiceException("Payment amount cannot be more than the remaining balance of "
                        + MoneyFormat.php(currentOrder.getBalance()) + ".");
            }
            if (cash.compareTo(amount) < 0) {
                throw new ServiceException("Cash received is less than the payment amount.");
            }
            change = cash.subtract(amount);
        } catch (ServiceException e) {
            Dialogs.showError(formMessageLabel, e.getMessage());
            return;
        }

        String question = "Record a cash payment of " + MoneyFormat.php(amount)
                + " for " + currentOrder.getOrderNumber() + " (" + currentOrder.getCustomerName() + ")?";
        if (change.signum() > 0) {
            question += "\n\nChange to give: " + MoneyFormat.php(change);
        }
        if (!Dialogs.confirm("Confirm payment", question)) {
            return;
        }

        final int orderId = currentOrder.getId();
        final String amountText = amountField.getText();
        final String cashText = cashField.getText();
        String message = "Payment of " + MoneyFormat.php(amount) + " recorded for " + currentOrder.getOrderNumber() + ".";
        if (change.signum() > 0) {
            message += " Give " + MoneyFormat.php(change) + " change.";
        }
        final String successMessage = message;

        // Lock the form so the payment isn't sent twice.
        setFormBusy(true);
        BackgroundTask.run(() -> paymentService.recordPayment(orderId, amountText, cashText), updated -> {
            setFormBusy(false);
            AppShell.setStatus(successMessage);
            loadDay();
            loadPayableOrders(orderId, successMessage);
        }, error -> {
            setFormBusy(false);
            confirmButton.setDisable(currentOrder == null || !currentOrder.canTakePayment());
            Dialogs.showFailure(formMessageLabel, "Record payment", error);
        });
    }

    /** Disables the form while saving. */
    private void setFormBusy(boolean busy) {
        confirmButton.setDisable(busy);
        cancelButton.setDisable(busy);
        amountField.setDisable(busy);
        cashField.setDisable(busy);
        orderComboBox.setDisable(busy);
        recordsTable.setDisable(busy);
        confirmButton.setText(busy ? "Saving..." : "Confirm Payment");
    }

    /** Cancel button: clears the form. */
    @FXML
    private void handleCancel() {
        recordsTable.getSelectionModel().clearSelection();
        clearForm();
    }

    /** Fills the Record Payment card with the order's details. */
    private void showOrder(PayableOrder order) {
        Dialogs.clearMessage(formMessageLabel);
        selectInCombo(order.getId());

        historyTitleLabel.setText("Payment History (Selected Order: " + order.getOrderNumber() + ")");
        customerLabel.setText(order.getCustomerName());
        serviceLabel.setText(order.getServices().isEmpty() ? "—" : order.getServices());
        quantityLabel.setText(order.getQuantityText().isEmpty() ? "—" : order.getQuantityText());
        totalLabel.setText(MoneyFormat.php(order.getTotalAmount()));
        paidLabel.setText(MoneyFormat.php(order.getAmountPaid()));
        balanceLabel.setText(MoneyFormat.php(order.getBalance()));

        boolean canPay = order.canTakePayment();
        amountField.setDisable(!canPay);
        cashField.setDisable(!canPay);
        confirmButton.setDisable(!canPay);
        cashField.clear();
        if (canPay) {
            amountField.setText(order.getBalance().toPlainString());
            cashField.requestFocus();
        } else {
            amountField.clear();
            if (order.getStatus() == OrderStatus.CANCELLED) {
                Dialogs.showError(formMessageLabel, "This order was cancelled. Payments cannot be recorded.");
            } else {
                Dialogs.showSuccess(formMessageLabel, "This order is fully paid.");
            }
        }
        updateChangeAndBalance();
    }

    /** Clears the Record Payment card and history table. */
    private void clearForm() {
        currentOrder = null;
        selectInCombo(0);
        Dialogs.clearMessage(formMessageLabel);

        historyTitleLabel.setText("Payment History (Selected Order: —)");
        historyTable.setItems(FXCollections.<Payment>observableArrayList());
        historyPlaceholderLabel.setText("Select an order to see its payments.");

        customerLabel.setText("—");
        serviceLabel.setText("—");
        quantityLabel.setText("—");
        totalLabel.setText("—");
        paidLabel.setText("—");
        balanceLabel.setText("—");

        amountField.clear();
        cashField.clear();
        changeField.clear();
        newBalanceField.clear();
        amountField.setDisable(true);
        cashField.setDisable(true);
        confirmButton.setDisable(true);
    }

    /** Updates Change and New Balance as the user types. */
    private void updateChangeAndBalance() {
        if (currentOrder == null || !currentOrder.canTakePayment()) {
            changeField.clear();
            newBalanceField.setText(currentOrder == null ? "" : MoneyFormat.php(currentOrder.getBalance()));
            return;
        }

        BigDecimal change = PaymentService.computeChange(amountField.getText(), cashField.getText());
        if (change == null) {
            changeField.clear();
        } else {
            changeField.setText(MoneyFormat.php(change));
        }

        try {
            BigDecimal amount = PaymentService.parseMoney(amountField.getText(), "payment amount");
            BigDecimal newBalance = currentOrder.getBalance().subtract(amount);
            if (newBalance.signum() < 0) {
                newBalanceField.setText("Over the balance");
            } else {
                newBalanceField.setText(MoneyFormat.php(newBalance));
            }
        } catch (ServiceException e) {
            newBalanceField.clear();
        }
    }

    /** Picks the order in the box without firing handleOrderChosen. */
    private void selectInCombo(int orderId) {
        PayableOrder match = null;
        for (PayableOrder order : payableOrders) {
            if (order.getId() == orderId) {
                match = order;
                break;
            }
        }
        updatingOrderCombo = true;
        try {
            orderComboBox.setValue(match);
        } finally {
            updatingOrderCombo = false;
        }
    }

    // ---- Daily summary ----

    /** View Daily Summary button: shows the day's payment figures in a pop-up. */
    @FXML
    private void handleViewDailySummary() {
        LocalDate day = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();

        int count = dayPayments.size();
        BigDecimal largest = BigDecimal.ZERO;
        List<Integer> ordersPaid = new ArrayList<>();
        int fullyPaid = 0;
        for (Payment payment : dayPayments) {
            if (payment.getAmount().compareTo(largest) > 0) {
                largest = payment.getAmount();
            }
            if (!ordersPaid.contains(payment.getOrderId())) {
                ordersPaid.add(payment.getOrderId());
                if (payment.getOrderPaymentStatus() == PaymentStatus.PAID) {
                    fullyPaid++;
                }
            }
        }

        String summary = "Date: " + day.format(LONG_DATE) + "\n\n"
                + "   Payments recorded:      " + count + "\n"
                + "   Cash collected:           " + MoneyFormat.php(dailyTotals[0]) + "\n"
                + "   Largest payment:          " + MoneyFormat.php(largest) + "\n"
                + "   Orders paid:                " + ordersPaid.size()
                + " (" + fullyPaid + " now fully paid)\n"
                + "   Pending (unpaid):         " + MoneyFormat.php(dailyTotals[1]) + "\n\n"
                + "Pending is the unpaid balance of orders received on this date.";
        Dialogs.info("Daily Payment Summary", summary);
    }

    // ---- Status cell ----

    private static class StatusCell extends TableCell<Payment, String> {
        /** Paid = green, Partial = amber, Unpaid = red. */
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("pay-paid", "pay-partial", "pay-unpaid");
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(item);
            if (item.equals(PaymentStatus.PAID.getDisplayName())) {
                getStyleClass().add("pay-paid");
            } else if (item.equals(PaymentStatus.PARTIALLY_PAID.getDisplayName())) {
                getStyleClass().add("pay-partial");
            } else {
                getStyleClass().add("pay-unpaid");
            }
        }
    }

    private static class StatusCellFactory
            implements Callback<TableColumn<Payment, String>, TableCell<Payment, String>> {
        /** Makes a new cell. */
        @Override
        public TableCell<Payment, String> call(TableColumn<Payment, String> column) {
            return new StatusCell();
        }
    }

    /** What loads for a day: its payments and totals. */
    private static class DayData {
        List<Payment> payments;
        BigDecimal[] totals;
    }

    /** What loads for an order: the order and its payments. */
    private static class OrderData {
        PayableOrder order;
        List<Payment> history;
    }
}
