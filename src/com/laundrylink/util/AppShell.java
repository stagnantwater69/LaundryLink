package com.laundrylink.util;

import com.laundrylink.controller.MainShellController;
import javafx.scene.control.Label;

/**
 * Lets any screen switch tabs or show a status message in the main window.
 */
public final class AppShell {

    // The main window and its status label, saved when it opens.
    private static MainShellController shell;
    private static Label statusLabel;

    private AppShell() {
    }

    /** Called once by the main window so other screens can reach it. */
    public static void setShell(MainShellController mainShell, Label bottomStatusLabel) {
        shell = mainShell;
        statusLabel = bottomStatusLabel;
    }

    /** Switches to the tab that shows this view. */
    public static void openTab(View view) {
        if (shell != null) {
            shell.showTab(view);
        }
    }

    /** Updates the name at the top right after a profile edit. */
    public static void refreshUserBadge() {
        if (shell != null) {
            shell.showUserBadge();
        }
    }

    /** Shows a message in the bottom status bar. */
    public static void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }
}
