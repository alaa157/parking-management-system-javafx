package com.parking.services;

import com.parking.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Local cron-like scheduler with delivery adapters ready for email/SMS providers. */
public final class ScheduledReportService implements AutoCloseable {
    public enum Delivery { LOCAL, EMAIL, SMS }
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "parkingos-report-scheduler"); t.setDaemon(true); return t;
    });
    private ScheduledFuture<?> future;
    private ScheduleHandle activeHandle;
    private boolean closed;

    public synchronized void scheduleEvery(Duration interval, Supplier<Path> generator,
                                            Delivery delivery, java.util.function.Consumer<String> status) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Report interval must be positive");
        }
        if (generator == null) throw new IllegalArgumentException("Report generator is required");
        if (delivery == null) throw new IllegalArgumentException("Report delivery is required");
        final long delay;
        try {
            delay = interval.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Report interval is too large", overflow);
        }
        if (delay <= 0) throw new IllegalArgumentException("Report interval is too small");
        ensureOpen();

        cancelLocked();
        ScheduleHandle handle = new ScheduleHandle();
        try {
            future = executor.scheduleAtFixedRate(() -> runReport(handle, generator, delivery, status),
                    delay, delay, TimeUnit.NANOSECONDS);
            activeHandle = handle;
        } catch (RejectedExecutionException rejected) {
            handle.cancelled = true;
            throw new IllegalStateException("Report scheduler is closed", rejected);
        }
    }

    private void runReport(ScheduleHandle handle, Supplier<Path> generator, Delivery delivery,
                           java.util.function.Consumer<String> status) {
        if (!isCurrent(handle)) return;
        try {
            Path report = generator.get();
            if (report == null) throw new IllegalStateException("Report generator returned no file");
            deliver(report, delivery);
            notifyIfCurrent(handle, status, "Scheduled " + delivery + " report generated: " + report);
        } catch (Throwable failure) {
            notifyIfCurrent(handle, status, "Scheduled report failed: " + failureMessage(failure));
        }
    }

    private String failureMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void notifyIfCurrent(ScheduleHandle handle, java.util.function.Consumer<String> status, String message) {
        if (status == null) return;
        synchronized (this) {
            if (closed || activeHandle != handle || handle.cancelled) return;
            try {
                status.accept(message);
            } catch (Throwable ignored) {
                // A disposed UI callback must not terminate the recurring task.
            }
        }
    }

    private synchronized boolean isCurrent(ScheduleHandle handle) {
        return !closed && activeHandle == handle && !handle.cancelled;
    }

    public synchronized void cancel() {
        cancelLocked();
    }

    private void cancelLocked() {
        if (activeHandle != null) activeHandle.cancelled = true;
        if (future != null) future.cancel(false);
        activeHandle = null;
        future = null;
    }

    public synchronized boolean isScheduled() {
        return !closed && future != null && !future.isCancelled() && !future.isDone();
    }

    public synchronized boolean isClosed() { return closed; }

    public synchronized boolean isTerminated() { return executor.isTerminated(); }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Report scheduler is closed");
    }

    private void deliver(Path report, Delivery delivery) throws Exception {
        if (delivery == Delivery.LOCAL) return;
        // Integration seam: replace this audit file with SMTP/Twilio adapters in deployment.
        Path audit = AppConfig.reportsDirectory().resolve("delivery-audit.log").normalize();
        Files.createDirectories(audit.getParent());
        Files.writeString(audit, LocalDateTime.now() + " " + delivery + " " + report + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        cancelLocked();
        executor.shutdownNow();
    }

    private static final class ScheduleHandle {
        private volatile boolean cancelled;
    }
}
