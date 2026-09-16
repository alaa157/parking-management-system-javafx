package com.parking.services;

import com.parking.enums.NotificationType;
import com.parking.enums.PaymentStatus;
import com.parking.enums.UserRole;
import com.parking.exceptions.AuthorizationException;
import com.parking.model.DutySession;
import com.parking.model.Payment;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Attendant duty and shift workflows on top of the notification backbone.
 * One attendant holds at most one {@code OPEN} session; every start/end
 * transition runs in one {@link PersistenceStore#inTransaction} and publishes
 * exactly one backbone notification for the attendant. The single-OPEN rule is
 * enforced twice: the duplicate check runs inside the start transaction and a
 * partial unique index {@code duty_sessions(attendant_id) WHERE status='OPEN'}
 * rejects concurrent winners at the database level. Admin summaries attribute
 * completed payment successes whose payment time falls inside an attendant's
 * open session windows, so ticket/payment success is connected to the on-duty
 * session without new payment-table columns.
 *
 * <p>Dedup rule for team summaries: a COMPLETED payment inside overlapping
 * on-duty windows of several attendants is attributed to exactly one
 * attendant — the one whose containing window has the earliest start
 * (tie-break by attendant id). Single-attendant summaries count each payment
 * once even if that attendant's own windows overlap. Team totals therefore
 * never inflate when shifts overlap.
 */
public class DutyService {
    private final PersistenceStore store;
    private final NotificationService notifications;

    public DutyService(PersistenceStore store, NotificationService notifications) {
        if (store == null) throw new IllegalArgumentException("store cannot be null");
        if (notifications == null) throw new IllegalArgumentException("notifications cannot be null");
        this.store = store;
        this.notifications = notifications;
    }

    public synchronized DutySession start(User actor, String shift, String zone, LocalDateTime now) {
        requireDutyActor(actor);
        if (shift == null || shift.isBlank()) throw new IllegalArgumentException("shift cannot be null or blank");
        if (zone == null || zone.isBlank()) throw new IllegalArgumentException("zone cannot be null or blank");
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        requireKnownUser(actor);
        String sessionId = "DUTY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String trimmedShift = shift.trim();
        String trimmedZone = zone.trim();
        try {
            return store.inTransaction(() -> {
                if (store.loadOpenDutySession(actor.getUserId()).isPresent()) {
                    throw new IllegalStateException("Attendant already has an open duty session: " + actor.getUserId());
                }
                store.saveDutySession(sessionId, actor.getUserId(), trimmedShift, trimmedZone,
                        now.toString(), null, "OPEN");
                notifications.publish(actor.getUserId(), NotificationType.ENTRY_EXIT,
                        "Duty started: " + trimmedShift + " shift, " + trimmedZone + " at " + now + ".");
                return new DutySession(sessionId, actor.getUserId(), trimmedShift, trimmedZone, now, null, "OPEN");
            });
        } catch (IllegalStateException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("Attendant already has an open duty session")) {
                throw failure;
            }
            if (isOpenSessionConflict(failure)) {
                throw new IllegalStateException("Attendant already has an open duty session: " + actor.getUserId(), failure);
            }
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not start duty session", failure);
        }
    }

    public synchronized DutySession end(User actor, LocalDateTime now) {
        requireDutyActor(actor);
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        DutySession open = current(actor)
                .orElseThrow(() -> new IllegalStateException("Attendant has no open duty session: " + actor.getUserId()));
        if (now.isBefore(open.startedAt())) {
            throw new IllegalArgumentException("Duty end time cannot be before duty start time");
        }
        try {
            return store.inTransaction(() -> {
                store.updateDutySessionEnd(open.sessionId(), now.toString(), "CLOSED");
                Duration worked = Duration.between(open.startedAt(), now);
                notifications.publish(actor.getUserId(), NotificationType.ENTRY_EXIT,
                        "Duty ended: " + open.shift() + " shift, " + open.zone()
                                + ". On duty for " + formatDuration(worked) + ".");
                return new DutySession(open.sessionId(), open.attendantId(), open.shift(), open.zone(),
                        open.startedAt(), now, "CLOSED");
            });
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not end duty session", failure);
        }
    }

    public synchronized Optional<DutySession> current(User actor) {
        requireActor(actor);
        return store.loadOpenDutySession(actor.getUserId()).map(this::toSession);
    }

    public synchronized List<DutySession> history(User actor) {
        requireActor(actor);
        List<DutySession> result = new ArrayList<>();
        for (PersistenceStore.DutySessionRow row : store.loadDutySessionsForAttendant(actor.getUserId())) {
            result.add(toSession(row));
        }
        return result;
    }

    public synchronized DutySummary summary(User actor, LocalDateTime from, LocalDateTime to) {
        requireAdmin(actor);
        requireRange(from, to);
        return summarize(actor.getUserId(), from, to);
    }

    public synchronized DutySummary summary(User admin, String attendantId, LocalDateTime from, LocalDateTime to) {
        requireAdmin(admin);
        if (attendantId == null || attendantId.isBlank()) {
            throw new IllegalArgumentException("attendantId cannot be null or blank");
        }
        requireRange(from, to);
        return summarize(attendantId, from, to);
    }

    public synchronized List<DutySummary> summaries(User admin, LocalDateTime from, LocalDateTime to) {
        requireAdmin(admin);
        requireRange(from, to);
        Map<String, List<Interval>> byAttendant = new TreeMap<>();
        for (PersistenceStore.DutySessionRow row : store.loadAllDutySessions()) {
            Interval overlap = overlap(toSession(row), from, to);
            if (overlap != null) {
                byAttendant.computeIfAbsent(row.attendantId(), ignored -> new ArrayList<>()).add(overlap);
            }
        }
        List<Payment> payments = completedPayments();
        // Dedup rule (documented on the class): each COMPLETED payment is
        // attributed to exactly one attendant — the containing window with the
        // earliest start (tie-break by attendant id) — so overlapping shifts
        // never inflate team totals.
        Map<String, Long> transactionsByAttendant = new TreeMap<>();
        Map<String, Double> revenueByAttendant = new TreeMap<>();
        for (String attendantId : byAttendant.keySet()) {
            transactionsByAttendant.put(attendantId, 0L);
            revenueByAttendant.put(attendantId, 0.0);
        }
        for (Payment payment : payments) {
            LocalDateTime at = payment.getPaymentTime();
            if (at == null) continue;
            String winner = null;
            LocalDateTime winnerStart = null;
            for (Map.Entry<String, List<Interval>> entry : byAttendant.entrySet()) {
                for (Interval window : entry.getValue()) {
                    if (!at.isBefore(window.start()) && !at.isAfter(window.end())) {
                        if (winner == null || window.start().isBefore(winnerStart)
                                || (window.start().equals(winnerStart) && entry.getKey().compareTo(winner) < 0)) {
                            winner = entry.getKey();
                            winnerStart = window.start();
                        }
                        break;
                    }
                }
            }
            if (winner != null) {
                transactionsByAttendant.merge(winner, 1L, Long::sum);
                revenueByAttendant.merge(winner, payment.getFinalAmount(), Double::sum);
            }
        }
        List<DutySummary> result = new ArrayList<>();
        for (Map.Entry<String, List<Interval>> entry : byAttendant.entrySet()) {
            Duration total = Duration.ZERO;
            for (Interval window : entry.getValue()) total = total.plus(Duration.between(window.start(), window.end()));
            result.add(new DutySummary(entry.getKey(), attendantName(entry.getKey()),
                    transactionsByAttendant.getOrDefault(entry.getKey(), 0L),
                    revenueByAttendant.getOrDefault(entry.getKey(), 0.0), total));
        }
        result.sort(Comparator.comparing(DutySummary::attendantName).thenComparing(DutySummary::attendantId));
        return result;
    }

    private DutySummary summarize(String attendantId, LocalDateTime from, LocalDateTime to) {
        List<Interval> windows = new ArrayList<>();
        for (PersistenceStore.DutySessionRow row : store.loadDutySessionsForAttendant(attendantId)) {
            Interval overlap = overlap(toSession(row), from, to);
            if (overlap != null) windows.add(overlap);
        }
        return buildSummary(attendantId, windows, completedPayments());
    }

    private DutySummary buildSummary(String attendantId, List<Interval> windows, List<Payment> payments) {
        Duration total = Duration.ZERO;
        for (Interval window : windows) total = total.plus(Duration.between(window.start(), window.end()));
        long transactions = 0;
        double revenue = 0.0;
        for (Payment payment : payments) {
            LocalDateTime at = payment.getPaymentTime();
            if (at == null) continue;
            for (Interval window : windows) {
                if (!at.isBefore(window.start()) && !at.isAfter(window.end())) {
                    transactions++;
                    revenue += payment.getFinalAmount();
                    break;
                }
            }
        }
        return new DutySummary(attendantId, attendantName(attendantId), transactions, revenue, total);
    }

    private List<Payment> completedPayments() {
        List<Payment> result = new ArrayList<>();
        for (Payment payment : store.loadPayments()) {
            if (payment.getStatus() == PaymentStatus.COMPLETED) result.add(payment);
        }
        return result;
    }

    private String attendantName(String attendantId) {
        for (User user : store.loadUsers()) {
            if (attendantId.equals(user.getUserId())) {
                if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
                if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
                return attendantId;
            }
        }
        return attendantId;
    }

    private static Interval overlap(DutySession session, LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = session.endedAt() == null ? to : session.endedAt();
        LocalDateTime start = session.startedAt().isAfter(from) ? session.startedAt() : from;
        LocalDateTime finish = end.isBefore(to) ? end : to;
        if (finish.isBefore(start)) return null;
        return new Interval(start, finish);
    }

    private DutySession toSession(PersistenceStore.DutySessionRow row) {
        LocalDateTime startedAt = LocalDateTime.parse(row.startedAt());
        LocalDateTime endedAt = row.endedAt() == null || row.endedAt().isBlank() || "null".equalsIgnoreCase(row.endedAt())
                ? null : LocalDateTime.parse(row.endedAt());
        return new DutySession(row.sessionId(), row.attendantId(), row.shift(), row.zone(),
                startedAt, endedAt, row.status());
    }

    private static String formatDuration(Duration worked) {
        long minutes = worked.toMinutes();
        if (minutes < 60) return minutes + " min";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private void requireKnownUser(User actor) {
        if (!store.hasUser(actor.getUserId())) {
            throw new IllegalArgumentException("Duty attendant does not exist: " + actor.getUserId());
        }
    }

    private static boolean isOpenSessionConflict(IllegalStateException failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String upper = message.toUpperCase();
                if (upper.contains("IDX_DUTY_SESSIONS_OPEN_ATTENDANT") || upper.contains("DUTY_SESSIONS")
                        || (upper.contains("UNIQUE") && upper.contains("ATTENDANT"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireDutyActor(User actor) {
        requireActor(actor);
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.ATTENDANT) {
            throw new AuthorizationException("Only attendants may manage duty sessions.");
        }
    }

    private static void requireAdmin(User actor) {
        requireActor(actor);
        if (actor.getRole() != UserRole.ADMIN) {
            throw new AuthorizationException("Only administrators may view duty summaries.");
        }
    }

    private static void requireActor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() != UserRole.ADMIN
                && actor.getRole() != UserRole.ATTENDANT
                && actor.getRole() != UserRole.CUSTOMER) {
            throw new AuthorizationException("You are not authorized for parking operations.");
        }
    }

    private static void requireRange(LocalDateTime from, LocalDateTime to) {
        if (from == null) throw new IllegalArgumentException("from cannot be null");
        if (to == null) throw new IllegalArgumentException("to cannot be null");
        if (to.isBefore(from)) throw new IllegalArgumentException("to cannot be before from");
    }

    private record Interval(LocalDateTime start, LocalDateTime end) { }
}
