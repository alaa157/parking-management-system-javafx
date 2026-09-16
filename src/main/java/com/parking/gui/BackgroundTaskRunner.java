package com.parking.gui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Small application-owned bridge for blocking work and JavaFX callbacks. */
public final class BackgroundTaskRunner implements AutoCloseable {
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "parkingos-background");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public <T> Future<?> submit(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        if (closed) throw new RejectedExecutionException("Background task runner is closed");
        return executor.submit(() -> {
            try {
                T result = work.call();
                onFx(() -> success.accept(result));
            } catch (Throwable error) {
                if (!(error instanceof InterruptedException)) {
                    onFx(() -> failure.accept(error));
                } else {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private void onFx(Runnable callback) {
        if (closed) return;
        if (Platform.isFxApplicationThread()) callback.run();
        else Platform.runLater(() -> { if (!closed) callback.run(); });
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        executor.shutdownNow();
    }

    public boolean isClosed() { return closed; }
}
