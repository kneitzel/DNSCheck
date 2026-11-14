package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.view.CatalogController;
import de.neitzel.dnscheck.fx.view.ZoneController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Test
    void treeSelectionSwitchesBetweenOnlyTheTableAndZoneDetails() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            assertEquals("Domains", f.tree().getRoot().getValue());
            assertTrue(f.tree().getRoot().isExpanded());
            assertEquals(List.of("example.com", "example.net"), f.tree().getRoot().getChildren().stream()
                    .map(TreeItem::getValue).toList());
            assertTrue(f.view().getRoot().lookup("#entriesTable").isVisible());
            assertFalse(f.view().getRoot().lookup("#editorHost").isVisible());
            assertEquals(2, ((SplitPane) f.view().getRoot().lookup("#catalogSplit")).getItems().size());
            f.tree().getSelectionModel().select(f.tree().getRoot().getChildren().getFirst());
            assertEquals("example.com", f.desk().zone().getZone());
            assertEquals("ns-a.example.com", ((TextField) f.view().getRoot().lookup("#nameserverField")).getText());
            assertFalse(f.view().getRoot().lookup("#entriesTable").isVisible());
            assertTrue(f.view().getRoot().lookup("#editorHost").isVisible());
            f.tree().getSelectionModel().select(f.tree().getRoot());
            assertNull(f.desk().zone());
            assertTrue(f.view().getRoot().lookup("#entriesTable").isVisible());
            assertNull(f.view().getRoot().lookup("#nameserverField"));
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
            assertEquals("other.example", f.tree().getRoot().getChildren().getFirst().getValue());
            assertEquals(f.directoryB().toString(), ((Label) f.view().getRoot().lookup("#directoryLabel")).getText());
            zoneFile(f.directoryB(), "new.example", "ns-new.example");
            menu(f, 1).fire();
            assertEquals(List.of("new.example", "other.example"), f.tree().getRoot().getChildren().stream()
                    .map(TreeItem::getValue).toList());
            assertEquals(f.tree().getRoot(), f.tree().getSelectionModel().getSelectedItem());
            assertNull(f.desk().zone());
            return null;
        });
    }

    @Test
    void cancelledChooserAndFailedOpenPreserveTheCurrentEditorAndDirectory() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.tree().getSelectionModel().select(f.tree().getRoot().getChildren().getFirst());
            var editor = f.desk().zone();
            f.controller().choice = null;
            menu(f, 0).fire();
            assertSame(editor, f.desk().zone());
            f.controller().choice = temp.resolve("missing");
            menu(f, 0).fire();
            assertSame(editor, f.desk().zone());
            assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
            assertEquals("example.com", f.tree().getSelectionModel().getSelectedItem().getValue());
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Save", "Discard", "Cancel"})
    void fileOpenProtectsUnsavedEditsAndCommitsTheDirectoryOnlyOnSuccess(String choice) throws Exception {
        Fixture f = fixture();
        String original = Files.readString(f.fileA());
        onFx(() -> {
            f.tree().getSelectionModel().select(f.tree().getRoot().getChildren().getFirst());
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            Platform.runLater(() -> clickDialog(choice));
            f.controller().choice = f.directoryB();
            menu(f, 0).fire();
            if (choice.equals("Cancel")) {
                assertEquals(f.directoryA(), f.desk().requireLoadedDirectory());
                assertTrue(f.desk().zone().isDirty());
                assertEquals("example.com", f.tree().getSelectionModel().getSelectedItem().getValue());
                assertEquals(List.of(f.directoryA().toRealPath()),
                        new RecentDirectories(temp.resolve("settings/config.json")).existingDirectories());
            } else {
                assertEquals(f.directoryB(), f.desk().requireLoadedDirectory());
                assertNull(f.desk().zone());
                assertEquals(f.tree().getRoot(), f.tree().getSelectionModel().getSelectedItem());
            }
            return null;
        });
        if (choice.equals("Save")) assertTrue(Files.readString(f.fileA()).contains("edited.example.com"));
        else assertEquals(original, Files.readString(f.fileA()));
    }

    @ParameterizedTest
    @CsvSource({"Save,true", "Discard,true", "Cancel,true", "Save,false", "Discard,false", "Cancel,false"})
    void treeNavigationProtectsUnsavedEdits(String choice, boolean toRoot) throws Exception {
        Fixture f = fixture();
        String original = Files.readString(f.fileA());
        onFx(() -> {
            TreeItem<String> originalItem = f.tree().getRoot().getChildren().getFirst();
            f.tree().getSelectionModel().select(originalItem);
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            Platform.runLater(() -> clickDialog(choice));
            TreeItem<String> destination = toRoot ? f.tree().getRoot() : f.tree().getRoot().getChildren().get(1);
            f.tree().getSelectionModel().select(destination);
            if (choice.equals("Cancel")) {
                assertEquals(originalItem, f.tree().getSelectionModel().getSelectedItem());
                assertEquals("example.com", f.desk().zone().getZone());
                assertTrue(f.desk().zone().isDirty());
            } else {
                assertEquals(destination, f.tree().getSelectionModel().getSelectedItem());
                if (toRoot) assertNull(f.desk().zone());
                else assertEquals("example.net", f.desk().zone().getZone());
            }
            return null;
        });
        if (choice.equals("Save")) assertTrue(Files.readString(f.fileA()).contains("edited.example.com"));
        else assertEquals(original, Files.readString(f.fileA()));
    }

    @Test
    void invalidDomainShowsItsErrorInsteadOfThePreviousDomainsDetails() throws Exception {
        Fixture f = fixture();
        Files.writeString(f.directoryA().resolve("bad.domain"), "invalid JSON");
        onFx(() -> {
            menu(f, 1).fire();
            f.tree().getSelectionModel().select(f.tree().getRoot().getChildren().get(1));
            assertNotNull(f.desk().zone());
            f.tree().getSelectionModel().select(f.tree().getRoot().getChildren().getFirst());
            assertNull(f.desk().zone());
            assertNull(f.view().getRoot().lookup("#nameserverField"));
            assertTrue(f.view().getRoot().lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).anyMatch(label -> label.getText().contains("bad is invalid")));
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
            f.tree().getSelectionModel().select(f.tree().getRoot().getChildren().getFirst());
            f.context().getComponent(ZoneController.class).dryRun();
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
            assertEquals("example.com", f.tree().getRoot().getChildren().getFirst().getValue());
            assertEquals(f.directoryA().toRealPath().toString(), recent.getItems().getFirst().getText());
            Files.delete(f.directoryB().resolve("other.example.domain"));
            Files.delete(f.directoryB().resolve("dnscheck.key"));
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
            assertEquals("other.example", f.tree().getRoot().getChildren().getFirst().getValue());
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
        Path directoryA = temp.resolve("a");
        Path directoryB = temp.resolve("b");
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
            new javafx.scene.Scene(view.getRoot(), 1200, 800);
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
        TreeView<String> tree() { return (TreeView<String>) view.getRoot().lookup("#domainsTree"); }
    }
    private record Invocation(Path directory, String zone) {}

    private static class ChoosingController extends CatalogController {
        Path choice;
        ChoosingController(Desk desk, ZoneFiles files, RecentDirectories recent) { super(desk, files, recent); }
        @Override protected Path chooseDirectory() { return choice; }
    }

    private static class RecordingFiles extends ZoneFiles {
        final LinkedBlockingQueue<Invocation> calls = new LinkedBlockingQueue<>();
        @Override
        public de.neitzel.dnscheck.core.model.LiveZone readLive(de.neitzel.dnscheck.core.model.ZoneDefinition definition) {
            return new de.neitzel.dnscheck.core.model.LiveZone(definition.zone(), 3600, List.of());
        }
        @Override
        public ZoneReconciliationResult reconcile(Path directory, String zone, boolean dryRun) {
            calls.add(new Invocation(directory, zone));
            return ZoneReconciliationResult.failed(zone, "Offline test result");
        }
    }
}
