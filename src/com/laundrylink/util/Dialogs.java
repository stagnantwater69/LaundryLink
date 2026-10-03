package com.laundrylink.util;

import com.laundrylink.config.DatabaseConfig;
import com.laundrylink.service.ServiceException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

/**
 * Consistent success/error/confirmation dialogs and inline form messages.
 */
public final class Dialogs {

    private static final String STYLESHEET = "/com/laundrylink/css/laundrylink.css";

    private Dialogs() {
    }

    public static void info(String title, String message) {
        createAlert(Alert.AlertType.INFORMATION, title, message).showAndWait();
    }

    public static void error(String title, String message) {
        createAlert(Alert.AlertType.ERROR, title, message).showAndWait();
    }

    public static void error(String title, Throwable error) {
        error(title, friendlyMessage(error));
    }

    public static boolean confirm(String title, String message) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, title, message);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    /**
     * Shows a validation/business-rule failure in the inline label, and anything
     * unexpected (database down, bugs) in an error dialog.
     */
    public static void showFailure(Label inlineLabel, String title, Throwable error) {
        if (error instanceof ServiceException) {
            showError(inlineLabel, error.getMessage());
        } else {
            clearMessage(inlineLabel);
            error(title, error);
        }
    }

    public static void showError(Label label, String message) {
        setMessage(label, message, "form-error");
    }

    public static void showSuccess(Label label, String message) {
        setMessage(label, message, "form-success");
    }

    public static void clearMessage(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    public static String friendlyMessage(Throwable error) {
        if (error instanceof ServiceException) {
            return error.getMessage();
        }
        if (error instanceof SQLException) {
            return "Could not reach the LaundryLink database.\n\n"
                    + "Check that MySQL is running and that " + DatabaseConfig.CONFIG_FILE
                    + " has the correct settings.\n\nDetails: " + error.getMessage();
        }
        error.printStackTrace();
        return "Something went wrong. Please try again.";
    }

    private static void setMessage(Label label, String message, String styleClass) {
        label.getStyleClass().removeAll("form-error", "form-success");
        label.getStyleClass().add(styleClass);
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private static Alert createAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        if (SceneNavigator.getStage() != null) {
            alert.initOwner(SceneNavigator.getStage());
        }
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        URL css = Dialogs.class.getResource(STYLESHEET);
        if (css != null) {
            alert.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        alert.getDialogPane().getStyleClass().add("ll-dialog");
        return alert;
    }
}
