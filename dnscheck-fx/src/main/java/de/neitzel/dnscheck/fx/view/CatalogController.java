package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.fx.Attributes;
import de.neitzel.dnscheck.fx.Desk;
import de.neitzel.dnscheck.fx.DnsCheckFxApp;
import de.neitzel.injection.ApplicationContext;
import de.neitzel.dnscheck.fx.FolderLayout;
import de.neitzel.dnscheck.fx.LocalFileImport;
import de.neitzel.dnscheck.fx.RecentDirectories;
import de.neitzel.dnscheck.fx.UnsavedChanges;
import de.neitzel.dnscheck.fx.ZoneFiles;
import de.neitzel.dnscheck.fx.model.CatalogEntryModel;
import de.neitzel.fx.docking.*;
import de.neitzel.fx.view.HasViewModel;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.fx.view.ViewModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class CatalogController implements HasViewModel {
    private static final DockTarget EDITORS = new DockTarget.Area(new DockAreaId("editors"));
    private final Desk desk;
    private final ZoneFiles zoneFiles;
    private final RecentDirectories recentDirectories;
    private final Map<DockableId, ZoneController> editors = new LinkedHashMap<>();
    private final FolderLayout layouts = new FolderLayout();
    private final LocalFileImport imports = new LocalFileImport();
    private final PauseTransition saveDelay = new PauseTransition(Duration.millis(250));
    private ViewModel viewModel;
    private ApplicationContext tabContext;
    private Path workingFolder;
    private boolean installingLayout;
    private Path pendingFolder;
    private List<CatalogEntryModel> pendingEntries;
    private String status = "";
    private String actionStatus = "";
    private String layoutFailure = "";
    private DockingManager docking;
    private Stage stage;
    private de.neitzel.dnscheck.fx.DnsThemes themes;

    @FXML private SplitPane catalogSplit;
    @FXML private ListView<CatalogEntryModel> zonesList;
    @FXML private ListView<String> keysList;
    @FXML private Label zonesHeading;
    @FXML private Label keysHeading;
    @FXML private DockPane editorHost;
    @FXML private Menu recentMenu;
    @FXML private Menu themeMenu;
    @FXML private Label themeMessage;
    @FXML private Button newZoneButton;
    @FXML private Button addKeyButton;
    @FXML private Label statusLabel;

    @Inject
    public CatalogController(Desk desk, ZoneFiles zoneFiles, RecentDirectories recentDirectories) {
        this.desk = desk;
        this.zoneFiles = zoneFiles;
        this.recentDirectories = recentDirectories;
    }

    @Override
    public void setViewModel(ViewModel viewModel) {
        this.viewModel = viewModel;
        sizeAddButton(newZoneButton, zonesHeading);
        sizeAddButton(addKeyButton, keysHeading);
        zonesList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(CatalogEntryModel entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null ? null : entry.getZone()
                        + (entry.isValid() ? "" : "\n" + entry.getError()));
                setWrapText(true);
                setOnMouseClicked(empty || entry == null ? null : event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) open(entry);
                });
            }
        });
        zonesList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) open();
        });
        refreshRecent();
        acceptFileDrops(catalogSplit.getParent() == null ? catalogSplit : catalogSplit.getParent());
        catalogSplit.getDividers().getFirst().positionProperty().addListener(observable -> scheduleSave());
        saveDelay.setOnFinished(event -> {
            if (stage.isShowing()) saveLayout(docking.snapshot());
        });
    }

    private static void sizeAddButton(Button button, Label heading) {
        button.minHeightProperty().bind(heading.heightProperty());
        button.prefHeightProperty().bind(heading.heightProperty());
        button.maxHeightProperty().bind(heading.heightProperty());
        button.minWidthProperty().bind(heading.heightProperty());
        button.prefWidthProperty().bind(heading.heightProperty());
        button.maxWidthProperty().bind(heading.heightProperty());
    }

    public void start(Stage stage) {
        this.stage = stage;
        themes = new de.neitzel.dnscheck.fx.DnsThemes(recentDirectories);
        themes.install(stage.getScene(), themeMenu);
        themeMessage.textProperty().bind(themes.messageProperty());
        themeMessage.managedProperty().bind(themeMessage.textProperty().isNotEmpty());
        themeMessage.visibleProperty().bind(themeMessage.managedProperty());
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> themes.close());
        tabContext = new ApplicationContext(DnsCheckFxApp.class) {
            @Override public <T> T getComponent(Class<? extends T> type) {
                if (type == ZoneController.class) return type.cast(new ZoneController(desk, zoneFiles));
                return desk.context().getComponent(type);
            }
        };
        stage.xProperty().addListener(observable -> scheduleSave());
        stage.yProperty().addListener(observable -> scheduleSave());
        stage.widthProperty().addListener(observable -> scheduleSave());
        stage.heightProperty().addListener(observable -> scheduleSave());
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> saveDelay.stop());
        attach();
        String initial = desk.catalog().getDirectory();
        if (!initial.isBlank() && openDirectory(Path.of(initial))) return;
        Attributes.set(viewModel, "directory", "");
        Platform.runLater(this::browse);
    }

    private void attach() {
        DockLayout layout = new DockLayout();
        layout.setCenter(new DockGroupDefinition("editors"));
        editorHost.setInitialLayout(layout);
        DockingOptions options = new DockingOptions();
        options.setFallbackTarget(EDITORS);
        options.setFloatingWindowCustomizer(window -> window.setTitle("DNSCheck"));
        options.setBeforeShutdown(request -> {
            if (request.reason() == ShutdownReason.MANAGER_ONLY && pendingFolder != null) {
                try {
                    pendingEntries = zoneFiles.load(pendingFolder);
                } catch (IOException | RuntimeException failure) {
                    note("Could not open " + pendingFolder + ": " + text(failure));
                    return CompletableFuture.completedFuture(ShutdownDecision.CANCEL);
                }
            }
            saveDelay.stop();
            saveLayout(request.snapshot());
            return CompletableFuture.completedFuture(ShutdownDecision.PROCEED);
        });
        docking = DockingManager.attach(stage, editorHost, options);
        docking.activeDockableProperty().addListener((observable, previous, active) -> {
            ZoneController editor = active == null ? null : editors.get(active.getId());
            desk.edit(editor == null ? null : editor.editor());
            if (editor != null) editor.activated();
        });
        docking.layoutProperty().addListener((observable, previous, current) -> {
            if (docking.closingScopeProperty().get() == null) showVisibleEditors();
            scheduleSave();
        });
        docking.closingScopeProperty().addListener((observable, previous, current) -> {
            editors.forEach((id, editor) -> editor.setClosing(current != null && current.dockableIds().contains(id)));
            if (current == null && previous != null
                    && (previous.kind() == CloseScope.Kind.TAB || previous.kind() == CloseScope.Kind.FLOATING_WINDOW)) {
                showVisibleEditors();
                scheduleSave();
            }
        });
    }

    private void scheduleSave() {
        if (!installingLayout && workingFolder != null && docking != null && stage.isShowing()
                && docking.closingScopeProperty().get() == null) saveDelay.playFromStart();
    }

    private void saveLayout(LayoutSnapshot snapshot) {
        if (workingFolder == null) return;
        try {
            layouts.write(workingFolder, new FolderLayout.Saved(snapshot,
                    catalogSplit.getItems().getFirst().getLayoutBounds().getWidth(),
                    new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())));
        } catch (IOException | RuntimeException failure) {
            layoutFailure = "Could not save the layout for " + workingFolder.getFileName() + ": " + text(failure);
            note(layoutFailure);
        }
    }

    private void restoreLayout() {
        installingLayout = true;
        try {
            catalogSplit.setDividerPosition(0, 0.22);
            var saved = layouts.read(workingFolder).orElse(null);
            if (saved == null || saved.layout() == null) return;
            LayoutSnapshot snapshot = saved.layout();
            RestoreResult result = docking.restore(snapshot, id -> desk.catalog().getEntries().stream()
                    .filter(entry -> entry.isValid() && entry.getZone().equals(id.value()))
                    .findFirst().<DockableResolution>map(entry -> {
                        try {
                            return new DockableResolution.Available(createDockable(id, entry));
                        } catch (RuntimeException missing) {
                            return new DockableResolution.Missing();
                        }
                    }).orElseGet(DockableResolution.Missing::new));
            if (result instanceof RestoreResult.Applied) {
                restoreMainWindow(saved.mainWindow());
                if (Double.isFinite(saved.sidebarWidth()) && saved.sidebarWidth() > 0 && stage.getWidth() > 0) {
                    catalogSplit.setDividerPosition(0, Math.min(0.85, saved.sidebarWidth() / stage.getWidth()));
                }
                showVisibleEditors();
            }
        } catch (IOException | RuntimeException unavailable) {
            // Layout is best effort. The attached manager already has the default arrangement.
        } finally {
            installingLayout = false;
        }
    }

    private void restoreMainWindow(Rectangle2D bounds) {
        if (bounds == null || !Double.isFinite(bounds.getMinX()) || !Double.isFinite(bounds.getMinY())
                || !Double.isFinite(bounds.getWidth()) || !Double.isFinite(bounds.getHeight())
                || bounds.getWidth() < 320 || bounds.getHeight() < 240) return;
        var screens = Screen.getScreensForRectangle(bounds);
        if (screens.isEmpty()) return;
        Rectangle2D visible = screens.getFirst().getVisualBounds();
        double width = Math.min(bounds.getWidth(), visible.getWidth());
        double height = Math.min(bounds.getHeight(), visible.getHeight());
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(Math.clamp(bounds.getMinX(), visible.getMinX(), visible.getMaxX() - width));
        stage.setY(Math.clamp(bounds.getMinY(), visible.getMinY(), visible.getMaxY() - height));
    }

    private void showVisibleEditors() {
        for (var window : docking.snapshot().windows()) {
            for (var root : window.roots()) activateVisible(root.branch());
        }
    }

    private void activateVisible(LayoutSnapshot.Branch branch) {
        if (branch instanceof LayoutSnapshot.Split split) {
            activateVisible(split.first());
            activateVisible(split.second());
        } else if (branch instanceof LayoutSnapshot.Group group && !group.collapsed()) {
            ZoneController editor = editors.get(group.selected());
            if (editor != null) editor.activated();
        }
    }

    @FXML public void browse() {
        Path selected = chooseDirectory();
        if (selected != null) openDirectory(selected);
    }

    protected Path chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open DNSCheck directory");
        if (!desk.catalog().getDirectory().isBlank()) {
            var current = desk.requestedDirectory().toFile();
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        }
        var selected = chooser.showDialog(stage);
        return selected == null ? null : selected.toPath();
    }

    @FXML public void reload() {
        if (desk.catalog().getDirectory().isBlank()) return;
        try {
            List<CatalogEntryModel> loaded = zoneFiles.load(desk.requireLoadedDirectory());
            refreshCatalog(loaded);
            List<String> keys = imports.keyFiles(workingFolder);
            for (ZoneController editor : editors.values()) {
                editor.refreshKeys(keys);
                editor.reload(false);
                loaded.stream().filter(entry -> !entry.isValid() && entry.getZone().equals(editor.editor().getZone()))
                        .findFirst().ifPresent(entry -> editor.reportError(entry.getError()));
            }
        } catch (IOException | RuntimeException failure) {
            note(text(failure));
        }
    }

    public boolean openDirectory(Path requested) {
        if (pendingFolder != null) return false;
        try {
            Path directory = requested.toRealPath();
            List<CatalogEntryModel> loaded = zoneFiles.load(directory);
            if (directory.equals(workingFolder)) {
                reload();
                return true;
            }
            if (workingFolder == null) {
                useDirectory(directory, loaded);
            } else {
                pendingFolder = directory;
                docking.requestManagerClose().thenAccept(result -> {
                    List<CatalogEntryModel> refreshed = pendingEntries;
                    pendingFolder = null;
                    pendingEntries = null;
                    if (result instanceof CloseResult.Closed && refreshed != null && stage.isShowing()) {
                        attach();
                        useDirectory(directory, refreshed);
                    } else {
                        scheduleSave();
                    }
                });
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            note(text(failure));
            return false;
        }
    }

    private void useDirectory(Path directory, List<CatalogEntryModel> loaded) {
        workingFolder = directory;
        Attributes.set(viewModel, "directory", directory.toString());
        desk.catalogLoaded(directory);
        refreshCatalog(loaded);
        newZoneButton.setDisable(false);
        addKeyButton.setDisable(false);
        note(layoutFailure);
        layoutFailure = "";
        restoreLayout();
        try {
            recentDirectories.remember(directory);
        } catch (IOException | RuntimeException failure) {
            note("Folder opened, but recent history could not be saved: " + text(failure));
        }
        refreshRecent();
    }

    private void refreshCatalog(List<CatalogEntryModel> entries) {
        desk.catalog().setEntries(entries);
        zonesList.getItems().setAll(entries);
        try {
            keysList.getItems().setAll(imports.keyFiles(workingFolder));
        } catch (IOException | RuntimeException failure) {
            keysList.getItems().clear();
            note("Could not list keys: " + text(failure));
        }
    }

    @FXML public void refreshRecent() {
        recentMenu.getItems().clear();
        try {
            for (Path directory : recentDirectories.existingDirectories()) {
                MenuItem item = new MenuItem(directory.toString());
                item.setMnemonicParsing(false);
                item.setOnAction(event -> openDirectory(directory));
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

    public void open() {
        var selected = zonesList.getSelectionModel().getSelectedItem();
        if (selected != null) open(selected);
    }

    private void open(CatalogEntryModel entry) {
        if (!entry.isValid()) {
            note(entry.getZone() + ": " + entry.getError());
            return;
        }
        DockableId id = new DockableId(entry.getZone());
        ActionResult result = editors.containsKey(id) ? docking.activate(id)
                : docking.open(id, () -> createDockable(id, entry), EDITORS);
        if (result instanceof ActionResult.Rejected rejected) {
            var failure = rejected.failure();
            note(failure.cause() == null ? docking.describeFailure(failure) : "Could not open " + entry.getZone()
                    + ": " + failure.cause().getMessage());
        } else {
            showVisibleEditors();
        }
    }

    private Dockable createDockable(DockableId id, CatalogEntryModel entry) {
        var previous = desk.zone();
        try {
            var model = zoneFiles.open(desk.requireLoadedDirectory(), entry);
            desk.edit(model);
            var view = ViewLoader.load(ZoneController.class.getResource("Zone.fxml"), model, tabContext);
            var editor = (ZoneController) view.getRoot().getUserData();
            editor.setStatus(this::showAction);
            editor.refreshKeys(imports.keyFiles(workingFolder));
            acceptFileDrops(view.getRoot());
            var dockable = new Dockable(id, view.getRoot());
            dockable.setTitle(entry.getZone());
            editors.put(id, editor);
            dockable.setCloseCheck(request -> {
                var pending = editor.editor().isApplying() ? editor.pendingOperation()
                        : CompletableFuture.completedFuture(true);
                return pending.thenApply(success -> success && UnsavedChanges.confirm(model, zoneFiles, request.owner())
                        ? CloseConsent.ALLOW : CloseConsent.VETO);
            });
            Runnable remove = () -> {
                editors.remove(id, editor);
                editor.disposed();
            };
            dockable.setOnClosed(remove);
            dockable.setOnDiscarded(remove);
            return dockable;
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        } finally {
            desk.edit(previous);
        }
    }

    @FXML public void newZone() {
        if (workingFolder == null) return;
        try {
            String zone = new FolderDialogs(stage, imports).createZone(workingFolder);
            if (zone != null) {
                refreshCatalog(zoneFiles.load(workingFolder));
                desk.catalog().getEntries().stream().filter(entry -> entry.getZone().equals(zone))
                        .findFirst().ifPresent(this::open);
            }
        } catch (IOException | RuntimeException failure) {
            note(text(failure));
        }
    }

    @FXML public void addKey() {
        if (workingFolder != null) new FolderDialogs(stage, imports).addKey(workingFolder, this::install);
    }

    private void acceptFileDrops(Node node) {
        node.setOnDragOver(event -> {
            if (workingFolder != null && event.getDragboard().hasFiles()
                    && event.getDragboard().getFiles().stream().map(java.io.File::toPath).anyMatch(LocalFileImport::supports)) {
                event.acceptTransferModes(TransferMode.COPY);
                event.consume();
            }
        });
        node.setOnDragDropped(event -> {
            if (workingFolder == null || !event.getDragboard().hasFiles()) return;
            List<Path> dropped = event.getDragboard().getFiles().stream().map(java.io.File::toPath)
                    .filter(LocalFileImport::supports).toList();
            if (dropped.isEmpty()) return;
            event.setDropCompleted(true);
            event.consume();
            Path directory = workingFolder;
            var owner = node.getScene().getWindow();
            Platform.runLater(() -> {
                if (!directory.equals(workingFolder)) return;
                for (Path file : dropped) {
                    try {
                        install(imports.droppedFile(directory, file), owner);
                    } catch (IOException | RuntimeException failure) {
                        note(text(failure));
                    }
                }
            });
        });
    }

    private boolean install(LocalFileImport.Pending pending, javafx.stage.Window owner) throws IOException {
        String filename = pending.target().getFileName().toString();
        boolean replace = pending.exists();
        boolean key = pending.kind() == LocalFileImport.Kind.KEY;
        List<ZoneController> affected = editors.values().stream().filter(editor -> key
                ? filename.equals(editor.editor().getDiskDefinition().keyFile())
                : filename.equals(Path.of(editor.editor().getSourcePath()).getFileName().toString())).toList();
        var busy = affected.stream().filter(editor -> editor.editor().isApplying())
                .map(editor -> editor.pendingOperation().toCompletableFuture()).toList();
        if (!busy.isEmpty()) {
            CompletableFuture.allOf(busy.toArray(CompletableFuture[]::new)).thenRun(() -> Platform.runLater(() -> {
                if (!pending.target().getParent().equals(workingFolder) || !stage.isShowing() || pendingFolder != null) return;
                try {
                    install(pending, owner != null && owner.isShowing() ? owner : stage);
                } catch (IOException | RuntimeException failure) {
                    note(text(failure));
                }
            }));
            return true;
        }
        if (replace) {
            StringBuilder text = new StringBuilder("Replace ").append(filename).append('?');
            for (ZoneController editor : affected) {
                text.append("\n").append(editor.editor().getZone());
                if (!key && editor.editor().isDirty()) text.append(" — pending edits will be discarded.");
            }
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text.toString(), ButtonType.OK, ButtonType.CANCEL);
            alert.initOwner(owner);
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        }
        installNow(pending, replace, affected);
        return true;
    }

    private void installNow(LocalFileImport.Pending pending, boolean replace, List<ZoneController> affected)
            throws IOException {
        Path directory = pending.target().getParent();
        if (!directory.equals(workingFolder)) return;
        pending.install(replace);
        refreshCatalog(zoneFiles.load(directory));
        if (pending.kind() == LocalFileImport.Kind.KEY) {
            List<String> keys = imports.keyFiles(directory);
            editors.values().forEach(editor -> editor.refreshKeys(keys));
            affected.stream().filter(editors::containsValue).forEach(ZoneController::refreshUsingSavedSettings);
        } else {
            affected.stream().filter(editors::containsValue).forEach(editor -> editor.reload(true));
        }
    }

    private void note(String text) {
        status = text;
        renderStatus();
    }

    private void showAction(String text) {
        actionStatus = text;
        renderStatus();
    }

    private void renderStatus() {
        statusLabel.setText(actionStatus.isEmpty() ? status : actionStatus);
    }

    private static String text(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "Could not read the zone catalog." : message;
    }
}
