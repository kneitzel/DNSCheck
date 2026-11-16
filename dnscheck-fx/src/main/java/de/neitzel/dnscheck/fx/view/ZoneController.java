package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.fx.*;
import de.neitzel.dnscheck.fx.model.*;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.Message;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

/** A merged file/live record view with explicit popup editing and background network operations. */
@Singleton
public class ZoneController implements HasViewModel {
    private final Desk desk;
    private final ZoneFiles zoneFiles;
    private ViewModel viewModel;
    private ZoneEditor editor;
    private LiveZone liveSnapshot;
    private long loadSequence;
    private boolean closed;
    private boolean closing;
    private java.util.function.Consumer<String> status = text -> {};
    private boolean liveRequested;
    private java.util.concurrent.CompletableFuture<Boolean> operation =
            java.util.concurrent.CompletableFuture.completedFuture(true);

    @FXML private VBox zoneRoot;
    @FXML private Label liveStatus;
    @FXML private ComboBox<String> keyFileField;
    @FXML private Button dryRunFileButton;
    @FXML private Button reconcileFileButton;
    @FXML private Button dryRunButton;
    @FXML private Button reconcileButton;
    @FXML private TableView<RecordRow> recordTable;
    @FXML private TableColumn<RecordRow, RecordRow.Kind> kindColumn;
    @FXML private TableColumn<RecordRow, String> nameColumn;
    @FXML private TableColumn<RecordRow, String> typeColumn;
    @FXML private TableColumn<RecordRow, String> valueColumn;
    @FXML private TableColumn<RecordRow, String> ttlColumn;
    @FXML private TableColumn<RecordRow, String> statusColumn;

