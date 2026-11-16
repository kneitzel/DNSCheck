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
import org.junit.jupiter.api.AfterEach;
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
    private final java.util.Queue<Throwable> fxErrors = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private Thread.UncaughtExceptionHandler previousHandler;
    @org.junit.jupiter.api.BeforeEach void captureFxErrors() throws Exception {
        onFx(() -> {previousHandler = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((t,e) -> fxErrors.add(e));return null;});
    }
    @BeforeAll static void toolkit() throws Exception {
        Platform.setImplicitExit(false);
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }
    @AfterEach void closeWindows() throws Exception {
        onFx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) window.hide();
            Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
            return null;
        });
        assertTrue(fxErrors.isEmpty(), fxErrors.toString());
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
            var zone = (ZoneController) f.view().getRoot().lookup("#zoneRoot").getUserData();
            zone.addRecord();
            return null;
        });
        assertTrue(saved.await(5, TimeUnit.SECONDS));
        assertEquals(1, f.files().reads.get());
        assertEquals(3, new LocalZoneDirectory(temp).read("example.com").records().size());
        onFx(() -> { assertFalse(f.desk().zone().isDirty()); assertFalse(f.desk().zone().isApplying()); return null; });
    }

    @Test void themesPreserveRecordIdentityDraftsAndDomainIndicators() throws Exception {
        Fixture f = fixture();
        f.files().response.complete(live());assertTrue(f.loaded().await(5,TimeUnit.SECONDS));
        String disk = Files.readString(temp.resolve("example.com.domain"));
        var state = onFx(() -> {
            var root = f.view().getRoot();
            var field = (TextField)root.lookup("#nameserverField");field.setText("unfinished.example.net");
            var table=f.table();var items=table.getItems();var selected=items.stream().filter(r -> r.kind()==RecordRow.Kind.REMOVAL).findFirst().orElseThrow();
            table.getSelectionModel().select(selected);
            var nameColumn=table.getColumns().get(1);nameColumn.setPrefWidth(235);nameColumn.setSortType(TableColumn.SortType.DESCENDING);table.getSortOrder().setAll(nameColumn);
            var menu=((MenuBar)root.lookup("#fileMenuBar")).getMenus().get(1);
            var themes=(Menu)menu.getItems().getFirst();
            var dialog=new RecordDialog(root.getScene().getWindow(),"example.com",null);dialog.show();
            ((TextArea)dialog.getDialogPane().lookup("#recordRdata")).setText("unfinished-invalid-address");
            ((Button)dialog.getDialogPane().lookup("#applyRecordButton")).fire();
            return new ThemeState(root,field,table,items,selected,nameColumn,themes,dialog);
        });
        try {
            for(int index=0;index<3;index++) {
                final int themeIndex=index;
                String theme=List.of("light","dark","high-contrast").get(index);double target=index==2?7:4.5;
                onFx(() -> {((RadioMenuItem)state.themes().getItems().get(themeIndex)).fire();return null;});
                var pulse=new CountDownLatch(1);
                onFx(() -> {var wait=new javafx.animation.PauseTransition(javafx.util.Duration.millis(80));wait.setOnFinished(e->pulse.countDown());wait.play();return null;});
                assertTrue(pulse.await(5,TimeUnit.SECONDS));
                onFx(() -> {
                    var root=state.root();var field=state.field();var table=state.table();var items=state.items();var selected=state.selected();var nameColumn=state.nameColumn();var dialog=state.dialog();
                    root.applyCss();root.layout();dialog.getDialogPane().applyCss();dialog.getDialogPane().layout();
                    assertSame(items,table.getItems());assertSame(selected,table.getSelectionModel().getSelectedItem());
                    assertEquals(List.of(nameColumn),table.getSortOrder());assertEquals(TableColumn.SortType.DESCENDING,nameColumn.getSortType());assertEquals(235,nameColumn.getWidth(),.1);assertEquals("unfinished.example.net",field.getText());assertTrue(f.desk().zone().isDirty());
                    assertEquals("unfinished-invalid-address",((TextArea)dialog.getDialogPane().lookup("#recordRdata")).getText());
                    assertFalse(((CheckBox)dialog.getDialogPane().lookup("#recordOnline")).isSelected());assertTrue(((CheckBox)dialog.getDialogPane().lookup("#recordStore")).isSelected());
                    for (var node : root.lookupAll(".catalog-icon")) {
                        var icon = (javafx.scene.shape.SVGPath)node;
                        ThemeEvidence.paint(theme+" catalog icon", (javafx.scene.paint.Color)icon.getStroke(), ThemeEvidence.background(icon), 3);
                    }
                    for (var node : root.lookupAll(".zone-action-icon")) {
                        var icon = (javafx.scene.shape.SVGPath)node;
                        ThemeEvidence.paint(theme+" action icon", (javafx.scene.paint.Color)icon.getStroke(), ThemeEvidence.background(icon), 3);
                    }
                    ThemeEvidence.check(theme+" dialog error",(Label)dialog.getDialogPane().lookup("#recordError"),target);
                    for(String kind:List.of("file","live","removal"))ThemeEvidence.check(theme+" "+kind+" legend",(Label)root.lookup("."+kind+"-legend"),target);
                    for(var rowItem:List.copyOf(items)) {
                        table.getSelectionModel().clearSelection();root.applyCss();root.layout();
                        TableRow<?> row=table.lookupAll(".table-row-cell").stream().filter(n->n instanceof TableRow<?> tr && tr.getItem()==rowItem).map(n->(TableRow<?>)n).findFirst().orElseThrow();
                        var cells=row.lookupAll(".table-cell").stream().map(n->(TableCell<?,?>)n).filter(c->c.getText()!=null && !c.getText().isBlank()).toList();
                        String symbol=switch(rowItem.kind()){case ENFORCED->"✓";case REMOVAL->"−";case LIVE->"●";};
                        assertTrue(cells.stream().anyMatch(c->symbol.equals(c.getText())));
                        for(var cell:cells)ThemeEvidence.check(theme+" "+rowItem.kind()+" normal",cell,target);
                        table.getSelectionModel().select(rowItem);row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"),true);root.applyCss();root.layout();
                        for(var cell:cells)ThemeEvidence.check(theme+" "+rowItem.kind()+" selected/focused",cell,target);
                        table.setDisable(true);root.applyCss();for(var cell:cells)ThemeEvidence.check(theme+" "+rowItem.kind()+" selected/disabled",cell,target);table.setDisable(false);
                        row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"),false);
                    }
                    table.getSelectionModel().select(selected);root.applyCss();root.layout();
                    ThemeEvidence.snapshot(theme,root);ThemeEvidence.snapshot(theme+"-dialog",dialog.getDialogPane());
                    return null;
                });
            }
        } finally {onFx(() -> {state.dialog().close();return null;});}
        assertEquals(1,f.files().reads.get(),"Theme changes must not issue AXFR");
        assertEquals(disk,Files.readString(temp.resolve("example.com.domain")),"Themes must not save zone edits");
    }

    private record ThemeState(javafx.scene.Parent root, TextField field, TableView<RecordRow> table,
        javafx.collections.ObservableList<RecordRow> items, RecordRow selected,
        TableColumn<RecordRow,?> nameColumn, Menu themes, RecordDialog dialog) {}

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
            var stage = new javafx.stage.Stage();
            stage.setScene(new Scene(view.getRoot(), 1200, 800));
            stage.show();
            var controller = context.getComponent(CatalogController.class);
            controller.start(stage);
            var list = (ListView<?>) view.getRoot().lookup("#zonesList");
            list.getSelectionModel().select(0);
            controller.open();
            view.getRoot().applyCss();
            view.getRoot().layout();
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
