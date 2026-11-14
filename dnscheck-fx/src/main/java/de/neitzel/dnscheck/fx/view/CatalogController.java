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

    /**
     * The desk that holds the catalog Model and the open zone.
     */
    private final Desk desk;

    /**
     * Catalog access: load, save, and reconcile zone files.
     */
    private final ZoneFiles zoneFiles;

    /**
     * The catalog ViewModel, set before the bindings run.
     */
    private ViewModel viewModel;

    /**
     * Whether a reconciliation run is already going.
     */
    private boolean busy;

    /**
     * The pane that holds the open zone View, or a prompt when none is open.
     */
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
            Desk.CatalogDirectories directories = desk.requestedDirectories();
            List<CatalogEntryModel> loaded = zoneFiles.load(directories.zones());
            desk.catalogLoaded(directories);
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
            var editor = zoneFiles.open(desk.requireLoadedDirectories().zones(), selected);
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

    /**
     * Dry-runs or reconciles the selected catalog entry from the file on disk.
     *
     * @param dryRun {@code true} to report drift without sending {@code nsupdate}
     */
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
        Desk.CatalogDirectories directories;
        try {
            directories = desk.requireLoadedDirectories();
        } catch (RuntimeException failure) {
            note(Message.Severity.ERROR, text(failure), "reconcile");
            return;
        }
        busy = true;
        Attributes.set(viewModel, "outcome", dryRun ? "Dry-run…" : "Reconciling…");
        Path zones = directories.zones();
        Path keys = directories.keys();
        String owner = selected.getOwnerFolder();
        String zone = selected.getZone();
        Thread.startVirtualThread(() -> finish(reconcile(zones, keys, owner, zone, dryRun)));
    }

    /**
     * Reconciles one catalog entry, turning a failure into a failed zone outcome.
     *
     * @param zones   the zone catalog directory
     * @param keys    the keys directory
     * @param owner   the owner folder
     * @param zone    the zone name
     * @param dryRun  {@code true} to report drift without sending {@code nsupdate}
     * @return the zone outcome
     */
    private ZoneReconciliationResult reconcile(
            Path zones, Path keys, String owner, String zone, boolean dryRun) {
        try {
            return zoneFiles.reconcile(zones, keys, owner, zone, dryRun);
        } catch (RuntimeException failure) {
            return ZoneReconciliationResult.failed(owner + "/" + zone, text(failure));
        }
    }

    /**
     * Writes the zone outcome onto the catalog View on the JavaFX thread.
     *
     * @param result the zone outcome
     */
    private void finish(ZoneReconciliationResult result) {
        Platform.runLater(() -> {
            busy = false;
            show(viewModel, result);
        });
    }

    /**
     * Writes a short description of the selected catalog entry into the outcome text.
     */
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

    /**
     * Returns the catalog entries the table watches.
     *
     * @return the entries
     */
    @SuppressWarnings("unchecked")
    private List<CatalogEntryModel> entries() {
        return (List<CatalogEntryModel>) viewModel.property("entries").getValue();
    }

    /**
     * Records a Message and the same text as the outcome.
     *
     * @param severity how serious the notice is
     * @param text     the notice text
     * @param source   where the notice came from
     */
    private void note(Message.Severity severity, String text, String source) {
        viewModel.addMessage(new Message(severity, text, source));
        Attributes.set(viewModel, "outcome", text);
    }

    /**
     * Writes a zone outcome onto a ViewModel as text and as a Message.
     *
     * @param target the ViewModel that shows the outcome
     * @param result the zone outcome
     */
    private static void show(ViewModel target, ZoneReconciliationResult result) {
        Attributes.set(target, "outcome", OutcomeText.of(result));
        target.addMessage(new Message(
                OutcomeText.severity(result.outcome()),
                result.zoneName() + ": " + result.outcome(),
                "reconcile"));
    }

    /**
     * Returns the exception message, or a fallback when it is missing.
     *
     * @param failure the failure
     * @return the text
     */
    private static String text(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "Could not read the zone catalog.";
        }
        return message;
    }
}
