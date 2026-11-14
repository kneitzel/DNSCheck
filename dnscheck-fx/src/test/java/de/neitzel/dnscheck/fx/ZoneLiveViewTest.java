package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.model.*;
import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import de.neitzel.dnscheck.fx.model.*;
import de.neitzel.dnscheck.fx.view.*;
import de.neitzel.fx.view.*;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ZoneLiveViewTest {
    @TempDir Path temp;
    @BeforeAll static void toolkit() throws Exception {
        Platform.setImplicitExit(false);
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }
    @Test void opensImmediatelyAndLoadsMergedColoredRowsInTheBackground() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            assertEquals(2, f.table().getItems().size());
            assertTrue(f.status().getText().startsWith("Loading live DNS"));
            return null;
        });
        f.files().response.complete(live());
        assertTrue(f.loaded().await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(4, f.table().getItems().size());
            assertEquals(1, f.table().getItems().stream().filter(row -> row.kind() == RecordRow.Kind.ENFORCED).count());
            assertTrue(f.table().getItems().stream().anyMatch(row -> row.status().equals("TTL drift")));
            assertTrue(f.table().getItems().stream().anyMatch(row -> row.type().equals("SOA")));
            f.view().getRoot().applyCss(); f.view().getRoot().layout();
            var colored = f.table().lookupAll(".table-row-cell").stream()
                    .filter(TableRow.class::isInstance).map(TableRow.class::cast).filter(row -> !row.isEmpty()).toList();
            assertEquals(4, colored.size());
            assertTrue(colored.stream().allMatch(row -> row.getPseudoClassStates().stream()
                    .anyMatch(state -> List.of("file-entry", "removal-entry", "live-entry").contains(state.getPseudoClassName()))));
            return null;
        });
    }
    @Test void failedAxfrLeavesFileRecordsAndDistinguishesUnknownLiveState() throws Exception {
        Fixture f = fixture();
        f.files().response.completeExceptionally(new IOException("AXFR unavailable"));
        assertTrue(f.loaded().await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(2, f.table().getItems().size());
            assertTrue(f.table().getItems().stream().allMatch(row -> row.status().equals("Live not loaded")));
            assertTrue(f.status().getText().contains("AXFR unavailable"));
            assertFalse(f.desk().zone().isDirty());
            return null;
        });
    }
    @Test void newRecordPopupFileOnlyApplyPersistsAndRefreshesTheTableWithoutDns() throws Exception {
        Fixture f = fixture();
        f.files().response.complete(live());
        assertTrue(f.loaded().await(5, TimeUnit.SECONDS));
        CountDownLatch saved = new CountDownLatch(1);
        onFx(() -> {
            f.table().getItems().addListener((ListChangeListener<RecordRow>) change -> {
                if (f.table().getItems().stream().anyMatch(row -> row.name().equals("new.example.com."))) saved.countDown();
            });
            Platform.runLater(() -> {
                DialogPane pane = Window.getWindows().stream().filter(Window::isShowing)
                        .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                        .findFirst().orElseThrow();
                ((TextField) pane.lookup("#recordName")).setText("new");
                ((TextArea) pane.lookup("#recordRdata")).setText("192.0.2.7");
                ((Button) pane.lookup("#applyRecordButton")).fire();
            });
            f.context().getComponent(ZoneController.class).addRecord();
            return null;
        });
        assertTrue(saved.await(5, TimeUnit.SECONDS));
        assertEquals(1, f.files().reads.get());
        assertEquals(3, new LocalZoneDirectory(temp).read("example.com").records().size());
        onFx(() -> { assertFalse(f.desk().zone().isDirty()); assertFalse(f.desk().zone().isApplying()); return null; });
    }

    private Fixture fixture() throws Exception {
        Files.writeString(temp.resolve("test.key"), """
                key "test" { algorithm hmac-sha256; secret "dGVzdA=="; };
                """);
        Files.writeString(temp.resolve("example.com.domain"), """
                {"zone":"example.com","nameserver":"ns.example.com","keyFile":"test.key","records":[
                {"name":"www","type":"A","rdata":"192.0.2.1","ttl":300},
                {"name":"old","type":"TXT","delete":true,"rdataPattern":"old*"}]}
                """);
        return onFx(() -> {
            DeferredFiles files = new DeferredFiles();
            RecentDirectories recent = new RecentDirectories(temp.resolve("settings/config.json"));
            ApplicationContext context = new ApplicationContext(DnsCheckFxApp.class) {
                @Override public <T> T getComponent(Class<? extends T> type) {
                    if (type == ZoneFiles.class) return type.cast(files);
                    if (type == RecentDirectories.class) return type.cast(recent);
                    return super.getComponent(type);
                }
            };
            Desk desk = context.getComponent(Desk.class);
            CatalogSession session = new CatalogSession(); session.setDirectory(temp.toString()); desk.prepare(session, context);
            LoadedView view = ViewLoader.load(CatalogController.class.getResource("Catalog.fxml"), session, context);
            new Scene(view.getRoot(), 1200, 800); view.getRoot().applyCss(); view.getRoot().layout();
            Attributes.set(view.getViewModel(), "selected", session.getEntries().getFirst());
            context.getComponent(CatalogController.class).open();
            @SuppressWarnings("unchecked") TableView<RecordRow> table = (TableView<RecordRow>) view.getRoot().lookup("#recordTable");
            Label status = (Label) view.getRoot().lookup("#liveStatus");
            CountDownLatch loaded = new CountDownLatch(1);
            status.textProperty().addListener((observable, before, after) -> {
                if (!after.startsWith("Loading")) loaded.countDown();
            });
            return new Fixture(context, desk, view, files, table, status, loaded);
        });
    }
    private static LiveZone live() {
        return new LiveZone("example.com", 3600, List.of(
                new DnsRecord("www.example.com.", "A", "192.0.2.1", 600),
                new DnsRecord("mail.example.com.", "A", "192.0.2.2", 300),
                new DnsRecord("example.com.", "SOA", "ns.example.com. hostmaster.example.com. 1 60 60 60 60", 300)));
    }
    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
    private record Fixture(ApplicationContext context, Desk desk, LoadedView view, DeferredFiles files,
                           TableView<RecordRow> table, Label status, CountDownLatch loaded) {}
    private static class DeferredFiles extends ZoneFiles {
        final CompletableFuture<LiveZone> response = new CompletableFuture<>();
        final AtomicInteger reads = new AtomicInteger();
        @Override public LiveZone readLive(ZoneDefinition definition) throws IOException {
            reads.incrementAndGet();
            try { return response.get(5, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new IOException("AXFR unavailable", failure); }
        }
    }
}
