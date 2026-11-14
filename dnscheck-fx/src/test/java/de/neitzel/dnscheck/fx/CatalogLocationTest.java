package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.fx.model.CatalogSession;
import de.neitzel.dnscheck.fx.view.CatalogController;
import de.neitzel.dnscheck.fx.view.ZoneController;
import de.neitzel.fx.view.LoadedView;
import de.neitzel.fx.view.ViewLoader;
import de.neitzel.injection.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real FXML controllers; only the network reconciliation boundary is replaced. */
class CatalogLocationTest {
    @TempDir Path temp;

    @BeforeAll
    static void toolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "true,true,false", "true,false,true", "true,false,false",
            "false,true,true", "false,true,false", "false,false,true", "false,false,false"})
    void directoryEditsRequireReloadBeforeReconciling(boolean zoneAction, boolean changeZones, boolean dryRun)
            throws Exception {
        Fixture f = fixture();
        String originalA = Files.readString(f.fileA());
        String originalB = Files.readString(f.fileB());
        onFx(() -> {
            f.catalogController().open();
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            TextField directory = (TextField) f.view().getRoot().lookup(changeZones ? "#zonesField" : "#keysField");
            String originalDirectory = directory.getText();
            directory.setText(changeZones ? f.zonesB().toString() : f.keysB().toString());
            run(f, zoneAction, dryRun);
            // Both controllers must report the blocked action in the visible notice.
            assertTrue(f.view().getRoot().lookupAll(".label").stream()
                    .filter(javafx.scene.control.Label.class::isInstance)
                    .map(javafx.scene.control.Label.class::cast)
                    .anyMatch(label -> label.getText() != null && label.getText().contains("Reload")));
            assertEquals(originalA, Files.readString(f.fileA()), "Blocked reconciliation must not save first");
            assertEquals(originalB, Files.readString(f.fileB()));
            assertTrue(f.files().calls.isEmpty());
            directory.setText(originalDirectory);
            run(f, zoneAction, dryRun); // Rejection must not leave the controller busy.
            return null;
        });
        Invocation call = f.files().calls.poll(10, TimeUnit.SECONDS);
        assertNotNull(call);
        assertEquals(f.zonesA(), call.zones());
        assertEquals(f.keysA(), call.keys());
        assertEquals(dryRun, call.dryRun());
        assertEquals(originalB, Files.readString(f.fileB()));
        if (zoneAction) assertTrue(Files.readString(f.fileA()).contains("edited.example.com"));
        else assertEquals(originalA, Files.readString(f.fileA()));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void invalidPathsDoNotSaveOrLeaveReconciliationBusy(boolean zoneAction, boolean changeZones)
            throws Exception {
        Fixture f = fixture();
        String original = Files.readString(f.fileA());
        onFx(() -> {
            f.catalogController().open();
            ((TextField) f.view().getRoot().lookup("#nameserverField")).setText("edited.example.com");
            String field = changeZones ? "zonesDirectory" : "keysDirectory";
            String old = changeZones ? f.zonesA().toString() : f.keysA().toString();
            Attributes.set(f.view().getViewModel(), field, "invalid\0path");
            run(f, zoneAction, true);
            assertEquals(original, Files.readString(f.fileA()));
            assertTrue(f.files().calls.isEmpty());
            Attributes.set(f.view().getViewModel(), field, old);
            run(f, zoneAction, true);
            return null;
        });
        assertNotNull(f.files().calls.poll(10, TimeUnit.SECONDS));
    }

    @Test
    void changedOrFailedReloadCannotOpenAStaleSelection() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            TextField zones = (TextField) f.view().getRoot().lookup("#zonesField");
            zones.setText(f.zonesB().toString());
            f.catalogController().open();
            assertNull(f.desk().zone());
            zones.setText(temp.resolve("missing").toString());
            f.catalogController().reload();
            f.catalogController().open();
            assertNull(f.desk().zone());
            zones.setText(f.zonesA().toString());
            f.catalogController().open();
            assertEquals(f.fileA(), Path.of(f.desk().zone().getSourcePath()));
            return null;
        });
    }

    @Test
    void successfulReloadReplacesContextAndClearsOldEditorAndSelection() throws Exception {
        Fixture f = fixture();
        onFx(() -> {
            f.catalogController().open();
            ((TextField) f.view().getRoot().lookup("#zonesField")).setText(f.zonesB().toString());
            ((TextField) f.view().getRoot().lookup("#keysField")).setText(f.keysB().toString());
            f.catalogController().reload();
            assertNull(f.desk().zone());
            assertNull(f.desk().catalog().getSelected());
            Attributes.set(f.view().getViewModel(), "selected", f.desk().catalog().getEntries().getFirst());
            f.catalogController().open();
            assertEquals(f.fileB(), Path.of(f.desk().zone().getSourcePath()));
            f.context().getComponent(ZoneController.class).dryRun();
            return null;
        });
        Invocation call = f.files().calls.poll(10, TimeUnit.SECONDS);
        assertNotNull(call);
        assertEquals(f.zonesB(), call.zones());
        assertEquals(f.keysB(), call.keys());
    }

    private static void run(Fixture f, boolean zone, boolean dry) {
        if (zone) {
            ZoneController controller = f.context().getComponent(ZoneController.class);
            if (dry) controller.dryRun(); else controller.reconcile();
        } else {
            if (dry) f.catalogController().dryRun(); else f.catalogController().reconcile();
        }
    }

    private Fixture fixture() throws Exception {
        Path zonesA = temp.resolve("a");
        Path zonesB = temp.resolve("b");
        Path keysA = Files.createDirectories(temp.resolve("keys-a"));
        Path keysB = Files.createDirectories(temp.resolve("keys-b"));
        Path fileA = zoneFile(zonesA, "ns-a.example.com");
        Path fileB = zoneFile(zonesB, "ns-b.example.com");
        return onFx(() -> {
            RecordingFiles files = new RecordingFiles();
            ApplicationContext context = new ApplicationContext(DnsCheckFxApp.class) {
                @Override
                public <T> T getComponent(Class<? extends T> type) {
                    if (type == ZoneFiles.class) {
                        return type.cast(files);
                    }
                    return super.getComponent(type);
                }
            };
            Desk desk = context.getComponent(Desk.class);
            CatalogSession session = new CatalogSession();
            session.setZonesDirectory(zonesA.toString());
            session.setKeysDirectory(keysA.toString());
            desk.prepare(session, context);
            LoadedView view = ViewLoader.load(CatalogController.class.getResource("Catalog.fxml"), session, context);
            Attributes.set(view.getViewModel(), "selected", session.getEntries().getFirst());
            return new Fixture(context, desk, view, context.getComponent(CatalogController.class), files,
                    zonesA, zonesB, keysA, keysB, fileA, fileB);
        });
    }

    private static Path zoneFile(Path zones, String server) throws Exception {
        Path file = Files.createDirectories(zones.resolve("admin.u")).resolve("example.com.json");
        Files.writeString(file, """
                {"zone":"example.com", "nameserver":"%s", "keyFile":"admin.u/example.com.key", "records":[]}
                """.formatted(server));
        return file;
    }

    private static <T> T onFx(Callable<T> check) throws Exception {
        FutureTask<T> task = new FutureTask<>(check);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private record Fixture(ApplicationContext context, Desk desk, LoadedView view,
                           CatalogController catalogController, RecordingFiles files,
                           Path zonesA, Path zonesB, Path keysA, Path keysB, Path fileA, Path fileB) {}
    private record Invocation(Path zones, Path keys, boolean dryRun) {}

    private static class RecordingFiles extends ZoneFiles {
        final LinkedBlockingQueue<Invocation> calls = new LinkedBlockingQueue<>();
        @Override
        public ZoneReconciliationResult reconcile(Path zones, Path keys, String owner, String zone, boolean dryRun) {
            calls.add(new Invocation(zones, keys, dryRun));
            return ZoneReconciliationResult.failed(owner + "/" + zone, "Offline test result");
        }
    }
}