    @Inject
    public ZoneController(Desk desk, ZoneFiles zoneFiles) {
        this.desk = desk;
        this.zoneFiles = zoneFiles;
    }

    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
        this.editor = desk.zone();
        liveSnapshot = null;
        column(nameColumn, RecordRow::name);
        column(typeColumn, RecordRow::type);
        column(valueColumn, RecordRow::value);
        column(ttlColumn, RecordRow::ttl);
        column(statusColumn, RecordRow::status);
        kindColumn.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().kind()));
        kindColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(RecordRow.Kind kind, boolean empty) {
                super.updateItem(kind, empty);
                setText(empty || kind == null ? null : switch (kind) { case ENFORCED -> "✓"; case REMOVAL -> "−"; case LIVE -> "●"; });
                setTooltip(empty || kind == null ? null : new Tooltip(switch (kind) {
                    case ENFORCED -> "Enforced entry"; case REMOVAL -> "Enforced removal"; case LIVE -> "Live entry";
                }));
                setAccessibleText(getTooltip() == null ? "" : getTooltip().getText());
            }
        });
        recordTable.setRowFactory(table -> {
            TableRow<RecordRow> row = new TableRow<>() {
                @Override protected void updateItem(RecordRow item, boolean empty) {
                    super.updateItem(item, empty);
                    pseudoClassStateChanged(PseudoClass.getPseudoClass("file-entry"), !empty && item != null && item.kind() == RecordRow.Kind.ENFORCED);
                    pseudoClassStateChanged(PseudoClass.getPseudoClass("removal-entry"), !empty && item != null && item.kind() == RecordRow.Kind.REMOVAL);
                    pseudoClassStateChanged(PseudoClass.getPseudoClass("live-entry"), !empty && item != null && item.kind() == RecordRow.Kind.LIVE);
                }
            };
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty()) showRecord(row.getItem()); });
            return row;
        });
        recordTable.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER) editRecord(); });
        showRows();
        zoneRoot.setUserData(this);
        icon(dryRunFileButton, "Dry-run file", "M2,1 H11 L15,5 V16 H2 Z M10,1 V6 H15 M5,9 H11 M5,12 H9");
        icon(reconcileFileButton, "Reconcile file", "M2,1 H11 L15,5 V16 H2 Z M10,1 V6 H15 M5,10 L7,13 L12,8");
        icon(dryRunButton, "Save and dry-run", "M2,1 H13 L16,4 V16 H2 Z M5,1 V6 H12 V1 M5,16 V10 H12 V16");
        icon(reconcileButton, "Save and reconcile", "M8,1 L3,9 H8 L7,16 L14,6 H9 Z");
    }

    ZoneEditor editor() { return editor; }

    void setStatus(java.util.function.Consumer<String> status) { this.status = status; }

    void reportError(String error) { note(Message.Severity.ERROR, error); }

    void refreshKeys(java.util.List<String> available) {
        String current = editor.getKeyFile();
        keyFileField.getItems().setAll(available);
        keyFileField.setValue(current);
    }

    private void icon(Button button, String text, String path) {
        var graphic = new javafx.scene.shape.SVGPath();
        graphic.setContent(path);
        graphic.getStyleClass().add("zone-action-icon");
        button.setGraphic(graphic);
        button.setAccessibleText(text);
        button.setOnMouseEntered(event -> status.accept(text));
        button.setOnMouseExited(event -> status.accept(""));
        button.focusedProperty().addListener((observable, previous, focused) -> status.accept(focused ? text : ""));
    }

    void reload(boolean discard) {
        if (closed) return;
        if (editor.isApplying()) {
            operation.thenRun(() -> reload(discard));
            return;
        }
        try {
            var definition = zoneFiles.readDefinition(Path.of(editor.getSourcePath()));
            if (editor.isDirty() && !discard) {
                if (!definition.equals(editor.getDiskDefinition())) {
                    note(Message.Severity.WARN, "Zone file changed on disk. Reload from disk to discard pending edits.");
                }
                return;
            }
            if (!definition.equals(editor.getDiskDefinition()) || discard) {
                editor.acceptDefinition(definition);
                Attributes.set(viewModel, "nameserver", definition.nameserver());
                Attributes.set(viewModel, "keyFile", definition.keyFile());
                showRows();
                if (liveRequested) refreshUsingSavedSettings();
            }
        } catch (IOException | RuntimeException failure) {
            note(Message.Severity.ERROR, text(failure));
        }
    }

    @FXML public void reloadFromDisk() {
        if (editor.isDirty()) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Discard pending zone settings and reload the file?");
            if (zoneRoot.getScene() != null) confirmation.initOwner(zoneRoot.getScene().getWindow());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        reload(true);
    }

    void activated() {
        if (!liveRequested) refreshLive();
    }

    void setClosing(boolean closing) {
        this.closing = closing;
        updateDisabled();
    }

    private void updateDisabled() {
        zoneRoot.setDisable(closing || editor.isApplying());
    }

    void disposed() {
        status.accept("");
        closed = true;
        ++loadSequence;
    }

    java.util.concurrent.CompletionStage<Boolean> pendingOperation() { return operation; }

    private static void column(TableColumn<RecordRow, String> column, Function<RecordRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    @FXML public void refreshLive() {
        if (editor.isApplying()) return;
        if (editor.isDirty()) { note(Message.Severity.WARN, "Save zone settings before refreshing live DNS."); return; }
        refreshUsingSavedSettings();
    }

    void refreshUsingSavedSettings() {
        if (editor.isApplying()) return;
        liveRequested = true;
        ZoneEditor opened = editor;
        ZoneDefinition definition = definition();
        long sequence = ++loadSequence;
        liveSnapshot = null;
        showRows();
        liveStatus.setText("Loading live DNS from " + definition.nameserver() + "…");
        Thread.startVirtualThread(() -> {
            try {
                LiveZone loaded = zoneFiles.readLive(definition);
                Platform.runLater(() -> {
                    if (!current(opened, sequence)) return;
                    liveSnapshot = loaded;
                    liveStatus.setText("Live DNS from " + definition.nameserver() + ": " + loaded.records().size() + " records (SOA is read-only).");
                    showRows();
                });
            } catch (Exception failure) {
                Platform.runLater(() -> {
                    if (!current(opened, sequence)) return;
                    liveSnapshot = null;
                    liveStatus.setText("Live DNS unavailable: " + text(failure) + ". Showing domain-file entries only.");
                    showRows();
                });
            }
        });
    }

    private boolean current(ZoneEditor opened, long sequence) {
        return editor == opened && !closed && sequence == loadSequence;
    }

    private ZoneDefinition definition() {
        return editor.getDiskDefinition() == null ? editor.toDefinition() : editor.getDiskDefinition();
    }

    private void showRows() {
        recordTable.getItems().setAll(RecordRow.merge(definition(), liveSnapshot));
    }

    @FXML public void addRecord() { showRecord(null); }
    @FXML public void editRecord() {
        RecordRow selected = recordTable.getSelectionModel().getSelectedItem();
        if (selected == null) { note(Message.Severity.WARN, "Select a record to view its details."); return; }
        showRecord(selected);
    }

    protected RecordDialog dialog(RecordRow row) {
        return new RecordDialog(zoneRoot.getScene() == null ? null : zoneRoot.getScene().getWindow(), editor.getZone(), row);
    }

    private void showRecord(RecordRow row) {
        if (editor.isApplying()) return;
        if (editor.isDirty()) { note(Message.Severity.WARN, "Save zone settings before editing records."); return; }
        dialog(row).showAndWait().ifPresent(this::apply);
    }

    private void apply(RecordChange request) {
        final Path directory;
        try { directory = desk.requireLoadedDirectory(); }
        catch (RuntimeException failure) { note(Message.Severity.ERROR, text(failure)); return; }
        ZoneEditor opened = editor;
        ZoneDefinition expected = definition();
        if (!expected.sourcePath().toAbsolutePath().getParent().equals(directory)) {
            note(Message.Severity.ERROR, "Reload the domain before editing records."); return;
        }
        ++loadSequence; // An older AXFR must not overwrite the result of this edit.
        operation = new java.util.concurrent.CompletableFuture<>();
        opened.setApplying(true);
        updateDisabled();
        note(Message.Severity.INFO, request.online() ? "Applying record change…" : "Saving file entry…");
        Thread.startVirtualThread(() -> {
            try {
                ZoneFiles.MutationResult result = zoneFiles.applyRecord(expected, request);
                Platform.runLater(() -> {
                    opened.setApplying(false);
                    if (closed || editor != opened) return;
                    updateDisabled();
                    opened.acceptDefinition(result.definition());
                    if (result.onlineAttempted()) {
                        liveSnapshot = result.live();
                        liveStatus.setText(result.live() == null ? "Live state is unconfirmed. Refresh live DNS to inspect it."
                                : "Live DNS refreshed: " + result.live().records().size() + " records.");
                    }
                    showRows();
                    note(result.onlineAttempted() && !result.onlineApplied() ? Message.Severity.ERROR
                            : result.onlineAttempted() && result.live() == null ? Message.Severity.WARN : Message.Severity.INFO, result.message());
                    operation.complete(!result.onlineAttempted() || result.onlineApplied());
                });
            } catch (Exception failure) {
                Platform.runLater(() -> {
                    opened.setApplying(false);
                    if (closed || editor != opened) return;
                    updateDisabled();
                    note(Message.Severity.ERROR, text(failure));
                    operation.complete(false);
                });
            }
        });
    }

    @FXML public void save() {
        if (editor.isApplying()) return;
        if (write()) { note(Message.Severity.INFO, "Zone settings saved."); refreshLive(); }
    }
    @FXML public void dryRun() { run(true, true); }
    @FXML public void reconcile() { run(false, true); }
    @FXML public void dryRunFile() { run(true, false); }
    @FXML public void reconcileFile() { run(false, false); }

    private void run(boolean dryRun, boolean saveFirst) {
        if (editor.isApplying()) { note(Message.Severity.WARN, "A record operation is already running."); return; }
        final Path directory;
        try { directory = desk.requireLoadedDirectory(); }
        catch (RuntimeException failure) { note(Message.Severity.ERROR, text(failure)); return; }
        if (saveFirst && !write()) return;
        ZoneEditor opened = editor;
        operation = new java.util.concurrent.CompletableFuture<>();
        opened.setApplying(true);
        updateDisabled();
        ++loadSequence;
        note(Message.Severity.INFO, dryRun ? "Dry-run…" : "Reconciling…");
        Thread.startVirtualThread(() -> {
            ZoneReconciliationResult result;
            try { result = zoneFiles.reconcile(directory, opened.getZone(), dryRun); }
            catch (RuntimeException failure) { result = ZoneReconciliationResult.failed(opened.getZone(), text(failure)); }
            ZoneReconciliationResult completed = result;
            Platform.runLater(() -> {
                opened.setApplying(false);
                if (closed || editor != opened) return;
                updateDisabled();
                note(OutcomeText.severity(completed.outcome()), OutcomeText.of(completed));
                if (!opened.isDirty()) refreshLive();
                operation.complete(OutcomeText.severity(completed.outcome()) != Message.Severity.ERROR);
            });
        });
    }

    private boolean write() {
        try { zoneFiles.save(editor); return true; }
        catch (RuntimeException | IOException failure) { note(Message.Severity.ERROR, text(failure)); return false; }
    }
    private void note(Message.Severity severity, String message) {
        viewModel.addMessage(new Message(severity, message, "records"));
        Attributes.set(viewModel, "outcome", message);
    }
    private static String text(Exception failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank() ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
