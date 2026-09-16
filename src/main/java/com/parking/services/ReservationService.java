package com.parking.services;

import com.parking.config.AppConfig;
import com.parking.enums.NotificationType;
import com.parking.enums.ReservationStatus;
import com.parking.enums.SpotStatus;
import com.parking.enums.UserRole;
import com.parking.exceptions.AuthorizationException;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Reservation;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted reservation workflow on top of the notification backbone.
 * The parking-spot state is the source of truth for availability; every
 * lifecycle transition runs in one {@link PersistenceStore#inTransaction}
 * and publishes exactly one backbone notification for the holder.
 */
public class ReservationService {
    private final ParkingGarage garage;
    private final PersistenceStore store;
    private final NotificationService notifications;

    public ReservationService(ParkingGarage garage, PersistenceStore store, NotificationService notifications) {
        if (garage == null) throw new IllegalArgumentException("garage cannot be null");
        if (store == null) throw new IllegalArgumentException("store cannot be null");
        if (notifications == null) throw new IllegalArgumentException("notifications cannot be null");
        this.garage = garage;
        this.store = store;
        this.notifications = notifications;
    }

    public synchronized Reservation reserve(User actor, String spotId, LocalDateTime now) {
        return reserve(actor, spotId, now, AppConfig.reservationHoldMinutes());
    }

    public synchronized Reservation reserve(User actor, String spotId, LocalDateTime now, int holdMinutes) {
        requireOperationalActor(actor);
        if (spotId == null || spotId.isBlank()) throw new IllegalArgumentException("spotId cannot be null or blank");
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        if (holdMinutes <= 0) throw new IllegalArgumentException("Hold time must be greater than 0");
        if (!store.hasUser(actor.getUserId())) {
            throw new IllegalArgumentException("Reservation holder does not exist: " + actor.getUserId());
        }
        ParkingSpot spot = garage.getSpotById(spotId);
        if (spot == null) throw new IllegalArgumentException("Parking spot does not exist: " + spotId);
        expireSingleIfElapsed(spotId, now);
        if (!spot.isAvailable()) {
            throw new IllegalStateException("Parking spot is not available for reservation: " + spotId);
        }
        if (store.loadActiveReservationForSpot(spotId).isPresent()) {
            throw new IllegalStateException("Parking spot is already reserved: " + spotId);
        }
        LocalDateTime expiresAt = now.plusMinutes(holdMinutes);
        String reservationId = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SpotSnapshot before = SpotSnapshot.of(spot);
        try {
            return store.inTransaction(() -> {
                if (!spot.reserveSpot(actor.getUserId())) {
                    throw new IllegalStateException("Parking spot could not be reserved: " + spotId);
                }
                spot.setReservationExpiry(expiresAt);
                garage.updateAvailability();
                store.saveReservation(reservationId, actor.getUserId(), spotId,
                        now.toString(), expiresAt.toString(), ReservationStatus.ACTIVE.name());
                store.saveParkingSpot(garage, spot);
                notifications.publish(actor.getUserId(), NotificationType.ENTRY_EXIT,
                        "Reservation " + reservationId + " confirmed for spot " + spotId
                                + ". Expires at " + expiresAt + ".");
                return new Reservation(reservationId, actor.getUserId(), spotId, now, expiresAt, ReservationStatus.ACTIVE);
            });
        } catch (RuntimeException failure) {
            before.restore(spot, garage);
            throw failure;
        } catch (Exception failure) {
            before.restore(spot, garage);
            throw new IllegalStateException("Could not create reservation", failure);
        }
    }

    public synchronized Reservation cancel(User actor, String reservationId, LocalDateTime now) {
        requireOperationalActor(actor);
        if (reservationId == null || reservationId.isBlank()) throw new IllegalArgumentException("reservationId cannot be null or blank");
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        Reservation reservation = loadOrThrow(reservationId);
        requireHolderOrAdmin(actor, reservation);
        if (reservation.status() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not active: " + reservationId);
        }
        if (!now.isBefore(reservation.expiresAt())) {
            expireSingle(reservation);
            throw new IllegalStateException("Reservation has already expired: " + reservationId);
        }
        ParkingSpot spot = garage.getSpotById(reservation.spotId());
        SpotSnapshot before = spot == null ? null : SpotSnapshot.of(spot);
        try {
            return store.inTransaction(() -> {
                store.updateReservationStatus(reservationId, ReservationStatus.CANCELLED.name());
                if (spot != null) {
                    clearHold(spot, reservation);
                    garage.updateAvailability();
                    store.saveParkingSpot(garage, spot);
                }
                notifications.publish(reservation.userId(), NotificationType.ENTRY_EXIT,
                        "Reservation " + reservationId + " for spot " + reservation.spotId() + " was cancelled.");
                return new Reservation(reservation.reservationId(), reservation.userId(), reservation.spotId(),
                        reservation.createdAt(), reservation.expiresAt(), ReservationStatus.CANCELLED);
            });
        } catch (RuntimeException failure) {
            if (before != null && spot != null) before.restore(spot, garage);
            throw failure;
        } catch (Exception failure) {
            if (before != null && spot != null) before.restore(spot, garage);
            throw new IllegalStateException("Could not cancel reservation", failure);
        }
    }

    public synchronized List<String> expire(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        List<String> expiredIds = new ArrayList<>();
        for (PersistenceStore.ReservationRow row : store.loadActiveReservations()) {
            LocalDateTime expiresAt = LocalDateTime.parse(row.expiresAt());
            if (!now.isBefore(expiresAt)) {
                expireSingle(toReservation(row));
                expiredIds.add(row.reservationId());
            }
        }
        return expiredIds;
    }

    public synchronized Reservation claimForEntry(User actor, Vehicle vehicle, String reservationId) {
        return claimForEntry(actor, vehicle, reservationId, LocalDateTime.now());
    }

    public synchronized Reservation claimForEntry(User actor, Vehicle vehicle, String reservationId, LocalDateTime now) {
        requireOperationalActor(actor);
        if (now == null) throw new IllegalArgumentException("now cannot be null");
        if (vehicle == null) throw new IllegalArgumentException("vehicle cannot be null");
        if (vehicle.getVehicleId() == null || vehicle.getVehicleId().isBlank()) {
            throw new IllegalArgumentException("vehicleId is required");
        }
        if (reservationId == null || reservationId.isBlank()) throw new IllegalArgumentException("reservationId cannot be null or blank");
        Reservation reservation = loadOrThrow(reservationId);
        if (reservation.status() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not active: " + reservationId);
        }
        requireHolderOrAdmin(actor, reservation);
        if (actor.getRole() == UserRole.CUSTOMER && !actor.getUserId().equals(vehicle.getUserId())) {
            throw new AuthorizationException("Customers may only claim reservations with their own vehicles.");
        }
        if (!now.isBefore(reservation.expiresAt())) {
            expireSingle(reservation);
            throw new IllegalStateException("Reservation has already expired: " + reservationId);
        }
        ParkingSpot spot = garage.getSpotById(reservation.spotId());
        if (spot == null) throw new IllegalArgumentException("Parking spot does not exist: " + reservation.spotId());
        if (spot.getStatus() != SpotStatus.RESERVED) {
            throw new IllegalStateException("Reserved parking spot is not held: " + reservation.spotId());
        }
        if (spot.getReservationHolderUserId() != null && !spot.getReservationHolderUserId().equals(reservation.userId())) {
            throw new IllegalStateException("Reserved parking spot is held by another user: " + reservation.spotId());
        }
        SpotSnapshot before = SpotSnapshot.of(spot);
        try {
            return store.inTransaction(() -> {
                store.updateReservationStatus(reservationId, ReservationStatus.CLAIMED.name());
                spot.clearReservation();
                garage.updateAvailability();
                store.saveParkingSpot(garage, spot);
                notifications.publish(reservation.userId(), NotificationType.ENTRY_EXIT,
                        "Reservation " + reservationId + " for spot " + reservation.spotId() + " claimed at entry.");
                return new Reservation(reservation.reservationId(), reservation.userId(), reservation.spotId(),
                        reservation.createdAt(), reservation.expiresAt(), ReservationStatus.CLAIMED);
            });
        } catch (RuntimeException failure) {
            before.restore(spot, garage);
            throw failure;
        } catch (Exception failure) {
            before.restore(spot, garage);
            throw new IllegalStateException("Could not claim reservation", failure);
        }
    }

    public synchronized List<Reservation> activeForUser(User actor) {
        requireOperationalActor(actor);
        List<Reservation> result = new ArrayList<>();
        for (PersistenceStore.ReservationRow row : store.loadActiveReservationsForUser(actor.getUserId())) {
            result.add(toReservation(row));
        }
        return result;
    }

    public synchronized List<Reservation> activeReservations() {
        List<Reservation> result = new ArrayList<>();
        for (PersistenceStore.ReservationRow row : store.loadActiveReservations()) {
            result.add(toReservation(row));
        }
        return result;
    }

    public synchronized Optional<Reservation> activeForSpot(String spotId) {
        if (spotId == null || spotId.isBlank()) return Optional.empty();
        return store.loadActiveReservationForSpot(spotId).map(this::toReservation);
    }

    private void expireSingleIfElapsed(String spotId, LocalDateTime now) {
        Optional<PersistenceStore.ReservationRow> existing = store.loadActiveReservationForSpot(spotId);
        if (existing.isPresent() && !now.isBefore(LocalDateTime.parse(existing.get().expiresAt()))) {
            expireSingle(toReservation(existing.get()));
        }
    }

    private void expireSingle(Reservation reservation) {
        ParkingSpot spot = garage.getSpotById(reservation.spotId());
        SpotSnapshot before = spot == null ? null : SpotSnapshot.of(spot);
        try {
            store.inTransaction(() -> {
                store.updateReservationStatus(reservation.reservationId(), ReservationStatus.EXPIRED.name());
                if (spot != null) {
                    clearHold(spot, reservation);
                    garage.updateAvailability();
                    store.saveParkingSpot(garage, spot);
                }
                notifications.publish(reservation.userId(), NotificationType.ENTRY_EXIT,
                        "Reservation " + reservation.reservationId() + " for spot " + reservation.spotId() + " expired.");
                return null;
            });
        } catch (RuntimeException failure) {
            if (before != null && spot != null) before.restore(spot, garage);
            throw failure;
        } catch (Exception failure) {
            if (before != null && spot != null) before.restore(spot, garage);
            throw new IllegalStateException("Could not expire reservation", failure);
        }
    }

    private static void clearHold(ParkingSpot spot, Reservation reservation) {
        if (spot.getStatus() != SpotStatus.RESERVED) return;
        if (spot.getReservationHolderUserId() != null && !spot.getReservationHolderUserId().equals(reservation.userId())) return;
        spot.clearReservation();
    }

    private Reservation loadOrThrow(String reservationId) {
        return store.loadReservation(reservationId).map(this::toReservation)
                .orElseThrow(() -> new IllegalArgumentException("Reservation does not exist: " + reservationId));
    }

    private Reservation toReservation(PersistenceStore.ReservationRow row) {
        return new Reservation(row.reservationId(), row.userId(), row.spotId(),
                LocalDateTime.parse(row.createdAt()), LocalDateTime.parse(row.expiresAt()),
                ReservationStatus.valueOf(row.status()));
    }

    private static void requireHolderOrAdmin(User actor, Reservation reservation) {
        if (actor.getUserId().equals(reservation.userId()) || actor.getRole() == UserRole.ADMIN) return;
        throw new AuthorizationException("Only the reservation holder may perform this action.");
    }

    private static void requireOperationalActor(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new AuthorizationException("An active authenticated user is required.");
        }
        if (actor.getRole() != UserRole.ADMIN
                && actor.getRole() != UserRole.ATTENDANT
                && actor.getRole() != UserRole.CUSTOMER) {
            throw new AuthorizationException("You are not authorized for parking operations.");
        }
    }

    private record SpotSnapshot(SpotStatus status, String holder, LocalDateTime expiry, String vehicleId) {
        static SpotSnapshot of(ParkingSpot spot) {
            return new SpotSnapshot(spot.getStatus(), spot.getReservationHolderUserId(),
                    spot.getReservationExpiry(), spot.getVehicleId());
        }

        void restore(ParkingSpot spot, ParkingGarage garage) {
            spot.setStatus(status);
            spot.setReservationHolderUserId(holder);
            spot.setReservationExpiry(expiry);
            spot.setVehicleId(vehicleId);
            garage.updateAvailability();
        }
    }
}
