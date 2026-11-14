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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(catalog.getViewModel().getLatestMessage().getText().contains("does not exist"));
            assertEquals("Zone", headerText(catalog.getRoot(), "entriesTable", 0));
            assertEquals("Nameserver", headerText(catalog.getRoot(), "entriesTable", 1));
            assertEquals("Valid", headerText(catalog.getRoot(), "entriesTable", 3));
            assertEquals(180, headerCell(catalog.getRoot(), "entriesTable", 0).getPrefWidth());

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
            var table = (javafx.scene.control.TableView<?>) zone.getRoot().lookup("#recordTable");
            assertEquals(1, table.getItems().size());
            assertEquals("Name", table.getColumns().get(1).getText());
            assertEquals("Status", table.getColumns().get(5).getText());
            assertEquals("192.0.2.10", ((de.neitzel.dnscheck.fx.model.RecordRow) table.getItems().getFirst()).value());
            assertEquals("ns1.example.com", text(zone.getRoot(), "ns1.example.com"));
        });
    }

    /**
     * Returns the header text of one column.
     *
     * @param root    the loaded view
     * @param tableId the table id
     * @param index   the column index
     * @return the header text
     */
    private static String headerText(Parent root, String tableId, int index) {
        Label label = (Label) headerCell(root, tableId, index).lookup(".column-header");
        return label.getText();
    }

    /**
     * Returns one header cell.
     *
     * @param root    the loaded view
     * @param tableId the table id
     * @param index   the column index
     * @return the cell
     */
    private static Region headerCell(Parent root, String tableId, int index) {
        ScrollPane headerScroll = (ScrollPane) root.lookup("#" + tableId).lookup(".table-header-scroll");
        HBox header = (HBox) headerScroll.getContent();
        return (Region) header.getChildren().get(index);
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
