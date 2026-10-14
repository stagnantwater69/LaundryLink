package com.laundrylink.util;

import com.laundrylink.config.DatabaseConfig;
import com.laundrylink.service.ServiceException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Pop-up messages and the small error/success text under forms, all in one style.
 */
public final class Dialogs {

    private static final String STYLESHEET = "/com/laundrylink/css/laundrylink.css";
    private static final String IMAGES = "/com/laundrylink/images/";
    private static final double CONTENT_WIDTH = 440;

    // How a pop-up looks (header color, icon and buttons).
    private static final int KIND_SUCCESS = 1;
    private static final int KIND_SUMMARY = 2;
    private static final int KIND_ERROR = 3;
    private static final int KIND_QUESTION = 4;
    private static final int KIND_DANGER = 5;

    private Dialogs() {
    }

    /** Info pop-up (a success message, or a summary if the title has "Summary"). */
    public static void info(String title, String message) {
        int kind = title.contains("Summary") ? KIND_SUMMARY : KIND_SUCCESS;
        createAlert(Alert.AlertType.INFORMATION, kind, title, message).showAndWait();
    }

    /** Shows an error pop-up. */
    public static void error(String title, String message) {
        createAlert(Alert.AlertType.ERROR, KIND_ERROR, title, message).showAndWait();
    }

    /** Error pop-up with a readable message for the exception. */
    public static void error(String title, Throwable error) {
        error(title, friendlyMessage(error));
    }

