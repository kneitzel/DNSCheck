package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.fx.Attributes;
import de.neitzel.dnscheck.fx.Desk;
import de.neitzel.dnscheck.fx.OutcomeText;
import de.neitzel.dnscheck.fx.ZoneFiles;
import de.neitzel.dnscheck.fx.model.CatalogEntryModel;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.Message;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Controller for the zone catalog.
 */
@Singleton
public class CatalogController implements HasViewModel {

    private final Desk desk;
    private final ZoneFiles zoneFiles;
    private ViewModel viewModel;
    private boolean busy;

    @FXML
    private VBox editorHost;

    /**
     * Creates the controller.
     *
     * @param desk      the desk
     * @param zoneFiles catalog access
     */
    @Inject
    public CatalogController(Desk desk, ZoneFiles zoneFiles) {
        this.desk = desk;
        this.zoneFiles = zoneFiles;
    }

    /**
     * Receives the catalog ViewModel and loads the catalog.
     *
     * @param viewModel the catalog ViewModel
     */
    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
        desk.catalogView(viewModel);
        viewModel.property("selected").addListener((observable, oldValue, newValue) -> describeSelection());
        reload();
    }

    /**
     * Reads the zone catalog directory again.
     */
    @FXML
    public void reload() {
        try {
            List<CatalogEntryModel> loaded = zoneFiles.load(Path.of(desk.catalog().getZonesDirectory()));
            entries().clear();
            entries().addAll(loaded);
            Attributes.set(viewModel, "selected", null);
            desk.edit(null);
            editorHost.getChildren().setAll(new Label("Open a catalog entry to edit its zone definition."));
            Attributes.set(viewModel, "outcome", loaded.isEmpty() ? "No catalog entries." : "");
        } catch (RuntimeException | IOException failure) {
            note(Message.Severity.ERROR, text(failure), "catalog");
        }
    }

    /**
     * Loads the selected zone definition into the editor.
     */
    @FXML
    public void open() {
        CatalogEntryModel selected = desk.catalog().getSelected();
        if (selected == null) {
            note(Message.Severity.WARN, "Select a catalog entry.", "catalog");
            return;
        }
        if (!selected.isValid()) {
            note(Message.Severity.ERROR, selected.getCatalogRef() + " is invalid: " + selected.getError(), "catalog");
            return;
        }
        try {
            var editor = zoneFiles.open(Path.of(desk.catalog().getZonesDirectory()), selected);
            desk.edit(editor);
            LoadedView zoneView = ViewLoader.load(
                    ZoneController.class.getResource("Zone.fxml"), editor, desk.context());
            editorHost.getChildren().setAll(zoneView.getRoot());
        } catch (RuntimeException | IOException failure) {
            note(Message.Severity.ERROR, text(failure), "catalog");
        }
    }

    /**
     * Dry-runs the selected catalog entry from the file on disk.
     */
    @FXML
    public void dryRun() {
        run(true);
    }

    /**
     * Reconciles the selected catalog entry from the file on disk.
     */
    @FXML
    public void reconcile() {
        run(false);
    }

    private void run(boolean dryRun) {
        CatalogEntryModel selected = desk.catalog().getSelected();
        if (selected == null || !selected.isValid()) {
            note(Message.Severity.WARN, "Select a valid catalog entry.", "reconcile");
            return;
        }
        if (busy) {
            note(Message.Severity.WARN, "A reconciliation run is already going.", "reconcile");
            return;
        }
        busy = true;
        Attributes.set(viewModel, "outcome", dryRun ? "Dry-run…" : "Reconciling…");
        Path zones = Path.of(desk.catalog().getZonesDirectory());
        Path keys = Path.of(desk.catalog().getKeysDirectory());
        String owner = selected.getOwnerFolder();
        String zone = selected.getZone();
        Thread.startVirtualThread(() -> finish(reconcile(zones, keys, owner, zone, dryRun)));
    }

    private ZoneReconciliationResult reconcile(
            Path zones, Path keys, String owner, String zone, boolean dryRun) {
        try {
            return zoneFiles.reconcile(zones, keys, owner, zone, dryRun);
        } catch (RuntimeException failure) {
            return ZoneReconciliationResult.failed(owner + "/" + zone, text(failure));
        }
    }

    private void finish(ZoneReconciliationResult result) {
        Platform.runLater(() -> {
            busy = false;
            show(viewModel, result);
        });
    }

    private void describeSelection() {
        CatalogEntryModel selected = desk.catalog().getSelected();
        if (selected == null) {
            return;
        }
        if (!selected.isValid()) {
            Attributes.set(viewModel, "outcome", selected.getCatalogRef() + " is invalid: " + selected.getError());
            return;
        }
        Attributes.set(viewModel, "outcome", selected.getCatalogRef()
                + "  nameserver " + selected.getNameserver()
                + "  key file " + selected.getKeyFile());
    }

    @SuppressWarnings("unchecked")
    private List<CatalogEntryModel> entries() {
        return (List<CatalogEntryModel>) viewModel.property("entries").getValue();
    }

    private void note(Message.Severity severity, String text, String source) {
        viewModel.addMessage(new Message(severity, text, source));
        Attributes.set(viewModel, "outcome", text);
    }

    private static void show(ViewModel target, ZoneReconciliationResult result) {
        Attributes.set(target, "outcome", OutcomeText.of(result));
        target.addMessage(new Message(
                OutcomeText.severity(result.outcome()),
                result.zoneName() + ": " + result.outcome(),
                "reconcile"));
    }

    private static String text(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "Could not read the zone catalog.";
        }
        return message;
    }
}
