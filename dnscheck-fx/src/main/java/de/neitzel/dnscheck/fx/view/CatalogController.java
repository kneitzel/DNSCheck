package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.fx.Attributes;
import de.neitzel.dnscheck.fx.Desk;
import de.neitzel.dnscheck.fx.UnsavedChanges;
import de.neitzel.dnscheck.fx.ZoneFiles;
import de.neitzel.dnscheck.fx.RecentDirectories;
import de.neitzel.dnscheck.fx.model.CatalogEntryModel;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.Message;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.skin.SplitPaneSkin;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Opens local directories and switches between their catalog and individual zone details. */
@Singleton
public class CatalogController implements HasViewModel {
    private final Desk desk;
    private final ZoneFiles zoneFiles;
    private final RecentDirectories recentDirectories;
    private ViewModel viewModel;
    private TreeItem<String> activeItem;
    private boolean changingSelection;

    @FXML private SplitPane catalogSplit;
    @FXML private TreeView<String> domainsTree;
    @FXML private de.neitzel.fx.view.Table entriesTable;
    @FXML private VBox editorHost;
    @FXML private Menu recentMenu;

    @Inject
    public CatalogController(Desk desk, ZoneFiles zoneFiles, RecentDirectories recentDirectories) {
        this.desk = desk;
        this.zoneFiles = zoneFiles;
        this.recentDirectories = recentDirectories;
    }

    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
        desk.catalogView(viewModel);
        // ViewLoader discovers tables through scene children, so expose SplitPane items before its traversal.
        catalogSplit.setSkin(new SplitPaneSkin(catalogSplit));
        domainsTree.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> navigate(selected));
        replaceTree();
        reload();
        refreshRecent();
    }

    /** File → Open loads the chosen directory without a second Reload action. */
    @FXML
    public void browse() {
        Path selected = chooseDirectory();
        if (selected != null) openDirectory(selected);
    }

    /** Native chooser boundary; a cancelled chooser leaves the current directory intact. */
    protected Path chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open DNSCheck directory");
        try {
            var current = desk.requestedDirectory().toFile();
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        } catch (RuntimeException ignored) {
            // A chooser can recover from an invalid initial directory.
        }
        var selected = chooser.showDialog(editorHost.getScene() == null ? null : editorHost.getScene().getWindow());
        return selected == null ? null : selected.toPath();
    }

    @FXML
    public void reload() {
        try {
            openDirectory(desk.requestedDirectory());
        } catch (RuntimeException failure) {
            note(text(failure));
        }
    }

    /** Commit a directory, catalog and navigation together, only after loading and edit confirmation succeed. */
    public boolean openDirectory(Path requested) {
        try {
            Path directory = requested.toAbsolutePath().normalize();
            List<CatalogEntryModel> loaded = zoneFiles.load(directory);
            boolean hadEdits = desk.zone() != null && desk.zone().isDirty();
            if (!confirmChanges()) return false;
            if (hadEdits) loaded = zoneFiles.load(directory);
            Attributes.set(viewModel, "directory", directory.toString());
            desk.catalogLoaded(directory);
            entries().clear();
            entries().addAll(loaded);
            replaceTree();
            Attributes.set(viewModel, "outcome", "");
            try {
                recentDirectories.remember(directory);
            } catch (IOException | RuntimeException historyFailure) {
                note("Folder opened, but recent history could not be saved: " + text(historyFailure));
            }
            refreshRecent();
            return true;
        } catch (IOException | RuntimeException failure) {
            note(text(failure));
            return false;
        }
    }

    @FXML
    public void refreshRecent() {
        recentMenu.getItems().clear();
        try {
            for (Path directory : recentDirectories.existingDirectories()) {
                MenuItem item = new MenuItem(directory.toString());
                item.setMnemonicParsing(false);
                item.setOnAction(event -> {
                    openDirectory(directory);
                    refreshRecent();
                });
                recentMenu.getItems().add(item);
            }
            if (recentMenu.getItems().isEmpty()) recentPlaceholder("No recent folders");
        } catch (IOException | RuntimeException failure) {
            recentPlaceholder("Recent folders unavailable");
            note("Could not read recent folders: " + text(failure));
        }
    }

    private void recentPlaceholder(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        recentMenu.getItems().setAll(item);
    }

    private void replaceTree() {
        changingSelection = true;
        try {
            TreeItem<String> root = new TreeItem<>("Domains");
            for (CatalogEntryModel entry : entries()) root.getChildren().add(new TreeItem<>(entry.getZone()));
            root.setExpanded(true);
            domainsTree.setRoot(root);
            domainsTree.getSelectionModel().select(root);
            activeItem = root;
            showCatalog();
        } finally {
            changingSelection = false;
        }
    }

    private void navigate(TreeItem<String> selected) {
        if (changingSelection || selected == null || selected == activeItem) return;
        changingSelection = true;
        try {
            if (!confirmChanges()) {
                domainsTree.getSelectionModel().select(activeItem);
                return;
            }
            if (selected == domainsTree.getRoot()) {
                showCatalog();
            } else {
                Path directory = desk.requireLoadedDirectory();
                CatalogEntryModel entry = entries().stream()
                        .filter(candidate -> candidate.getZone().equals(selected.getValue()))
                        .findFirst().orElseThrow();
                if (entry.isValid()) {
                    var editor = zoneFiles.open(directory, entry);
                    var previous = desk.zone();
                    desk.edit(editor);
                    LoadedView view;
                    try {
                        view = ViewLoader.load(ZoneController.class.getResource("Zone.fxml"), editor, desk.context());
                    } catch (RuntimeException failure) {
                        desk.edit(previous);
                        throw failure;
                    }
                    editorHost.getChildren().setAll(view.getRoot());
                } else {
                    desk.edit(null);
                    Label error = new Label(entry.getZone() + " is invalid: " + entry.getError());
                    error.setWrapText(true);
                    editorHost.getChildren().setAll(error);
                }
                Attributes.set(viewModel, "selected", entry);
                entriesTable.setVisible(false);
                entriesTable.setManaged(false);
                editorHost.setVisible(true);
                editorHost.setManaged(true);
            }
            activeItem = selected;
            Attributes.set(viewModel, "outcome", "");
        } catch (IOException | RuntimeException failure) {
            domainsTree.getSelectionModel().select(activeItem);
            note(text(failure));
        } finally {
            changingSelection = false;
        }
    }

    private void showCatalog() {
        desk.edit(null);
        Attributes.set(viewModel, "selected", null);
        editorHost.getChildren().clear();
        editorHost.setVisible(false);
        editorHost.setManaged(false);
        entriesTable.setVisible(true);
        entriesTable.setManaged(true);
    }

    /** A table row can also be opened by double-clicking it. */
    @FXML
    public void tableClicked(MouseEvent event) {
        if (event.getClickCount() == 2) open();
    }

    public void open() {
        CatalogEntryModel selected = desk.catalog().getSelected();
        if (selected == null) return;
        domainsTree.getRoot().getChildren().stream()
                .filter(item -> item.getValue().equals(selected.getZone()))
                .findFirst().ifPresent(item -> domainsTree.getSelectionModel().select(item));
    }

    private boolean confirmChanges() {
        if (desk.zone() != null && desk.zone().isApplying()) {
            note("Wait for the current record change to finish.");
            return false;
        }
        return UnsavedChanges.confirm(desk.zone(), zoneFiles,
                editorHost.getScene() == null ? null : editorHost.getScene().getWindow());
    }

    @SuppressWarnings("unchecked")
    private List<CatalogEntryModel> entries() {
        return (List<CatalogEntryModel>) viewModel.property("entries").getValue();
    }

    private void note(String text) {
        viewModel.addMessage(new Message(Message.Severity.ERROR, text, "catalog"));
        Attributes.set(viewModel, "outcome", text);
    }

    private static String text(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "Could not read the zone catalog." : message;
    }
}
