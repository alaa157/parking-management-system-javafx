package com.parking.services;

import com.parking.enums.UserRole;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.Garage;
import com.parking.model.GarageAccess;
import com.parking.model.User;
import com.parking.persistence.PersistenceStore;

import java.util.List;
import java.util.Optional;

/** Application service for garage lifecycle and user-to-garage access. */
public final class GarageService {
    private final PersistenceStore persistence;

    public GarageService(PersistenceStore persistence) {
        if (persistence == null) throw new IllegalArgumentException("persistence cannot be null");
        this.persistence = persistence;
    }

    public Garage createGarage(String garageId, String name, String address, int totalLevels,
                               double baseHourlyRate, String currency, int freeParkingMinutes,
                               int reservationHoldMinutes, int maxParkHours) {
        Garage garage = new Garage(garageId, name, address, totalLevels, baseHourlyRate,
                currency, freeParkingMinutes, reservationHoldMinutes, maxParkHours);
        persistence.saveGarage(garage);
        return garage;
    }

    public void updateGarage(Garage garage) {
        if (garage == null) throw new IllegalArgumentException("garage cannot be null");
        persistence.saveGarage(garage);
    }

    public Garage getGarage(String garageId) {
        return persistence.loadGarage(garageId)
                .orElseThrow(() -> new IllegalArgumentException("Garage not found: " + garageId));
    }

    public List<Garage> listGarages() { return persistence.loadGarages(); }

    public void archiveGarage(String garageId) { updateLifecycle(garageId, true); }
    public void reopenGarage(String garageId) { updateLifecycle(garageId, false); }

    private void updateLifecycle(String garageId, boolean archive) {
        Garage garage = getGarage(garageId);
        if (archive) garage.archive(); else garage.reopen();
        persistence.saveGarage(garage);
    }

    public GarageAccess grantAccess(String userId, String garageId, UserRole role) {
        if (!persistence.hasUser(userId)) throw new IllegalArgumentException("User not found: " + userId);
        Garage garage = getGarage(garageId);
        if (garage.isArchived()) throw new GarageAccessException("Archived garages cannot receive access");
        GarageAccess access = new GarageAccess(userId, garageId, role);
        persistence.saveGarageAccess(access);
        return access;
    }

    public void revokeAccess(String userId, String garageId) {
        GarageAccess access = persistence.loadGarageAccess(userId, garageId)
                .orElseThrow(() -> new GarageAccessException("Garage access not found"));
        access.setActive(false);
        persistence.saveGarageAccess(access);
    }

    public boolean canAccess(User user, String garageId) {
        if (user == null || !user.isActive()) return false;
        Optional<Garage> garage = persistence.loadGarage(garageId);
        if (garage.isEmpty() || garage.get().isArchived()) return false;
        if (user.getRole() == UserRole.ADMIN) return true;
        return persistence.loadGarageAccess(user.getUserId(), garageId)
                .map(GarageAccess::isActive).orElse(false);
    }

    public void requireAccess(User user, String garageId) {
        if (!canAccess(user, garageId)) {
            throw new GarageAccessException("User does not have access to garage: " + garageId);
        }
    }

    public List<Garage> listAccessibleGarages(User user) {
        return listGarages().stream().filter(garage -> canAccess(user, garage.getGarageId())).toList();
    }
}
