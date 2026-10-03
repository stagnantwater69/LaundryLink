package com.laundrylink.util;

import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * Runs database work off the JavaFX thread, then calls back on the JavaFX thread.
 */
public final class BackgroundTask {

    public interface Work<T> {
        T call() throws Exception;
    }

    public interface VoidWork {
        void run() throws Exception;
    }

    private BackgroundTask() {
    }

    public static <T> void run(Work<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<T>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));

        Thread thread = new Thread(task, "laundrylink-background");
        thread.setDaemon(true);
        thread.start();
    }

    public static void runVoid(VoidWork work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }
}
