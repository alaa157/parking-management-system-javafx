package com.parking.services;

import com.parking.enums.UserRole;
import com.parking.exceptions.GarageAccessException;
import com.parking.model.Garage;
import com.parking.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns the authenticated user's active garage selection for the JavaFX shell. */
public final class GarageContext {
    private final GarageService garages;
    private final User actor;
    private final List<Consumer<GarageContext>> listeners = new ArrayList<>();
    private String selectedGarageId;
    private boolean allGarages;

    public GarageContext(GarageService garages, User actor) {
        this.garages = Objects.requireNonNull(garages);
        this.actor = Objects.requireNonNull(actor);
    }

    public User getActor() { return actor; }
    public String getSelectedGarageId() { return selectedGarageId; }
    public boolean isAllGarages() { return allGarages; }
    public boolean hasOperationalSelection() { return selectedGarageId != null && !allGarages; }

    public Garage getSelectedGarage() {
        return selectedGarageId == null ? null : garages.getGarage(selectedGarageId);
    }

    public List<Garage> accessibleGarages() { return garages.listAccessibleGarages(actor); }

    public void select(String garageId) {
        garages.requireAccess(actor, garageId);
        selectedGarageId = garageId;
        allGarages = false;
        publish();
    }

    public void selectAll() {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new GarageAccessException("Only global administrators can select all garages");
        }
        selectedGarageId = null;
        allGarages = true;
        publish();
    }

    public void refresh() {
        if (allGarages) {
            if (actor.getRole() != UserRole.ADMIN) {
                allGarages = false;
                publish();
            }
            return;
        }
        if (selectedGarageId != null && !garages.canAccess(actor, selectedGarageId)) {
            selectedGarageId = null;
            publish();
        }
    }

    public void addListener(Consumer<GarageContext> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Consumer<GarageContext> listener) { listeners.remove(listener); }

    private void publish() { listeners.forEach(listener -> listener.accept(this)); }
}
