package com.parking.gui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ctrl/Cmd+K launcher with fuzzy matching and recent actions. */
public final class CommandPalette extends VBox {
    public record Command(String title, String detail, Runnable action) { }
    private static final List<Command> RECENT = new ArrayList<>();
    private final List<Command> commands;
    private final ListView<Command> results = new ListView<>();
    private final TextField search = new TextField();
    private ModalDialog modal;

    private CommandPalette(List<Command> commands) {
        this.commands = new ArrayList<>(commands);
        setSpacing(10);
        setPadding(new Insets(18));
        setPrefWidth(620);
        getStyleClass().add("command-palette");
        Label hint = DesignTokens.text("Command Palette  ·  Ctrl+K", 12, DesignTokens.MUTED, true);
        search.setPromptText("Search spots, tickets, users, payments, settings…");
        search.getStyleClass().add("dark-input");
        search.setPrefHeight(44);
        results.setPrefHeight(360);
        results.getStyleClass().add("command-results");
        results.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Command item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                setGraphic(new VBox(2, DesignTokens.text(item.title(), 14, DesignTokens.TEXT, true),
                        DesignTokens.text(item.detail(), 11, DesignTokens.MUTED, false)));
                setPadding(new Insets(9, 12, 9, 12));
            }
        });
        getChildren().addAll(hint, search, results);
        search.textProperty().addListener((obs, old, now) -> filter(now));
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) { results.requestFocus(); results.getSelectionModel().selectNext(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER) executeSelected();
        });
        results.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) executeSelected();
            else if (e.getCode() == KeyCode.UP && results.getSelectionModel().getSelectedIndex() == 0) { search.requestFocus(); e.consume(); }
        });
        results.setOnMouseClicked(e -> { if (e.getClickCount() == 2) executeSelected(); });
        filter("");
    }

    public static CommandPalette show(javafx.scene.layout.StackPane host, List<Command> commands) {
        CommandPalette palette = new CommandPalette(commands);
        palette.modal = ModalDialog.show(host, palette);
        palette.search.requestFocus();
        return palette;
    }

    private void filter(String query) {
        List<Command> source = new ArrayList<>();
        source.addAll(RECENT);
        commands.stream().filter(c -> !source.contains(c)).forEach(source::add);
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Command> filtered = source.stream()
                .map(c -> new Match(c, score(q, (c.title() + " " + c.detail()).toLowerCase())))
                .filter(m -> q.isEmpty() || m.score >= 0)
                .sorted(Comparator.comparingInt(Match::score).reversed()).map(Match::command).toList();
        results.getItems().setAll(filtered);
        if (!filtered.isEmpty()) results.getSelectionModel().selectFirst();
    }

    private void executeSelected() {
        Command command = results.getSelectionModel().getSelectedItem();
        if (command == null) return;
        RECENT.remove(command); RECENT.add(0, command);
        if (RECENT.size() > 6) RECENT.remove(RECENT.size() - 1);
        modal.close();
        command.action().run();
    }

    private int score(String query, String text) {
        if (query.isEmpty()) return 0;
        int cursor = 0, score = 0;
        for (char c : query.toCharArray()) {
            int found = text.indexOf(c, cursor);
            if (found < 0) return -1;
            score += found == cursor ? 4 : 1;
            cursor = found + 1;
        }
        return score - (text.length() - cursor) / 20;
    }

    private record Match(Command command, int score) { }
}
