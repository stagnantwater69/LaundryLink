package com.laundrylink.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * Runs database work in the background so the window doesn't freeze, then
 * hands the result back on the screen thread (where it's safe to update the UI).
 */
public final class BackgroundTask {

    /** Work that returns a result. */
    public interface Work<T> {
        T call() throws Exception;
    }

    /** Work that returns nothing. */
    public interface VoidWork {
        void run() throws Exception;
    }

    // A few reusable worker threads.
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private int count;

        /** Background thread that won't keep the app running after the window closes. */
        @Override
        public synchronized Thread newThread(Runnable work) {
            count++;
            Thread thread = new Thread(work, "laundrylink-background-" + count);
            thread.setDaemon(true);
            return thread;
        }
    });

    private BackgroundTask() {
    }

    /** Runs the work in the background, then calls onSuccess or onFailure on the screen thread. */
    public static <T> void run(Work<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<T>() {
            /** Does the work in the background. */
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        WORKERS.execute(task);
    }

    /** Same as run() for work that returns nothing. */
    public static void runVoid(VoidWork work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }
}
