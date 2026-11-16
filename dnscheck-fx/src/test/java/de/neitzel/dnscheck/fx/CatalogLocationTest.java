package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.view.CatalogController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.AfterEach;
import javafx.stage.Stage;
import de.neitzel.dnscheck.fx.model.CatalogEntryModel;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real FXML navigation and menus, replacing only the native chooser and network boundaries. */
class CatalogLocationTest {
    @TempDir Path temp;

    @BeforeAll
    static void toolkit() throws Exception {
        Platform.setImplicitExit(false);
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @AfterEach void closeWindows() throws Exception {
        onFx(() -> {
            for (var window : List.copyOf(javafx.stage.Window.getWindows())) window.hide();
            return null;
        });
    }

    @Test
    void openingAnotherZonePreservesTheFirstEditorAndReopeningActivatesIt() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            assertEquals(List.of("example.com", "example.net"), f.zones());
            assertEquals(2, ((SplitPane) f.view().getRoot().lookup("#catalogSplit")).getItems().size());
            f.open(0);
            var first = f.desk().zone();
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            f.open(1);
            assertEquals("example.net", f.desk().zone().getZone());
            f.open(0);
            assertSame(first, f.desk().zone());
            assertEquals("edited.example.com", first.getNameserver());
            assertEquals(2, f.view().getRoot().lookupAll(".tab").size());
            return null;
        });
    }

    @Test
    void fileOpenLoadsImmediatelyAndFileReloadRescansTheCurrentDirectory() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.controller().choice = f.directoryB();
            menu(f, 0).fire();
            assertEquals(f.directoryB(), f.desk().requireLoadedDirectory());
            assertEquals("other.example", f.zones().getFirst());
            assertEquals(f.directoryB().toString(), ((Label) f.view().getRoot().lookup("#directoryLabel")).getText());
            zoneFile(f.directoryB(), "new.example", "ns-new.example");
            menu(f, 1).fire();
            assertEquals(List.of("new.example", "other.example"), f.zones());
            assertNull(f.list().getSelectionModel().getSelectedItem());
            assertNull(f.desk().zone());
            return null;
        });
    }

    @Test
    void cancelledChooserAndFailedOpenPreserveTheCurrentEditorAndDirectory() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.open(0);
            var editor = f.desk().zone();
            f.controller().choice = null;
            menu(f, 0).fire();
            assertSame(editor, f.desk().zone());
            f.controller().choice = temp.resolve("missing");
            menu(f, 0).fire();
            assertSame(editor, f.desk().zone());
            assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
            assertEquals("example.com", f.list().getSelectionModel().getSelectedItem().getZone());
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Save", "Discard", "Cancel"})
    void fileOpenProtectsUnsavedEditsAndCommitsTheDirectoryOnlyOnSuccess(String choice) throws Exception {
        Fixture f = fixture();
        String original = Files.readString(f.fileA());
        onFx(() -> {
            f.open(0);
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            Platform.runLater(() -> clickDialog(choice));
            f.controller().choice = f.directoryB();
            menu(f, 0).fire();
            if (choice.equals("Cancel")) {
                assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
                assertTrue(f.desk().zone().isDirty());
                assertEquals("example.com", f.list().getSelectionModel().getSelectedItem().getZone());
                assertEquals(List.of(f.directoryA().toRealPath()),
                        new RecentDirectories(temp.resolve("settings/config.json")).existingDirectories());
            } else {
                assertEquals(f.directoryB(), f.desk().requireLoadedDirectory());
                assertNull(f.desk().zone());
                assertNull(f.list().getSelectionModel().getSelectedItem());
            }
            return null;
        });
        if (choice.equals("Save")) assertTrue(Files.readString(f.fileA()).contains("edited.example.com"));
        else assertEquals(original, Files.readString(f.fileA()));
    }

    @Test
    void reloadRefreshesCleanEditorsAndPreservesConflictingEdits() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.open(0);
            var first = f.desk().zone();
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            f.open(1);
            var second = f.desk().zone();
            zoneFile(f.directoryA(), "example.com", "external.example.com");
            zoneFile(f.directoryA(), "example.net", "external.example.net");
            menu(f, 1).fire();
            assertEquals("external.example.net", second.getNameserver());
            f.open(0);
            assertSame(first, f.desk().zone());
            assertEquals("edited.example.com", first.getNameserver());
            assertThrows(java.io.IOException.class, () -> f.files().save(first));
            return null;
        });
    }

    @Test
    void returningToAFolderRestoresItsTabsAndActiveZone() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.open(0);
            f.open(1);
            assertEquals("example.net", f.desk().zone().getZone());
            assertTrue(f.controller().openDirectory(f.directoryB()));
            assertTrue(Files.exists(f.directoryA().resolve(FolderLayout.FILENAME)));
            assertNull(f.desk().zone());
            assertTrue(f.controller().openDirectory(f.directoryA()));
            f.view().getRoot().applyCss();
            f.view().getRoot().layout();
            assertEquals("example.net", f.desk().zone().getZone());
            assertEquals(2, f.view().getRoot().lookupAll(".tab").size());
            return null;
        });
    }

    @Test
    void pastedKeyAndNewZoneDialogsWorkInAnEmptyFolder() throws Exception {
        Fixture f = fixture();
        Path empty = Files.createDirectory(temp.resolve("empty")).toRealPath();
        onFx(() -> {
            assertTrue(f.controller().openDirectory(empty));
            assertTrue(f.zones().isEmpty());
            Platform.runLater(() -> {
                DialogPane dialog = activeDialog();
                ((TextArea) dialog.lookup("#bindKeyText")).setText("""
                        key "shared" { algorithm hmac-sha256; secret "dGVzdA=="; };
                        """);
                assertEquals("shared.key", ((TextField) dialog.lookup("#bindKeyFilename")).getText());
                ((Button) dialog.lookupButton(ButtonType.OK)).fire();
            });
            ((Button) f.view().getRoot().lookup("#addKeyButton")).fire();
            assertTrue(Files.isRegularFile(empty.resolve("shared.key")));

            Platform.runLater(() -> {
                DialogPane dialog = activeDialog();
                ((TextField) dialog.lookup("#newZoneName")).setText("first.example");
                ((TextField) dialog.lookup("#newZoneNameserver")).setText("ns.first.example");
                assertEquals("shared.key", ((ComboBox<?>) dialog.lookup("#newZoneKey")).getValue());
                ((Button) dialog.lookupButton(ButtonType.OK)).fire();
            });
            ((Button) f.view().getRoot().lookup("#newZoneButton")).fire();
            assertEquals(List.of("first.example"), f.zones());
            assertEquals("first.example", f.desk().zone().getZone());
            return null;
        });
    }

    private static DialogPane activeDialog() {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).findFirst().orElseThrow();
    }

    @Test
    void failedBusyOperationCancelsTheDeferredFolderSwitch() throws Exception {
        Fixture f = fixture();
        var result = new CompletableFuture<ZoneReconciliationResult>();
        f.files().deferredResult = result;
        onFx(() -> {
            f.open(1);
            f.open(0);
            ((Button) f.view().getRoot().lookup("#dryRunFileButton")).fire();
            assertTrue(f.desk().zone().isApplying());
            assertTrue(f.controller().openDirectory(f.directoryB()));
            assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
            f.open(1);
            assertEquals("example.net", f.desk().zone().getZone());
            return null;
        });
        assertNotNull(f.files().calls.poll(10, TimeUnit.SECONDS));
        result.complete(ZoneReconciliationResult.failed("example.com", "Offline test failure"));
        onFx(() -> {
            f.open(0);
            assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
            return null;
        });
        var completed = new CountDownLatch(1);
        onFx(() -> {
            var outcome = (TextArea) f.view().getRoot().lookup("#outcomeArea");
            if (outcome.getText().contains("Offline test failure")) completed.countDown();
            outcome.textProperty().addListener((observable, before, after) -> {
                if (after.contains("Offline test failure")) completed.countDown();
            });
            return null;
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
            assertFalse(f.desk().zone().isApplying());
            return null;
        });
    }

    @Test
    void aDestinationRemovedDuringADeferredSwitchLeavesTheOriginalTabsOpen() throws Exception {
        Fixture f = fixture();
        var operation = new CompletableFuture<ZoneReconciliationResult>();
        var rejected = new CountDownLatch(1);
        f.files().deferredResult = operation;
        onFx(() -> {
            f.open(0);
            var status = (Label) f.view().getRoot().lookup("#statusLabel");
            status.textProperty().addListener((observable, before, after) -> {
                if (after.startsWith("Could not open ")) rejected.countDown();
            });
            ((Button) f.view().getRoot().lookup("#dryRunFileButton")).fire();
            assertTrue(f.controller().openDirectory(f.directoryB()));
            return null;
        });
        assertNotNull(f.files().calls.poll(5, TimeUnit.SECONDS));
        Files.delete(f.directoryB().resolve("dnscheck.key"));
        Files.delete(f.directoryB().resolve("other.example.domain"));
        Files.delete(f.directoryB());
        operation.complete(ZoneReconciliationResult.success("example.com"));
        assertTrue(rejected.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
            assertEquals("example.com", f.desk().zone().getZone());
            return null;
        });
    }

    @Test
    void invalidDomainShowsItsErrorInsteadOfThePreviousDomainsDetails() throws Exception {
        Fixture f = fixture();
        Files.writeString(f.directoryA().resolve("bad.domain"), "invalid JSON");
        onFx(() -> {
            menu(f, 1).fire();
            f.open(1);
            assertNotNull(f.desk().zone());
            f.open(0);
            assertEquals("example.com", f.desk().zone().getZone());
            assertTrue(((Label) f.view().getRoot().lookup("#statusLabel")).getText().startsWith("bad:"));
            assertTrue(f.files().calls.isEmpty());
            return null;
        });
    }

    @Test
    void reconciliationUsesTheNewDirectoryAfterOpeningIt() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.controller().choice = f.directoryB();
            menu(f, 0).fire();
            f.open(0);
            ((Button) f.view().getRoot().lookup("#dryRunFileButton")).fire();
            return null;
        });
        Invocation call = f.files().calls.poll(10, TimeUnit.SECONDS);
        assertNotNull(call);
        assertEquals(f.directoryB(), call.directory());
        assertEquals("other.example", call.zone());
    }

    @Test
    void recentMenuReopensImmediatelyPromotesHistoryAndFiltersDeletedFoldersOnShowing() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.controller().choice = f.directoryB();
            menu(f, 0).fire();
            Menu recent = (Menu) menu(f, 2);
            assertEquals(List.of(f.directoryB().toRealPath().toString(), f.directoryA().toRealPath().toString()),
                    recent.getItems().stream().map(MenuItem::getText).toList());
            recent.getItems().get(1).fire();
            assertEquals(f.directoryA().toRealPath(), f.desk().requireLoadedDirectory());
            assertEquals("example.com", f.zones().getFirst());
            assertEquals(f.directoryA().toRealPath().toString(), recent.getItems().getFirst().getText());
            Files.delete(f.directoryB().resolve("other.example.domain"));
            Files.delete(f.directoryB().resolve("dnscheck.key"));
            Files.deleteIfExists(f.directoryB().resolve(FolderLayout.FILENAME));
            Files.delete(f.directoryB());
            javafx.event.Event.fireEvent(recent, new javafx.event.Event(Menu.ON_SHOWING));
            assertEquals(1, recent.getItems().size());
            assertFalse(recent.getItems().getFirst().isMnemonicParsing());
            assertEquals(List.of(f.directoryA().toRealPath()),
                    new RecentDirectories(temp.resolve("settings/config.json")).existingDirectories());
            return null;
        });
    }

    @Test
    void unreadableHistoryDoesNotPreventOpeningDirectoriesAndIsNotOverwritten() throws Exception {
        Fixture f = fixture();
        Path configuration = temp.resolve("settings/config.json");
        Files.writeString(configuration, "broken config");
        onFx(() -> {
            f.controller().choice = f.directoryB();
            menu(f, 0).fire();
            assertEquals(f.directoryB(), f.desk().requireLoadedDirectory());
            assertEquals("other.example", f.zones().getFirst());
            Menu recent = (Menu) menu(f, 2);
            assertTrue(recent.getItems().getFirst().isDisable());
            assertEquals("broken config", Files.readString(configuration));
            return null;
        });
    }

    private static MenuItem menu(Fixture f, int index) {
        return ((MenuBar) f.view().getRoot().lookup("#fileMenuBar")).getMenus().getFirst().getItems().get(index);
    }

    private static void clickDialog(String text) {
        for (javafx.stage.Window window : List.copyOf(javafx.stage.Window.getWindows())) {
            if (window.isShowing() && window.getScene().getRoot() instanceof DialogPane pane) {
                ButtonType choice = pane.getButtonTypes().stream().filter(button -> button.getText().equals(text)
                        || text.equals("Cancel") && button.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE)
                        .findFirst().orElseThrow();
                ((Button) pane.lookupButton(choice)).fire();
                return;
            }
        }
        fail("Expected unsaved changes dialog");
    }

    private Fixture fixture() throws Exception {
        Path directoryA = temp.toRealPath().resolve("a");
        Path directoryB = temp.toRealPath().resolve("b");
        Path fileA = zoneFile(directoryA, "example.com", "ns-a.example.com");
        zoneFile(directoryA, "example.net", "ns-a.example.net");
        zoneFile(directoryB, "other.example", "ns-b.example.com");
        return onFx(() -> {
            RecordingFiles files = new RecordingFiles();
            RecentDirectories recent = new RecentDirectories(temp.resolve("settings/config.json"));
            ApplicationContext context = new ApplicationContext(DnsCheckFxApp.class) {
                private ChoosingController controller;
                @Override
                public <T> T getComponent(Class<? extends T> type) {
                    if (type == ZoneFiles.class) return type.cast(files);
                    if (type == RecentDirectories.class) return type.cast(recent);
                    if (type == CatalogController.class) {
                        if (controller == null) controller = new ChoosingController(getComponent(Desk.class), files, recent);
                        return type.cast(controller);
                    }
                    return super.getComponent(type);
                }
            };
            Desk desk = context.getComponent(Desk.class);
            CatalogSession session = new CatalogSession();
            session.setDirectory(directoryA.toString());
            desk.prepare(session, context);
            LoadedView view = ViewLoader.load(CatalogController.class.getResource("Catalog.fxml"), session, context);
            Stage stage = new Stage();
            stage.setScene(new javafx.scene.Scene(view.getRoot(), 1200, 800));
            stage.show();
            context.getComponent(CatalogController.class).start(stage);
            view.getRoot().applyCss();
            view.getRoot().layout();
            return new Fixture(context, desk, view, (ChoosingController) context.getComponent(CatalogController.class),
                    files, directoryA, directoryB, fileA);
        });
    }

    private static Path zoneFile(Path directory, String zone, String server) throws Exception {
        Path file = Files.createDirectories(directory).resolve(zone + ".domain");
        Files.writeString(file, """
                {"zone":"%s", "nameserver":"%s", "keyFile":"dnscheck.key", "records":[]}
                """.formatted(zone, server));
        Files.writeString(directory.resolve("dnscheck.key"), """
                key "test" { algorithm hmac-sha256; secret "dGVzdA=="; };
                """);
        return file;
    }

    private static <T> T onFx(Callable<T> check) throws Exception {
        FutureTask<T> task = new FutureTask<>(check);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private record Fixture(ApplicationContext context, Desk desk, LoadedView view, ChoosingController controller,
                           RecordingFiles files, Path directoryA, Path directoryB, Path fileA) {
        @SuppressWarnings("unchecked")
        ListView<CatalogEntryModel> list() { return (ListView<CatalogEntryModel>) view.getRoot().lookup("#zonesList"); }
        List<String> zones() { return list().getItems().stream().map(CatalogEntryModel::getZone).toList(); }
        void open(int index) {
            list().getSelectionModel().select(index);
            controller.open();
            view.getRoot().applyCss();
            view.getRoot().layout();
        }
    }
    private record Invocation(Path directory, String zone) {}

    private static class ChoosingController extends CatalogController {
        Path choice;
        ChoosingController(Desk desk, ZoneFiles files, RecentDirectories recent) { super(desk, files, recent); }
        @Override protected Path chooseDirectory() { return choice; }
    }

    private static class RecordingFiles extends ZoneFiles {
        final LinkedBlockingQueue<Invocation> calls = new LinkedBlockingQueue<>();
        volatile CompletableFuture<ZoneReconciliationResult> deferredResult;
        @Override
        public de.neitzel.dnscheck.core.model.LiveZone readLive(de.neitzel.dnscheck.core.model.ZoneDefinition definition) {
            return new de.neitzel.dnscheck.core.model.LiveZone(definition.zone(), 3600, List.of());
        }
        @Override
        public ZoneReconciliationResult reconcile(Path directory, String zone, boolean dryRun) {
            calls.add(new Invocation(directory, zone));
            if (deferredResult != null) return deferredResult.join();
            return ZoneReconciliationResult.failed(zone, "Offline test result");
        }
    }
}
