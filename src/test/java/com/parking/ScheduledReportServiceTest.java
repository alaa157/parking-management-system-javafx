package com.parking;

import com.parking.services.ScheduledReportService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledReportServiceTest {

    @Test
    void rejectsInvalidIntervalsWithoutReplacingExistingSchedule() {
        try (ScheduledReportService service = new ScheduledReportService()) {
            service.scheduleEvery(Duration.ofMillis(20), () -> Path.of("report.pdf"),
                    ScheduledReportService.Delivery.LOCAL, null);
            assertThrows(IllegalArgumentException.class, () -> service.scheduleEvery(Duration.ZERO,
                    () -> Path.of("other.pdf"), ScheduledReportService.Delivery.LOCAL, null));
            assertThrows(IllegalArgumentException.class, () -> service.scheduleEvery(Duration.ofMillis(-1),
                    () -> Path.of("other.pdf"), ScheduledReportService.Delivery.LOCAL, null));
            assertTrue(service.isScheduled());
        }
    }

    @Test
    void replacementCancelsThePreviousSchedule() throws Exception {
        try (ScheduledReportService service = new ScheduledReportService()) {
            AtomicInteger first = new AtomicInteger();
            AtomicInteger second = new AtomicInteger();
            CountDownLatch secondRun = new CountDownLatch(1);
            service.scheduleEvery(Duration.ofMillis(10), () -> {
                first.incrementAndGet();
                return Path.of("first.pdf");
            }, ScheduledReportService.Delivery.LOCAL, null);
            awaitAtLeast(first, 1);

            service.scheduleEvery(Duration.ofMillis(10), () -> {
                second.incrementAndGet();
                secondRun.countDown();
                return Path.of("second.pdf");
            }, ScheduledReportService.Delivery.LOCAL, null);
            assertTrue(secondRun.await(1, TimeUnit.SECONDS));
            assertTrue(second.get() > 0);
            assertTrue(service.isScheduled());
        }
    }

    @Test
    void cancellationStopsCallbacksAndCloseIsIdempotent() throws Exception {
        ScheduledReportService service = new ScheduledReportService();
        AtomicInteger runs = new AtomicInteger();
        service.scheduleEvery(Duration.ofMillis(10), () -> {
            runs.incrementAndGet();
            return Path.of("report.pdf");
        }, ScheduledReportService.Delivery.LOCAL, null);
        awaitAtLeast(runs, 1);
        service.cancel();
        assertFalse(service.isScheduled());
        service.close();
        service.close();
        assertTrue(service.isClosed());
        awaitTermination(service);
        assertThrows(IllegalStateException.class, () -> service.scheduleEvery(Duration.ofMillis(10),
                () -> Path.of("closed.pdf"), ScheduledReportService.Delivery.LOCAL, null));
    }

    @Test
    void generatorFailureIsReportedWithoutLosingTheSchedule() throws Exception {
        try (ScheduledReportService service = new ScheduledReportService()) {
            CountDownLatch failed = new CountDownLatch(1);
            AtomicReference<String> message = new AtomicReference<>();
            service.scheduleEvery(Duration.ofMillis(10), () -> {
                throw new IllegalStateException("generator failed");
            }, ScheduledReportService.Delivery.LOCAL, status -> {
                message.set(status);
                failed.countDown();
            });
            assertTrue(failed.await(1, TimeUnit.SECONDS));
            assertTrue(message.get().startsWith("Scheduled report failed"));
            assertTrue(service.isScheduled());
        }
    }

    @Test
    void lifecycleMethodsAreSafeWhenCalledConcurrently() throws Exception {
        ScheduledReportService service = new ScheduledReportService();
        var callers = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var futures = IntStream.range(0, 40).mapToObj(index -> callers.submit(() -> {
                start.await();
                if (index % 3 == 0) service.cancel();
                else if (index % 3 == 1) {
                    try {
                        service.scheduleEvery(Duration.ofMillis(10), () -> Path.of("report.pdf"),
                                ScheduledReportService.Delivery.LOCAL, null);
                    } catch (IllegalStateException expectedAfterClose) {
                        // A concurrent close is an expected terminal result.
                    }
                } else service.isScheduled();
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get(2, TimeUnit.SECONDS);
            service.close();
            assertTrue(service.isClosed());
        } finally {
            service.close();
            callers.shutdownNow();
        }
    }

    private static void awaitAtLeast(AtomicInteger value, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (value.get() < expected && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertTrue(value.get() >= expected);
    }

    private static void awaitTermination(ScheduledReportService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!service.isTerminated() && System.nanoTime() < deadline) Thread.yield();
        assertTrue(service.isTerminated());
    }
}
