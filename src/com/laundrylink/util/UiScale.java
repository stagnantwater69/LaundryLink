package com.laundrylink.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Makes the UI responsive: everything is designed at 1200x760 and scaled up
 * (or slightly down) to fit the window.
 *
 * CSS sizes are in em, so they follow the root font size. Fixed sizes written
 * in FXML (image sizes, pref/min/max widths, HBox/VBox spacing) are scaled
 * here, using the original FXML value as the base.
 */
public final class UiScale {

    public static final double DESIGN_WIDTH = 1200;
    public static final double DESIGN_HEIGHT = 760;

    private static final double BASE_FONT_SIZE = 14;
    private static final double MIN_SCALE = 0.85;
    private static final double MAX_SCALE = 1.8;
    private static final String BASE_KEY = "laundrylink.base.";

    private UiScale() {
    }

    /** Scale factor for a window size, rounded so small resizes don't re-layout constantly. */
    public static double forWindow(double width, double height) {
        double scale = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        return Math.round(scale * 20) / 20.0;
    }

    /** Root font size; every em size in the stylesheet follows it. */
    public static void applyFont(Parent sceneRoot, double scale) {
        sceneRoot.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-size: %.2fpx;", BASE_FONT_SIZE * scale));
    }

    /** Scales FXML fixed sizes in this subtree (call on every newly loaded view and after resizes). */
    public static void applySizes(Node node, double scale) {
        if (node instanceof ImageView) {
            ImageView image = (ImageView) node;
            image.setFitWidth(base(image, "fitWidth", image.getFitWidth()) * scale);
            image.setFitHeight(base(image, "fitHeight", image.getFitHeight()) * scale);
        }
        if (node instanceof Region) {
            Region region = (Region) node;
            region.setPrefWidth(scaled(base(region, "prefWidth", region.getPrefWidth()), scale));
            region.setMinWidth(scaled(base(region, "minWidth", region.getMinWidth()), scale));
            region.setMaxWidth(scaled(base(region, "maxWidth", region.getMaxWidth()), scale));
        }
        if (node instanceof HBox) {
            HBox box = (HBox) node;
            box.setSpacing(base(box, "spacing", box.getSpacing()) * scale);
        } else if (node instanceof VBox) {
            VBox box = (VBox) node;
            box.setSpacing(base(box, "spacing", box.getSpacing()) * scale);
        }

        // Walk our own layout, not the internals of controls (skins).
        if (node instanceof Labeled && ((Labeled) node).getGraphic() != null) {
            applySizes(((Labeled) node).getGraphic(), scale);
        }
        if (node instanceof ScrollPane && ((ScrollPane) node).getContent() != null) {
            applySizes(((ScrollPane) node).getContent(), scale);
        }
        if (node instanceof Parent && !(node instanceof Control)) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                applySizes(child, scale);
            }
        }
    }

    /** Only real fixed sizes are scaled; computed/unbounded markers are left as they are. */
    private static double scaled(double baseValue, double scale) {
        boolean fixedSize = baseValue > 0 && baseValue != Double.MAX_VALUE;
        return fixedSize ? baseValue * scale : baseValue;
    }

    /** Remembers the FXML value the first time a node is seen. */
    private static double base(Node node, String name, double currentValue) {
        Object stored = node.getProperties().get(BASE_KEY + name);
        if (stored == null) {
            node.getProperties().put(BASE_KEY + name, currentValue);
            return currentValue;
        }
        return (Double) stored;
    }
}
