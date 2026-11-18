package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.model.RecordEditor;
import de.neitzel.dnscheck.fx.model.ZoneEditor;
import de.neitzel.dnscheck.fx.view.CatalogController;
import de.neitzel.dnscheck.fx.view.ZoneController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Loads the catalog and a zone definition the way the desktop application does.
 */
class DnsCheckFxLoadsTest {
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path temp;

    /**
     * Starts the JavaFX toolkit once for this class.
     */
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        Platform.setImplicitExit(false);
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        if (!started.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX toolkit did not start");
        }
    }

    /**
     * Catalog and zone views load.
     */
    @Test
    void catalogAndZoneViewsLoad() throws Exception {
        CountDownLatch gridLoaded = new CountDownLatch(1);
        AtomicReference<de.neitzel.fx.view.DataGrid<?, ?>> records = new AtomicReference<>();
        onFx(() -> {
            RecentDirectories recent = new RecentDirectories(temp.resolve("config.json"));
            ApplicationContext context = new ApplicationContext(DnsCheckFxApp.class) {
                @Override public <T> T getComponent(Class<? extends T> type) {
                    return type == RecentDirectories.class ? type.cast(recent) : super.getComponent(type);
                }
            };
            Desk desk = context.getComponent(Desk.class);
            CatalogSession session = new CatalogSession();
            session.setDirectory("/no/such/zones");
            session.setEntries(new ArrayList<>());
            session.setOutcome("");
            desk.prepare(session, context);

            LoadedView catalog = ViewLoader.load(
                    CatalogController.class.getResource("Catalog.fxml"), session, context);
            new javafx.scene.Scene(catalog.getRoot(), 1200, 800);
            catalog.getRoot().applyCss();
            catalog.getRoot().layout();
            assertNotNull(catalog.getRoot().lookup("#zonesList"));
            assertNotNull(catalog.getRoot().lookup("#editorHost"));
            assertTrue(catalog.getRoot().lookup("#newZoneButton").isDisabled());

            ZoneEditor editor = new ZoneEditor();
            editor.setZone("example.com");
            editor.setNameserver("ns1.example.com");
            editor.setKeyFile("dnscheck.key");
            editor.setRecords(new ArrayList<>());
            editor.setOutcome("");
            RecordEditor record = new RecordEditor();
            record.setName("mail.example.com.");
            record.setType("A");
            record.setRdata("192.0.2.10");
            editor.getRecords().add(record);
            desk.edit(editor);

            LoadedView zone = ViewLoader.load(ZoneController.class.getResource("Zone.fxml"), editor, context);
            var table = (de.neitzel.fx.view.DataGrid<?, ?>) zone.getRoot().lookup("#recordTable");
            records.set(table);
            assertSame(editor.getRecordRows(), table.getItems(), "Merged rows must be bound to the declared grid");
            assertEquals(6, table.getColumns().size());
            assertEquals("type", table.getGroupBy());
            table.stateProperty().addListener((observable, before, after) -> {
                if (after.phase() == de.neitzel.grid.GridCoordinator.Phase.READY) gridLoaded.countDown();
            });
            assertEquals("ns1.example.com", text(zone.getRoot(), "ns1.example.com"));
        });
        assertTrue(gridLoaded.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            var rows = records.get().stateProperty().get().result().rows();
            assertEquals(1, rows.size());
            assertEquals("192.0.2.10", ((de.neitzel.dnscheck.fx.model.RecordRow) rows.getFirst().model()).value());
        });
    }

    @Test
    void zoneFxmlShowsItsGridWithoutRuntimeData() throws Exception {
        onFx(() -> {
            try {
                var loader = new javafx.fxml.FXMLLoader(ZoneController.class.getResource("Zone.fxml"));
                loader.setControllerFactory(type -> new ZoneController(null, null));
                javafx.scene.Parent root = loader.load();
                var grid = (de.neitzel.fx.view.DataGrid<?, ?>) root.lookup("#recordTable");
                assertNotNull(grid);
                assertNull(root.lookup("#recordGridHost"));
                assertNull(grid.getItems());
                assertNull(grid.stateProperty().get(), "Designer loading must not start a query");
                assertEquals(6, grid.getColumns().size());
                assertEquals("Name", grid.getColumns().get(1).getHeader());
                assertEquals("✓ Enforced entry", grid.getColumns().getFirst().getGroupLabels().get("ENFORCED"));
                assertEquals(6, grid.lookupAll(".column-header").size());
                grid.close();
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        });
    }

    /**
     * Returns the text of the field whose current text equals the expected value.
     *
     * @param node the node
     * @param expected the field text to find
     * @return the field text
     */
    private static String text(Node node, String expected) {
        if (node instanceof TextField field && expected.equals(field.getText())) {
            return field.getText();
        }
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
            String found = text(scroll.getContent(), expected);
            if (found != null) {
                return found;
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (node instanceof ScrollPane scroll && child == scroll.getContent()) {
                    continue;
                }
                String found = text(child, expected);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Runs one check on the JavaFX application thread.
     *
     * @param check the check
     */
    private static void onFx(Runnable check) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                check.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(10, TimeUnit.SECONDS)) {
            fail("JavaFX check did not finish");
        }
        if (failure.get() != null) {
            fail("view check failed", failure.get());
        }
    }
}
