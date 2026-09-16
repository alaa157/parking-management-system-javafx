package com.parking.gui;

import com.parking.enums.UserRole;
import com.parking.model.User;
import com.parking.services.GarageContext;
import com.parking.services.GarageService;
import javafx.scene.control.ComboBox;

import java.util.Objects;
import java.util.function.Consumer;

/** Garage context selector used by the application shell header. */
public final class GarageSelectorView {
    private final GarageService garages;
    private final GarageContext context;
    private final User actor;
    private final Consumer<String> error;

    public GarageSelectorView(GarageService garages, GarageContext context, User actor, Consumer<String> error) {
        this.garages = Objects.requireNonNull(garages);
        this.context = Objects.requireNonNull(context);
        this.actor = Objects.requireNonNull(actor);
        this.error = error == null ? ignored -> {} : error;
    }

    public ComboBox<String> build() {
        ComboBox<String> selector = new ComboBox<>();
        selector.setPromptText("Garage context");
        selector.setPrefWidth(190);
        garages.listAccessibleGarages(actor).forEach(g -> selector.getItems().add(g.getName() + " · " + g.getGarageId()));
        if (actor.getRole() == UserRole.ADMIN) selector.getItems().add("All garages");
        String selected = context.getSelectedGarageId();
        selector.setValue(context.isAllGarages() ? "All garages" : selected == null ? null
                : selector.getItems().stream().filter(value -> value.endsWith(selected)).findFirst().orElse(null));
        selector.setOnAction(event -> {
            try {
                if ("All garages".equals(selector.getValue())) context.selectAll();
                else if (selector.getValue() != null) context.select(selector.getValue()
                        .substring(selector.getValue().lastIndexOf('·') + 1).trim());
            } catch (RuntimeException failure) { error.accept(failure.getMessage()); }
        });
        return selector;
    }
}
