package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.fx.Attributes;
import de.neitzel.dnscheck.fx.Desk;
import de.neitzel.dnscheck.fx.OutcomeText;
import de.neitzel.dnscheck.fx.ZoneFiles;
import de.neitzel.dnscheck.fx.model.RecordEditor;
import de.neitzel.dnscheck.fx.model.ZoneEditor;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.Message;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.fxml.FXML;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Controller for one open zone definition.
 */
@Singleton
public class ZoneController implements HasViewModel {

    /**
     * The desk that holds the open zone.
     */
    private final Desk desk;

    /**
     * Catalog access used to save and reconcile the open zone.
     */
    private final ZoneFiles zoneFiles;

    /**
     * The zone ViewModel, set before the bindings run.
     */
    private ViewModel viewModel;

    /**
     * Whether a reconciliation run is already going.
     */
    private boolean busy;

    /**
     * Creates the controller.
     *
     * @param desk      the desk
     * @param zoneFiles catalog access
     */
    @Inject
    public ZoneController(Desk desk, ZoneFiles zoneFiles) {
        this.desk = desk;
        this.zoneFiles = zoneFiles;
    }

    /**
     * Receives the ViewModel for the zone that was just loaded.
     *
     * @param viewModel the zone ViewModel
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
    }

    /**
     * Appends an empty A record.
     */
    @FXML
    public void addRecord() {
        RecordEditor record = new RecordEditor();
        record.setType("A");
        desk.zone().getRecords().add(record);
    }

    /**
     * Writes the zone definition file.
     */
    @FXML
    public void save() {
        if (write()) {
            viewModel.addMessage(new Message(
                    Message.Severity.INFO, "Saved " + desk.zone().getZone() + ".", "save"));
        }
    }

    /**
     * Saves the zone definition, then dry-runs it.
     */
    @FXML
    public void dryRun() {
        run(true);
    }

    /**
     * Saves the zone definition, then reconciles it.
     */
    @FXML
    public void reconcile() {
        run(false);
    }

    /**
     * Saves the open zone, then dry-runs or reconciles it.
     *
     * @param dryRun {@code true} to report drift without sending {@code nsupdate}
     */
    private void run(boolean dryRun) {
        if (busy) {
            viewModel.addMessage(new Message(
                    Message.Severity.WARN, "A reconciliation run is already going.", "reconcile"));
            return;
        }
        Desk.CatalogDirectories directories;
        try {
            directories = desk.requireLoadedDirectories();
        } catch (RuntimeException failure) {
            viewModel.addMessage(new Message(Message.Severity.ERROR, failure.getMessage(), "reconcile"));
            return;
        }
        if (!write()) {
            return;
        }
        busy = true;
        ZoneEditor editor = desk.zone();
        ViewModel target = viewModel;
        Attributes.set(target, "outcome", dryRun ? "Dry-run…" : "Reconciling…");
        Path zones = directories.zones();
        Path keys = directories.keys();
        Thread.startVirtualThread(() -> {
            ZoneReconciliationResult result = reconcile(zones, keys, editor, dryRun);
            Platform.runLater(() -> finish(editor, target, result));
        });
    }

    /**
     * Reconciles the open zone, turning a failure into a failed zone outcome.
     *
     * @param zones   the zone catalog directory
     * @param keys    the keys directory
     * @param editor  the zone being reconciled
     * @param dryRun  {@code true} to report drift without sending {@code nsupdate}
     * @return the zone outcome
     */
    private ZoneReconciliationResult reconcile(Path zones, Path keys, ZoneEditor editor, boolean dryRun) {
        try {
            return zoneFiles.reconcile(zones, keys, editor.getOwnerFolder(), editor.getZone(), dryRun);
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            return ZoneReconciliationResult.failed(
                    editor.getOwnerFolder() + "/" + editor.getZone(),
                    message == null || message.isBlank() ? "Reconciliation failed." : message);
        }
    }

    /**
     * Writes the zone outcome on the JavaFX thread.
     * <p>
     * The open zone's ViewModel receives it when that zone is still open. Otherwise the catalog
     * ViewModel receives it.
     *
     * @param editor the zone that was reconciled
     * @param target the ViewModel of that zone
     * @param result the zone outcome
     */
    private void finish(ZoneEditor editor, ViewModel target, ZoneReconciliationResult result) {
        busy = false;
        ViewModel shown = desk.zone() == editor ? target : desk.catalogView();
        if (shown == null) {
            return;
        }
        Attributes.set(shown, "outcome", OutcomeText.of(result));
        shown.addMessage(new Message(
                OutcomeText.severity(result.outcome()),
                result.zoneName() + ": " + result.outcome(),
                "reconcile"));
    }

    /**
     * Writes the open zone definition to disk.
     *
     * @return {@code true} when the file was written
     */
    private boolean write() {
        try {
            zoneFiles.save(desk.zone());
            return true;
        } catch (RuntimeException | IOException failure) {
            String message = failure.getMessage();
            viewModel.addMessage(new Message(
                    Message.Severity.ERROR,
                    message == null || message.isBlank() ? "Could not save the zone definition." : message,
                    "save"));
            return false;
        }
    }
}
