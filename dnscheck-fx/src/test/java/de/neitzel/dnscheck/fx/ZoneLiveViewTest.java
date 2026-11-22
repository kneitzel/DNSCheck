package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.model.*;
import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import de.neitzel.dnscheck.fx.model.*;
import de.neitzel.dnscheck.fx.view.*;
import de.neitzel.fx.view.*;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import de.neitzel.grid.*;
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
    private final java.util.List<GridApplicationConfiguration> gridConfigurations = new java.util.ArrayList<>();
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
        for (var configuration : gridConfigurations) configuration.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(fxErrors.isEmpty(), fxErrors.toString());
    }
    @Test void opensImmediatelyAndLoadsMergedColoredRowsInTheBackground() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            assertEquals(2, rows(f.table()).size());
            assertTrue(f.status().getText().startsWith("Loading live DNS"));
            return null;
        });
        f.files().response.complete(live());
        assertTrue(f.loaded().await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(4, rows(f.table()).size());
            assertEquals(1, rows(f.table()).stream().filter(row -> row.kind() == RecordRow.Kind.ENFORCED).count());
            assertTrue(rows(f.table()).stream().anyMatch(row -> row.status().equals("TTL drift")));
            assertTrue(rows(f.table()).stream().anyMatch(row -> row.type().equals("SOA")));
            f.view().getRoot().applyCss(); f.view().getRoot().layout();
            assertFalse(f.table().lookupAll(".group-heading").isEmpty());
            expandTypes(f.table());
            var colored = f.table().lookupAll(".result-row").stream()
                    .toList();
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
            assertEquals(2, rows(f.table()).size());
            assertTrue(rows(f.table()).stream().allMatch(row -> row.status().equals("Live not loaded")));
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
            f.table().stateProperty().addListener((observable, before, after) -> {
                if (after.phase() == GridCoordinator.Phase.READY && rows(f.table()).stream()
                        .anyMatch(row -> row.name().equals("new.example.com."))) saved.countDown();
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

    @Test void filtersAndSortCoverOtherPagesAndSurviveLiveRefresh() throws Exception {
        Fixture f = fixture();
        var records = new java.util.ArrayList<>(live().records());
        for (int index = 0; index < 80; index++) records.add(new DnsRecord(
                "host%02d.example.com.".formatted(index), "TXT", "value-" + index, 300));
        f.files().response.complete(new LiveZone("example.com", 3600, records));
        assertTrue(f.loaded().await(5, TimeUnit.SECONDS));
        String disk = Files.readString(temp.resolve("example.com.domain"));
        CountDownLatch candidateLoaded = new CountDownLatch(1);
        var popup = onFx(() -> {
            f.table().lookupAll(".filter-button").stream().map(Button.class::cast)
                    .filter(button -> "Filter File / live value".equals(button.getAccessibleText()))
                    .findFirst().orElseThrow().fire();
            var root = Window.getWindows().stream().filter(Window::isShowing)
                    .map(window -> window.getScene().getRoot())
                    .filter(node -> node.lookup(".filter-editor") != null).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") var mode = (ComboBox<String>) root.lookup("#mode");
            mode.setValue("Checklist");
            var candidates = (javafx.scene.layout.VBox) root.lookup("#candidates");
            candidates.getChildren().addListener((javafx.collections.ListChangeListener<javafx.scene.Node>) change -> {
                if (candidates.getChildren().stream().anyMatch(node -> node instanceof CheckBox box
                        && box.getText().equals("value-79"))) candidateLoaded.countDown();
            });
            ((TextField) root.lookup("#search")).setText("value-79");
            return root;
        });
        assertTrue(candidateLoaded.await(5, TimeUnit.SECONDS), "Checklist must find values beyond the first page");
        onFx(() -> {
            popup.lookupAll(".button").stream().filter(node -> node instanceof Button button
                    && button.getText().equals("Cancel")).map(Button.class::cast).findFirst().orElseThrow().fire();
            assertEquals(List.of(), f.table().stateProperty().get().displayed().filters());
            assertEquals(50, rows(f.table()).size());
            assertEquals(6, f.table().lookupAll(".filter-button").size());
            f.table().setFilters(List.of(
                    new TextFilter("type", TextFilter.Operator.EQUALS, "txt", false),
                    new TextFilter("value", TextFilter.Operator.CONTAINS, "value-7", false)));
            return null;
        });
        awaitReady(f.table());
        onFx(() -> {
            assertEquals(11, rows(f.table()).size());
            f.table().setSort(new SortKey("name", SortKey.Direction.DESCENDING));
            return null;
        });
        awaitReady(f.table());
        onFx(() -> {
            assertEquals("host79.example.com.", rows(f.table()).getFirst().name());
            expandTypes(f.table());
            select(f.table(), rows(f.table()).getFirst());
            ((ZoneController) f.view().getRoot().lookup("#zoneRoot").getUserData()).refreshLive();
            return null;
        });
        // Wait for the refreshed live result, rather than the interim file-only page.
        CountDownLatch refreshed = new CountDownLatch(1);
        onFx(() -> {
            if (rows(f.table()).size() == 11 && f.table().stateProperty().get().phase() == GridCoordinator.Phase.READY)
                refreshed.countDown();
            else f.table().stateProperty().addListener((o, before, after) -> {
                if (after.phase() == GridCoordinator.Phase.READY && rows(f.table()).size() == 11) refreshed.countDown();
            });
            return null;
        });
        assertTrue(refreshed.await(5, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals("host79.example.com.", f.table().selectedElementProperty().get().name());
            assertEquals(2, f.table().stateProperty().get().displayed().filters().size());
            f.table().setFilters(List.of());
            return null;
        });
        awaitReady(f.table());
        onFx(() -> { assertEquals(50, rows(f.table()).size()); return null; });
        assertEquals(disk, Files.readString(temp.resolve("example.com.domain")));
        assertFalse(f.desk().zone().isDirty());
    }

    @Test void savedSetupIsSharedButAlreadyOpenZoneKeepsItsOwnLayout() throws Exception {
        Fixture first=fixture();first.files().response.complete(live());assertTrue(first.loaded().await(5,TimeUnit.SECONDS));
        onFx(()->{first.table().setColumnWidth("name",new ColumnWidth.Explicit(240));
            first.table().setGroupingColumns(List.of("status","type"));return null;});
        first.desk().getGridConfiguration().flushAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
        Files.writeString(temp.resolve("other.com.domain"),
                "{\"zone\":\"other.com\",\"nameserver\":\"ns.other.com\",\"keyFile\":\"test.key\",\"records\":[]}");
        DataGrid<String,RecordRow> second=onFx(()->{
            var controller=first.context().getComponent(CatalogController.class);controller.reload();
            var zones=(ListView<?>)first.view().getRoot().lookup("#zonesList");zones.getSelectionModel().select(1);controller.open();
            @SuppressWarnings("unchecked") var other=(DataGrid<String,RecordRow>)first.view().getRoot().lookupAll("#recordTable").stream()
                    .filter(node->node!=first.table()).findFirst().orElseThrow();return other;
        });
        awaitReady(second);
        onFx(()->{
            assertEquals(List.of("status","type"),second.groupingColumns());
            assertEquals(new ColumnWidth.Explicit(240),second.snapshotSetup().columns().stream().filter(c->c.id().equals("name")).findFirst().orElseThrow().width());
            second.setColumnWidth("name",new ColumnWidth.Explicit(310));
            assertEquals(new ColumnWidth.Explicit(240),first.table().snapshotSetup().columns().stream().filter(c->c.id().equals("name")).findFirst().orElseThrow().width());
            return null;
        });
        first.desk().getGridConfiguration().flushAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
    }

    @Test void unreadableSetupBlocksInitialQueriesAndAxfrUntilExplicitReset() throws Exception {
        Fixture f=fixture(true);
        CountDownLatch errorShown=new CountDownLatch(1);
        onFx(()->{
            var state=f.desk().getGridConfiguration().state(new GridSetupStore.Key("dnscheck.zone-records",null));
            if(state.phase()==GridApplicationConfiguration.Phase.ERROR)errorShown.countDown();
            else f.desk().getGridConfiguration().observe(new GridSetupStore.Key("dnscheck.zone-records",null),
                    value->{if(value.phase()==GridApplicationConfiguration.Phase.ERROR)errorShown.countDown();});
            return null;
        });
        assertTrue(errorShown.await(5,TimeUnit.SECONDS));
        onFx(()->{assertEquals(0,f.files().reads.get());assertNull(f.table().stateProperty().get().result());
            assertFalse(f.view().getRoot().lookup("#nameserverField").isDisabled());return null;});
        f.files().response.complete(live());
        onFx(()->{
            var menu=(MenuButton)f.table().lookupAll(".menu-button").stream().filter(MenuButton.class::isInstance).findFirst().orElseThrow();
            menu.getItems().stream().filter(item->"Reset setup".equals(item.getText())).findFirst().orElseThrow().fire();return null;
        });
        assertTrue(f.loaded().await(5,TimeUnit.SECONDS));
        assertEquals(1,f.files().reads.get());
        f.desk().getGridConfiguration().flushAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
        var store=new de.neitzel.gson.grid.JsonGridSetupStore(temp.resolve("grid-settings"),new de.neitzel.gson.grid.GridSetupJson());
        assertEquals(List.of("type"),store.load(new GridSetupStore.Key("dnscheck.zone-records",null)).setup().grouping().columns());
    }

    @Test void switchingFoldersKeepsApplicationSettingsServiceAndRestoresTheSharedSlot() throws Exception {
        Fixture f=fixture();f.files().response.complete(live());assertTrue(f.loaded().await(5,TimeUnit.SECONDS));
        onFx(()->{f.table().setColumnWidth("name",new ColumnWidth.Explicit(245));return null;});
        f.desk().getGridConfiguration().flushAsync().toCompletableFuture().get(5,TimeUnit.SECONDS);
        Path other=temp.resolve("other-folder");Files.createDirectories(other);
        Files.copy(temp.resolve("test.key"),other.resolve("test.key"));
        Files.writeString(other.resolve("other.com.domain"),"{\"zone\":\"other.com\",\"nameserver\":\"ns.other.com\",\"keyFile\":\"test.key\",\"records\":[]}");
        CountDownLatch switched=new CountDownLatch(1);
        onFx(()->{
            var zones=(ListView<?>)f.view().getRoot().lookup("#zonesList");
            zones.getItems().addListener((javafx.collections.ListChangeListener<Object>)change->{
                if(zones.getItems().stream().anyMatch(item->item instanceof CatalogEntryModel entry&&entry.getZone().equals("other.com")))switched.countDown();
            });
            assertTrue(f.context().getComponent(CatalogController.class).openDirectory(other));return null;
        });
        assertTrue(switched.await(5,TimeUnit.SECONDS));
        DataGrid<String,RecordRow> next=onFx(()->{
            var zones=(ListView<?>)f.view().getRoot().lookup("#zonesList");zones.getSelectionModel().select(0);
            f.context().getComponent(CatalogController.class).open();
            @SuppressWarnings("unchecked") var table=(DataGrid<String,RecordRow>)f.view().getRoot().lookup("#recordTable");return table;
        });
        awaitReady(next);
        onFx(()->{
            assertEquals(new ColumnWidth.Explicit(245),next.snapshotSetup().columns().stream().filter(c->c.id().equals("name")).findFirst().orElseThrow().width());
            assertNotEquals(GridApplicationConfiguration.Phase.CLOSED,f.desk().getGridConfiguration().state(new GridSetupStore.Key("dnscheck.zone-records",null)).phase());return null;
        });
    }

    @Test void quitFlushFailureAllowsCancelThenRetryWithoutSkippingDomainChecks() throws Exception {
        var fault=failingConfiguration();Fixture f=fixture(false,fault.configuration());
        f.files().response.complete(live());assertTrue(f.loaded().await(5,TimeUnit.SECONDS));
        fault.fail().set(true);onFx(()->{f.table().setColumnWidth("name",new ColumnWidth.Explicit(255));return null;});
        DialogPane first=requestQuit(f);
        onFx(()->{button(first,"Cancel quit").fire();return null;});
        CountDownLatch resumed=new CountDownLatch(1);
        onFx(()->{var root=f.view().getRoot().lookup("#zoneRoot");
            if(!root.isDisabled())resumed.countDown();else root.disabledProperty().addListener((o,b,a)->{if(!a)resumed.countDown();});return null;});
        assertTrue(resumed.await(5,TimeUnit.SECONDS));
        assertTrue(onFx(()->f.view().getRoot().getScene().getWindow().isShowing()));
        assertTrue(fault.configuration().state(new GridSetupStore.Key("dnscheck.zone-records",null)).dirty());
        DialogPane second=requestQuit(f);fault.fail().set(false);
        CountDownLatch hidden=new CountDownLatch(1);
        onFx(()->{f.view().getRoot().getScene().getWindow().showingProperty().addListener((o,b,a)->{if(!a)hidden.countDown();});button(second,"Retry").fire();return null;});
        assertTrue(hidden.await(5,TimeUnit.SECONDS));assertEquals(1,f.files().reads.get());
        assertEquals(new ColumnWidth.Explicit(255),fault.store().load(new GridSetupStore.Key("dnscheck.zone-records",null)).setup().columns().stream().filter(c->c.id().equals("name")).findFirst().orElseThrow().width());
    }

    @Test void explicitQuitWithoutGridSettingsDiscardsOnlyPendingSetupWrites() throws Exception {
        var fault=failingConfiguration();Fixture f=fixture(false,fault.configuration());
        f.files().response.complete(live());assertTrue(f.loaded().await(5,TimeUnit.SECONDS));
        String zone=Files.readString(temp.resolve("example.com.domain"));fault.fail().set(true);
        onFx(()->{f.table().setColumnWidth("name",new ColumnWidth.Explicit(265));return null;});
        DialogPane pane=requestQuit(f);CountDownLatch hidden=new CountDownLatch(1);
        onFx(()->{f.view().getRoot().getScene().getWindow().showingProperty().addListener((o,b,a)->{if(!a)hidden.countDown();});button(pane,"Quit without saving grid settings").fire();return null;});
        assertTrue(hidden.await(5,TimeUnit.SECONDS));
        assertEquals(zone,Files.readString(temp.resolve("example.com.domain")));assertEquals(1,f.files().reads.get());
        assertFalse(Files.exists(fault.store().path(new GridSetupStore.Key("dnscheck.zone-records",null))));
    }

    private record FailureConfiguration(GridApplicationConfiguration configuration,java.util.concurrent.atomic.AtomicBoolean fail,
                                        de.neitzel.gson.grid.JsonGridSetupStore store) {}
    private FailureConfiguration failingConfiguration(){
        var store=new de.neitzel.gson.grid.JsonGridSetupStore(temp.resolve("grid-settings"),new de.neitzel.gson.grid.GridSetupJson());
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        GridSetupStore wrapped=new GridSetupStore(){
            public Stored load(Key key)throws IOException{return store.load(key);}
            public String revision(Key key)throws IOException{return store.revision(key);}
            public String save(Key key,String revision,GridSetup setup)throws IOException{if(fail.get())throw new IOException("Injected disk failure");return store.save(key,revision,setup);}
        };
        return new FailureConfiguration(new GridApplicationConfiguration("DNSCheck",wrapped),fail,store);
    }
    private static Button button(DialogPane pane,String text){
        var type=pane.getButtonTypes().stream().filter(item->text.equals(item.getText())).findFirst().orElseThrow();return (Button)pane.lookupButton(type);
    }
    private static DialogPane requestQuit(Fixture fixture)throws Exception{
        CountDownLatch shown=new CountDownLatch(1);var pane=new java.util.concurrent.atomic.AtomicReference<DialogPane>();
        javafx.collections.ListChangeListener<Window> listener=change->{
            for(Window window:Window.getWindows())if(window.getScene().getRoot() instanceof DialogPane dialog&&dialog.getButtonTypes().stream().anyMatch(item->item.getText().equals("Cancel quit"))){pane.set(dialog);shown.countDown();}
        };
        onFx(()->{Window.getWindows().addListener(listener);var stage=fixture.view().getRoot().getScene().getWindow();
            stage.fireEvent(new javafx.stage.WindowEvent(stage,javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));return null;});
        try{assertTrue(shown.await(5,TimeUnit.SECONDS));return pane.get();}
        finally{onFx(()->{Window.getWindows().removeListener(listener);return null;});}
    }

    @Test void themesPreserveRecordIdentityDraftsAndDomainIndicators() throws Exception {
        Fixture f = fixture();
        f.files().response.complete(live());assertTrue(f.loaded().await(5,TimeUnit.SECONDS));
        onFx(() -> { f.table().setSort(new SortKey("name", SortKey.Direction.DESCENDING)); return null; });
        awaitReady(f.table());
        String disk = Files.readString(temp.resolve("example.com.domain"));
        var state = onFx(() -> {
            var root = f.view().getRoot();
            var field = (TextField)root.lookup("#nameserverField");field.setText("unfinished.example.net");
            var table=f.table();expandTypes(table);var items=rows(table);var selected=items.stream().filter(r -> r.kind()==RecordRow.Kind.REMOVAL).findFirst().orElseThrow();
            select(table, selected);
            table.setColumnWidth("name", new ColumnWidth.Explicit(235));
            var menu=((MenuBar)root.lookup("#fileMenuBar")).getMenus().get(1);
            var themes=(Menu)menu.getItems().getFirst();
            var dialog=new RecordDialog(root.getScene().getWindow(),"example.com",null);dialog.show();
            ((TextArea)dialog.getDialogPane().lookup("#recordRdata")).setText("unfinished-invalid-address");
            ((Button)dialog.getDialogPane().lookup("#applyRecordButton")).fire();
            return new ThemeState(root,field,table,items,selected,themes,dialog);
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
                    var root=state.root();var field=state.field();var table=state.table();var items=state.items();var selected=state.selected();var dialog=state.dialog();
                    root.applyCss();root.layout();dialog.getDialogPane().applyCss();dialog.getDialogPane().layout();
                    assertEquals(items,rows(table));assertSame(selected,table.selectedElementProperty().get());
                    assertEquals(List.of(new SortKey("name", SortKey.Direction.DESCENDING)),
                            table.stateProperty().get().displayed().sort());
                    var nameHeader = table.lookupAll(".column-cell").stream()
                            .filter(node -> "name".equals(node.getUserData())).findFirst().orElseThrow();
                    assertEquals(235, nameHeader.getBoundsInParent().getWidth(), .1);
                    assertEquals("unfinished.example.net",field.getText());assertTrue(f.desk().zone().isDirty());
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
                        table.select(null);root.applyCss();root.layout();
                        var row=table.lookupAll(".result-row").stream().filter(n -> n.getUserData()==rowItem).findFirst().orElseThrow();
                        var cells=((javafx.scene.Parent)row).lookupAll(".record-cell").stream().map(n->(Label)n).filter(c->c.getText()!=null && !c.getText().isBlank()).toList();
                        String symbol=switch(rowItem.kind()){case ENFORCED->"✓";case REMOVAL->"−";case LIVE->"●";};
                        assertTrue(cells.stream().anyMatch(c->c.getText().startsWith(symbol)));
                        for(var cell:cells)ThemeEvidence.check(theme+" "+rowItem.kind()+" normal",cell,target);
                        select(table, rowItem);row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"),true);root.applyCss();root.layout();
                        for(var cell:cells)ThemeEvidence.check(theme+" "+rowItem.kind()+" selected/focused",cell,target);
                        table.setDisable(true);root.applyCss();for(var cell:cells)ThemeEvidence.check(theme+" "+rowItem.kind()+" selected/disabled",cell,target);table.setDisable(false);
                        row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("focused"),false);
                    }
                    select(table, selected);root.applyCss();root.layout();
                    ThemeEvidence.snapshot(theme,root);ThemeEvidence.snapshot(theme+"-dialog",dialog.getDialogPane());
                    return null;
                });
            }
        } finally {onFx(() -> {state.dialog().close();return null;});}
        assertEquals(1,f.files().reads.get(),"Theme changes must not issue AXFR");
        assertEquals(disk,Files.readString(temp.resolve("example.com.domain")),"Themes must not save zone edits");
    }

    private record ThemeState(javafx.scene.Parent root, TextField field, DataGrid<String, RecordRow> table,
        List<RecordRow> items, RecordRow selected,
        Menu themes, RecordDialog dialog) {}

    private Fixture fixture() throws Exception { return fixture(false); }
    private Fixture fixture(boolean corruptSetup) throws Exception { return fixture(corruptSetup,null); }
    private Fixture fixture(boolean corruptSetup, GridApplicationConfiguration suppliedConfiguration) throws Exception {
        if(corruptSetup){
            var store=new de.neitzel.gson.grid.JsonGridSetupStore(temp.resolve("grid-settings"),new de.neitzel.gson.grid.GridSetupJson());
            Files.createDirectories(temp.resolve("grid-settings"));
            Files.writeString(store.path(new GridSetupStore.Key("dnscheck.zone-records",null)),"{malformed");
        }
        Files.writeString(temp.resolve("test.key"), """
                key "test" { algorithm hmac-sha256; secret "dGVzdA=="; };
                """);
        Files.writeString(temp.resolve("example.com.domain"), """
                {"zone":"example.com","nameserver":"ns.example.com","keyFile":"test.key","records":[
                {"name":"www","type":"A","rdata":"192.0.2.1","ttl":300},
                {"name":"old","type":"TXT","delete":true,"rdataPattern":"old*"}]}
                """);
        Fixture result = onFx(() -> {
            DeferredFiles files = new DeferredFiles();
            RecentDirectories recent = new RecentDirectories(temp.resolve("settings/config.json"));
            ApplicationContext context = new ApplicationContext(DnsCheckFxApp.class) {
                @Override public <T> T getComponent(Class<? extends T> type) {
                    if (type == ZoneFiles.class) return type.cast(files);
                    if (type == RecentDirectories.class) return type.cast(recent);
                    return super.getComponent(type);
                }
            };
            DnsCheckFxApp.configureLanguage(context, java.util.Locale.ENGLISH);
            Desk desk = context.getComponent(Desk.class);
            var configuration = suppliedConfiguration == null ? de.neitzel.gson.grid.GridSetupFiles.forApplication("DNSCheck", temp.resolve("grid-settings")) : suppliedConfiguration;
            gridConfigurations.add(configuration);
            desk.setGridConfiguration(configuration);
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
            @SuppressWarnings("unchecked") DataGrid<String, RecordRow> table = (DataGrid<String, RecordRow>) view.getRoot().lookup("#recordTable");
            Label status = (Label) view.getRoot().lookup("#liveStatus");
            CountDownLatch loaded = new CountDownLatch(1);
            table.stateProperty().addListener((observable, before, after) -> {
                if (after.phase() == GridCoordinator.Phase.READY && !status.getText().isBlank() && !status.getText().startsWith("Loading")) loaded.countDown();
            });
            return new Fixture(context, desk, view, files, table, status, loaded);
        });
        if(corruptSetup)return result;
        awaitReady(result.table());
        CountDownLatch liveStarted = new CountDownLatch(1);
        onFx(() -> {
            if (result.status().getText().startsWith("Loading")) liveStarted.countDown();
            else result.status().textProperty().addListener((o,b,a) -> {if(a.startsWith("Loading"))liveStarted.countDown();});
            return null;
        });
        assertTrue(liveStarted.await(5, TimeUnit.SECONDS));
        return result;
    }
    private static void expandTypes(DataGrid<String, RecordRow> table) {
        rows(table).stream().map(RecordRow::type).distinct().forEach(type -> table.setExpanded(
                new GroupPath(List.of(new GroupPath.Segment("type", new FilterValue.Text(type)))), true));
        table.applyCss(); table.layout();
    }
    private static List<RecordRow> rows(DataGrid<String, RecordRow> table) {
        var result = table.stateProperty().get().result();
        return result == null ? List.of() : result.rows().stream().map(GridRow::model).toList();
    }
    private static void select(DataGrid<String, RecordRow> table, RecordRow selected) {
        table.select(table.stateProperty().get().result().rows().stream()
                .filter(row -> row.model() == selected).findFirst().orElseThrow().key());
    }
    private static void awaitReady(DataGrid<String, RecordRow> table) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        onFx(() -> {
            if (table.stateProperty().get().phase() == GridCoordinator.Phase.READY) ready.countDown();
            else table.stateProperty().addListener((observable, before, after) -> {
                if (after.phase() == GridCoordinator.Phase.READY) ready.countDown();
            });
            return null;
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS), "Grid did not publish a result");
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
                           DataGrid<String, RecordRow> table, Label status, CountDownLatch loaded) {}
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
