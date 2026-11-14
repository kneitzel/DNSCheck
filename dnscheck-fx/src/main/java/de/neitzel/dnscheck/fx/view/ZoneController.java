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

    @FXML private VBox zoneRoot;
    @FXML private Label liveStatus;
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
        refreshLive();
    }

    private static void column(TableColumn<RecordRow, String> column, Function<RecordRow, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    @FXML public void refreshLive() {
        if (editor.isApplying()) return;
        if (editor.isDirty()) { note(Message.Severity.WARN, "Save zone settings before refreshing live DNS."); return; }
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
        return editor == opened && desk.zone() == opened && sequence == loadSequence;
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
        opened.setApplying(true);
        zoneRoot.setDisable(true);
        note(Message.Severity.INFO, request.online() ? "Applying record change…" : "Saving file entry…");
        Thread.startVirtualThread(() -> {
            try {
                ZoneFiles.MutationResult result = zoneFiles.applyRecord(expected, request);
                Platform.runLater(() -> {
                    opened.setApplying(false);
                    if (desk.zone() != opened || editor != opened) return;
                    zoneRoot.setDisable(false);
                    opened.acceptDefinition(result.definition());
                    if (result.onlineAttempted()) {
                        liveSnapshot = result.live();
                        liveStatus.setText(result.live() == null ? "Live state is unconfirmed. Refresh live DNS to inspect it."
                                : "Live DNS refreshed: " + result.live().records().size() + " records.");
                    }
                    showRows();
                    note(result.onlineAttempted() && !result.onlineApplied() ? Message.Severity.ERROR
                            : result.onlineAttempted() && result.live() == null ? Message.Severity.WARN : Message.Severity.INFO, result.message());
                });
            } catch (Exception failure) {
                Platform.runLater(() -> {
                    opened.setApplying(false);
                    if (desk.zone() != opened || editor != opened) return;
                    zoneRoot.setDisable(false);
                    note(Message.Severity.ERROR, text(failure));
                });
            }
        });
    }

    @FXML public void save() {
        if (editor.isApplying()) return;
        if (write()) { note(Message.Severity.INFO, "Zone settings saved."); refreshLive(); }
    }
    @FXML public void dryRun() { run(true); }
    @FXML public void reconcile() { run(false); }

    private void run(boolean dryRun) {
        if (editor.isApplying()) { note(Message.Severity.WARN, "A record operation is already running."); return; }
        final Path directory;
        try { directory = desk.requireLoadedDirectory(); }
        catch (RuntimeException failure) { note(Message.Severity.ERROR, text(failure)); return; }
        if (!write()) return;
        ZoneEditor opened = editor;
        ViewModel target = viewModel;
        opened.setApplying(true);
        zoneRoot.setDisable(true);
        ++loadSequence;
        note(Message.Severity.INFO, dryRun ? "Dry-run…" : "Reconciling…");
        Thread.startVirtualThread(() -> {
            ZoneReconciliationResult result;
            try { result = zoneFiles.reconcile(directory, opened.getZone(), dryRun); }
            catch (RuntimeException failure) { result = ZoneReconciliationResult.failed(opened.getZone(), text(failure)); }
            ZoneReconciliationResult completed = result;
            Platform.runLater(() -> {
                opened.setApplying(false);
                if (desk.zone() != opened || editor != opened) {
                    Attributes.set(target, "outcome", OutcomeText.of(completed)); return;
                }
                zoneRoot.setDisable(false);
                note(OutcomeText.severity(completed.outcome()), OutcomeText.of(completed));
                refreshLive();
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