    /** Yes/no question; true if the user clicks the action button. */
    public static boolean confirm(String title, String message) {
        int kind = isRisky(title) ? KIND_DANGER : KIND_QUESTION;
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, kind, title, message);
        alert.showAndWait();
        ButtonType answer = alert.getResult();
        return answer != null && answer.getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }

    /** Question with a dropdown; returns the picked option, or null if the user goes back. */
    public static <T> T choose(String title, String message, List<T> options, T startWith) {
        Alert alert = createAlert(Alert.AlertType.CONFIRMATION, KIND_QUESTION, title, message);
        ComboBox<T> choices = new ComboBox<>(FXCollections.observableArrayList(options));
        choices.setMaxWidth(Double.MAX_VALUE);
        if (startWith != null && options.contains(startWith)) {
            choices.setValue(startWith);
        } else if (!options.isEmpty()) {
            choices.getSelectionModel().selectFirst();
        }
        ((VBox) alert.getDialogPane().getContent()).getChildren().add(choices);
        alert.showAndWait();
        ButtonType answer = alert.getResult();
        boolean ok = answer != null && answer.getButtonData() == ButtonBar.ButtonData.OK_DONE;
        return ok ? choices.getValue() : null;
    }

    /** Rule mistakes show under the form; anything unexpected (like the database being down) shows in a pop-up. */
    public static void showFailure(Label inlineLabel, String title, Throwable error) {
        if (error instanceof ServiceException) {
            showError(inlineLabel, error.getMessage());
        } else {
            clearMessage(inlineLabel);
            error(title, error);
        }
    }

    /** Shows red error text under a form. */
    public static void showError(Label label, String message) {
        setMessage(label, message, "form-error");
    }

    /** Shows green success text under a form. */
    public static void showSuccess(Label label, String message) {
        setMessage(label, message, "form-success");
    }

    /** Hides the text under a form. */
    public static void clearMessage(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    /** Turns an exception into a message shop users can understand. */
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

    /** Shows the text in the label with the given color style. */
    private static void setMessage(Label label, String message, String styleClass) {
        label.getStyleClass().removeAll("form-error", "form-success");
        label.getStyleClass().add(styleClass);
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    // ---- Building the pop-up ----

    /** Builds a pop-up with a colored header, icon, message and named buttons. */
    private static Alert createAlert(Alert.AlertType type, int kind, String title, String message) {
        Alert alert = new Alert(type);
        if (SceneNavigator.getStage() != null) {
            alert.initOwner(SceneNavigator.getStage());
        }
        alert.setTitle("LaundryLink - " + title);
        alert.setHeaderText(null);
        alert.setGraphic(null);

        DialogPane pane = alert.getDialogPane();
        URL css = Dialogs.class.getResource(STYLESHEET);
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        pane.getStyleClass().addAll("ll-dialog", kindStyle(kind));
        pane.setHeader(buildHeader(kind, title));
        VBox content = kind == KIND_ERROR ? buildErrorContent(message) : buildContent(message);
        if ((kind == KIND_QUESTION || kind == KIND_DANGER) && !content.getChildren().isEmpty()) {
            content.getChildren().get(0).getStyleClass().add("ll-dialog-lead");   // make the question stand out
        }
        pane.setContent(content);
        pane.setMinHeight(Region.USE_PREF_SIZE);

        setUpButtons(alert, kind, title);
        return alert;
    }

    /** The colored strip at the top: icon plus title. */
    private static HBox buildHeader(int kind, String title) {
        ImageView icon = new ImageView();
        URL image = Dialogs.class.getResource(IMAGES + iconFile(kind));
        if (image != null) {
            icon.setImage(new Image(image.toExternalForm()));
        }
        icon.setFitWidth(34);
        icon.setFitHeight(34);
        icon.setPreserveRatio(true);
        icon.setSmooth(false);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("ll-dialog-title");
        titleLabel.setWrapText(true);

        HBox header = new HBox(12, icon, titleLabel);
        header.getStyleClass().add("ll-dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /** Turns the message into labels: plain lines become paragraphs, and lines like "   Orders received:   8" become a two-column table. */
    private static VBox buildContent(String message) {
        VBox content = new VBox(8);
        content.getStyleClass().add("ll-dialog-body");
        content.setPrefWidth(CONTENT_WIDTH);

        String[] lines = message.split("\n");
        GridPane table = null;
        int tableRow = 0;
        boolean afterTable = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isTableRow(line)) {
                if (table == null) {
                    table = newTable();
                    tableRow = 0;
                    content.getChildren().add(table);
                }
                int colon = line.indexOf(':');
                Label name = new Label(line.substring(0, colon).trim());
                name.getStyleClass().add("ll-dialog-row-name");
                Label value = new Label(line.substring(colon + 1).trim());
                value.getStyleClass().add("ll-dialog-row-value");
                table.add(name, 0, tableRow);
                table.add(value, 1, tableRow);
                tableRow++;
                afterTable = true;
                continue;
            }
            table = null;
            if (line.trim().isEmpty()) {
                continue;
            }
            boolean nextIsRow = i + 1 < lines.length && isTableRow(lines[i + 1]);
            Label text = paragraph(line.trim());
            if (nextIsRow) {
                text.getStyleClass().add("ll-dialog-heading");
            } else if (afterTable) {
                text.getStyleClass().add("ll-dialog-note");
            }
            content.getChildren().add(text);
        }
        return content;
    }

    /** Error text, with the technical "Details:" part smaller and grey. */
    private static VBox buildErrorContent(String message) {
        int detailsAt = message.indexOf("Details:");
        if (detailsAt < 0) {
            return buildContent(message);
        }
        VBox content = buildContent(message.substring(0, detailsAt).trim());
        Label details = paragraph(message.substring(detailsAt).trim());
        details.getStyleClass().add("ll-dialog-details");
        content.getChildren().add(details);
        return content;
    }

    /** Names the buttons after the action (e.g. "Release Order" / "Go Back") and colors the main one. */
    private static void setUpButtons(Alert alert, int kind, String title) {
        ButtonType main;
        if (kind == KIND_QUESTION || kind == KIND_DANGER) {
            main = new ButtonType(titleCase(title), ButtonBar.ButtonData.OK_DONE);
            ButtonType back = new ButtonType("Go Back", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(main, back);
        } else {
            main = new ButtonType(kind == KIND_SUMMARY ? "Close" : "OK", ButtonBar.ButtonData.OK_DONE);
            alert.getButtonTypes().setAll(main);
        }
        Button mainButton = (Button) alert.getDialogPane().lookupButton(main);
        mainButton.getStyleClass().add(kind == KIND_DANGER ? "danger" : "primary");
        mainButton.setDefaultButton(true);
    }

    // ---- Small helpers ----

    /** True for questions that remove, cancel or block something (red button). */
    private static boolean isRisky(String title) {
        String lower = title.toLowerCase();
        return lower.startsWith("delete") || lower.startsWith("cancel") || lower.startsWith("deactivate")
                || lower.startsWith("log out");
    }

    /** True for summary lines like "   Orders received:   8". */
    private static boolean isTableRow(String line) {
        return line.startsWith("   ") && line.indexOf(':') > 0 && !line.trim().endsWith(":");
    }

    /** A wrapping label that never gets cut off. */
    private static Label paragraph(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(CONTENT_WIDTH);
        label.getStyleClass().add("ll-dialog-text");
        return label;
    }

    /** An empty two-column table (names left, values right). */
    private static GridPane newTable() {
        GridPane table = new GridPane();
        table.getStyleClass().add("ll-dialog-table");
        table.setHgap(18);
        table.setVgap(3);
        ColumnConstraints names = new ColumnConstraints();
        names.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().addAll(names, values);
        return table;
    }

    /** "release order" becomes "Release Order". */
    private static String titleCase(String text) {
        StringBuilder result = new StringBuilder();
        boolean startOfWord = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = c == ' ';
        }
        return result.toString();
    }

    /** Style class that colors the pop-up for its kind. */
    private static String kindStyle(int kind) {
        switch (kind) {
            case KIND_SUCCESS:
                return "ll-dialog-success";
            case KIND_SUMMARY:
                return "ll-dialog-summary";
            case KIND_ERROR:
                return "ll-dialog-error";
            case KIND_DANGER:
                return "ll-dialog-danger";
            default:
                return "ll-dialog-question";
        }
    }

    /** Header icon for each kind. */
    private static String iconFile(int kind) {
        switch (kind) {
            case KIND_SUCCESS:
                return "icon-success.png";
            case KIND_SUMMARY:
                return "icon-document.png";
            case KIND_ERROR:
            case KIND_DANGER:
                return "icon-alert.png";
            default:
                return "icon-question.png";
        }
    }
}
