package com.laundrylink.util;

import java.io.IOException;
import java.net.URL;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Shared navigation: swaps full screens on the main window and loads views
 * that are placed inside other screens (e.g. the main shell's content area).
 * Also keeps the UI scaled to the window size (see UiScale).
 */
public final class SceneNavigator {

    private static Stage stage;
    private static Scene scene;
    private static double scale = 1.0;

    private SceneNavigator() {
    }

    public static void init(Stage primaryStage, double width, double height) {
        stage = primaryStage;
        scene = new Scene(new StackPane(), width, height);
        stage.setScene(scene);

        ChangeListener<Number> resizeListener = (obs, oldSize, newSize) -> rescale();
        scene.widthProperty().addListener(resizeListener);
        scene.heightProperty().addListener(resizeListener);
        scale = UiScale.forWindow(width, height);
    }

    public static Stage getStage() {
        return stage;
    }

    /** Replaces the whole window content and returns the screen's controller. */
    public static <T> T showScreen(View view) {
        LoadedView<T> loaded = load(view);
        scene.setRoot(loaded.getRoot());
        UiScale.applyFont(loaded.getRoot(), scale);
        return loaded.getController();
    }

    /** Loads a view (already scaled to the current window) without displaying it. */
    public static <T> LoadedView<T> load(View view) {
        URL location = SceneNavigator.class.getResource(view.getPath());
        if (location == null) {
            throw new IllegalStateException("Screen not found: " + view.getPath());
        }
        FXMLLoader loader = new FXMLLoader(location);
        try {
            Parent root = loader.load();
            UiScale.applySizes(root, scale);
            T controller = loader.getController();
            return new LoadedView<>(root, controller);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load screen " + view.getPath(), e);
        }
    }

    private static void rescale() {
        double newScale = UiScale.forWindow(scene.getWidth(), scene.getHeight());
        if (newScale == scale) {
            return;
        }
        scale = newScale;
        UiScale.applyFont(scene.getRoot(), scale);
        UiScale.applySizes(scene.getRoot(), scale);
    }

    public static final class LoadedView<T> {

        private final Parent root;
        private final T controller;

        private LoadedView(Parent root, T controller) {
            this.root = root;
            this.controller = controller;
        }

        public Parent getRoot() {
            return root;
        }

        public T getController() {
            return controller;
        }
    }
}
