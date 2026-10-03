package com.laundrylink.util;

import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * Lets module screens talk to the main window without knowing its controller:
 * open another tab, or show a message in the bottom status bar.
 */
public final class AppShell {

    private static final ReadOnlyStringWrapper STATUS = new ReadOnlyStringWrapper("Ready.");
    private static Consumer<View> tabOpener = view -> { };

    private AppShell() {
    }

    /** Called by the main shell when it is shown. */
    public static void setTabOpener(Consumer<View> opener) {
        tabOpener = opener;
    }

    /** Switches the main window to the tab showing this view. */
    public static void openTab(View view) {
        tabOpener.accept(view);
    }

    public static void setStatus(String message) {
        STATUS.set(message);
    }

    public static ReadOnlyStringProperty statusProperty() {
        return STATUS.getReadOnlyProperty();
    }
}
