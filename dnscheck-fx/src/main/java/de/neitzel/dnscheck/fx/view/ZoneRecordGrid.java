package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.fx.model.RecordRow;
import de.neitzel.fx.view.DataGrid;
import de.neitzel.fx.view.ResultRow;
import de.neitzel.grid.*;

import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.function.Consumer;

/** Maps the merged zone view to the portable grid without exposing DNS operations to it. */
final class ZoneRecordGrid implements AutoCloseable {
    private final Map<RecordRow, String> keys = new IdentityHashMap<>();

    @Getter
    @Accessors(fluent = true)
    private final DataGrid<String, RecordRow> grid;
    private final GridApplicationConfiguration configuration;
    private de.neitzel.fx.view.GridPersistenceAttachment<String, RecordRow> persistence;
    private final java.util.concurrent.CompletableFuture<Void> ready = new java.util.concurrent.CompletableFuture<>();
    private boolean initializing;

    ZoneRecordGrid(DataGrid<String, RecordRow> grid, Consumer<RecordRow> open, GridApplicationConfiguration configuration) {
        this.grid = grid;
        this.configuration = configuration;
        grid.setKeyFactory(keys::get);
        grid.setKeyComparator(String::compareTo);
        grid.setRowFactory(row -> render(row, grid, open));
        grid.setFocusTraversable(true);
        grid.itemsProperty().addListener((observable, previous, current) -> {
            if (current != null && !initializing) start();
        });
    }

    private void start() {
        initializing = true;
        grid.initialize();
        if (configuration != null) {
            persistence = grid.attachPersistence(configuration, "dnscheck.zone-records");
            persistence.ready().whenComplete((ok, error) -> {
                if (error == null) ready.complete(null);
                else ready.completeExceptionally(error);
            });
            persistence.loadAsync();
        } else grid.loadAsync().whenComplete((ok, error) -> {
            if (error == null) ready.complete(null);
            else ready.completeExceptionally(error);
        });
    }

    void show(List<RecordRow> updated) {
        keys.clear();
        Map<String, Integer> occurrences = new HashMap<>();
        for (RecordRow row : updated) {
            // Ignore changing status/TTL. Include live rdata to distinguish wildcard matches.
            String identity =
                    encode(
                            row.kind().name(),
                            row.name(),
                            row.type(),
                            row.value(),
                            row.live() == null ? "" : row.live().rdata());
            keys.put(row, identity + occurrences.merge(identity, 1, Integer::sum));
        }
    }

    java.util.concurrent.CompletionStage<Void> ready() {
        return ready;
    }

    private static String encode(String... values) {
        StringBuilder key = new StringBuilder();
        for (String value : values) key.append(value.length()).append(':').append(value);
        return key.toString();
    }

    private static ResultRow render(
            RecordRow row,
            DataGrid<String, RecordRow> grid,
            Consumer<RecordRow> open) {
        var root = new HBox();
        root.setUserData(row);
        root.setFocusTraversable(true);
        root.setAccessibleText(row.entry() + ": " + row.name() + " " + row.type());
        root.pseudoClassStateChanged(
                PseudoClass.getPseudoClass(
                        switch (row.kind()) {
                            case ENFORCED -> "file-entry";
                            case REMOVAL -> "removal-entry";
                            case LIVE -> "live-entry";
                        }),
                true);
        Map<String, Region> cells = new LinkedHashMap<>();
        grid.getColumns().forEach(column -> {
            String id = column.getId();
            Label label = new Label(Objects.toString(column.getValue(row), ""));
            label.getStyleClass().add("record-cell");
            label.setTooltip(new Tooltip(label.getText()));
            cells.put(id, label);
            root.getChildren().add(label);
        });
        root.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                root.requestFocus();
                if (event.getClickCount() == 2) open.accept(row);
            }
        });
        root.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                open.accept(row);
                event.consume();
            }
        });
        return new ResultRow(
                root,
                cells,
                () -> {
                    root.setOnMouseClicked(null);
                    root.setOnKeyPressed(null);
                });
    }

    @Override
    public void close() {
        grid.close();
        keys.clear();
    }
}
