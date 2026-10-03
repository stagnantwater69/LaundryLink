package com.laundrylink;

import com.laundrylink.util.SceneNavigator;
import com.laundrylink.util.View;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * LaundryLink entry point. Opens the Welcome screen.
 */
public class LaundryLinkApp extends Application {

    private static final double WIDTH = 1200;
    private static final double HEIGHT = 720;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("LaundryLink - Laundry Shop Management");
        primaryStage.getIcons().add(new Image(
                LaundryLinkApp.class.getResourceAsStream("/com/laundrylink/images/logo-basket.png")));
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(680);

        SceneNavigator.init(primaryStage, WIDTH, HEIGHT);
        SceneNavigator.showScreen(View.WELCOME);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
