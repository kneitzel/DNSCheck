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

    private final Desk desk;
    private final ZoneFiles zoneFiles;
    private ViewModel viewModel;
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

    private void run(boolean dryRun) {
        if (busy) {
            viewModel.addMessage(new Message(
                    Message.Severity.WARN, "A reconciliation run is already going.", "reconcile"));
            return;
        }
        if (!write()) {
            return;
        }
        busy = true;
        ZoneEditor editor = desk.zone();
        ViewModel target = viewModel;
        Attributes.set(target, "outcome", dryRun ? "Dry-run…" : "Reconciling…");
        Path zones = Path.of(desk.catalog().getZonesDirectory());
        Path keys = Path.of(desk.catalog().getKeysDirectory());
        Thread.startVirtualThread(() -> {
            ZoneReconciliationResult result = reconcile(zones, keys, editor, dryRun);
            Platform.runLater(() -> finish(editor, target, result));
        });
    }

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
