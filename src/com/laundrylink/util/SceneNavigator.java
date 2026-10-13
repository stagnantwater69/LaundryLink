package com.laundrylink.util;

import java.io.IOException;
import java.net.URL;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Opens screens in the main window and keeps them scaled to its size.
 */
public final class SceneNavigator {

    private static Stage stage;
    private static Scene scene;
    private static double scale = 1.0;

    private SceneNavigator() {
    }

    /** Creates the window's scene and watches for resizes. */
    public static void init(Stage primaryStage, double width, double height) {
        stage = primaryStage;
        scene = new Scene(new StackPane(), width, height);
        stage.setScene(scene);

        ResizeListener resizeListener = new ResizeListener();
        scene.widthProperty().addListener(resizeListener);
        scene.heightProperty().addListener(resizeListener);
        scale = UiScale.forWindow(width, height);
    }

    /** The main window. */
    public static Stage getStage() {
        return stage;
    }

    /** Shows a screen in the whole window and returns its controller. */
    public static Object showScreen(View view) {
        FXMLLoader loader = loadFxml(view);
        Parent root = loader.getRoot();
        scene.setRoot(root);
        UiScale.applyFont(root, scale);
        return loader.getController();
    }

    /** Loads a screen (like a tab's content) without showing it. */
    public static Parent loadView(View view) {
        return loadFxml(view).getRoot();
    }

    /** Loads a view's FXML and scales it to the window. */
    private static FXMLLoader loadFxml(View view) {
        URL location = SceneNavigator.class.getResource(view.getPath());
        if (location == null) {
            throw new IllegalStateException("Screen not found: " + view.getPath());
        }
        FXMLLoader loader = new FXMLLoader(location);
        try {
            Parent root = loader.load();
            UiScale.applySizes(root, scale);
            return loader;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load screen " + view.getPath(), e);
        }
    }

    /** Re-scales the screen when the window size changes enough. */
    private static void rescale() {
        double newScale = UiScale.forWindow(scene.getWidth(), scene.getHeight());
        if (newScale == scale) {
            return;
        }
        scale = newScale;
        UiScale.applyFont(scene.getRoot(), scale);
        UiScale.applySizes(scene.getRoot(), scale);
    }

    /** Calls rescale() when the window is resized. */
    private static class ResizeListener implements ChangeListener<Number> {
        /** Runs on every resize. */
        @Override
        public void changed(ObservableValue<? extends Number> value, Number oldSize, Number newSize) {
            rescale();
        }
    }
}
