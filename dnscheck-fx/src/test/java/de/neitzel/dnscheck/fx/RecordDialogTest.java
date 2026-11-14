package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.model.*;
import de.neitzel.dnscheck.fx.model.*;
import de.neitzel.dnscheck.fx.view.RecordDialog;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordDialogTest {
    @BeforeAll static void toolkit() throws Exception {
        Platform.setImplicitExit(false);
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException running) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }
    @Test void newRecordDefaultsToFileOnlyAndUsesTheSameDialog() throws Exception {
        onFx(() -> {
            var dialog = new RecordDialog(null, "example.com", null);
            assertTrue(box(dialog, "recordStore").isSelected());
            assertFalse(box(dialog, "recordOnline").isSelected());
            ((TextField) dialog.getDialogPane().lookup("#recordName")).setText("www");
            ((TextArea) dialog.getDialogPane().lookup("#recordRdata")).setText("192.0.2.1");
            Platform.runLater(() -> button(dialog, "applyRecordButton").fire());
            RecordChange result = dialog.showAndWait().orElseThrow();
            assertTrue(result.store()); assertFalse(result.online());
            assertEquals("www.example.com.", result.replacement().nameFqdn());
            return null;
        });
    }
    @Test void existingRecordOpensReadOnlyThenAllowsIndependentOnlineOnlyEdit() throws Exception {
        onFx(() -> {
            var live = new DnsRecord("www.example.com.", "A", "192.0.2.1", 300);
            var dialog = new RecordDialog(null, "example.com", new RecordRow(null, live, "Live only"));
            assertFalse(((TextField) dialog.getDialogPane().lookup("#recordName")).isEditable());
            assertTrue(button(dialog, "applyRecordButton").isDisabled());
            Platform.runLater(() -> {
                button(dialog, "editRecordButton").fire();
                assertTrue(dialog.isShowing());
                assertTrue(((TextField) dialog.getDialogPane().lookup("#recordName")).isEditable());
                box(dialog, "recordOnline").setSelected(true);
                ((TextArea) dialog.getDialogPane().lookup("#recordRdata")).setText("192.0.2.2");
                button(dialog, "applyRecordButton").fire();
            });
            RecordChange result = dialog.showAndWait().orElseThrow();
            assertFalse(result.store()); assertTrue(result.online());
            assertEquals("192.0.2.2", result.replacement().rdata());
            return null;
        });
    }
    @Test void invalidRdataKeepsTheDialogOpenAndCancelHasNoResult() throws Exception {
        onFx(() -> {
            var dialog = new RecordDialog(null, "example.com", null);
            ((TextArea) dialog.getDialogPane().lookup("#recordRdata")).setText("invalid-address");
            Platform.runLater(() -> {
                button(dialog, "applyRecordButton").fire();
                assertTrue(dialog.isShowing());
                assertFalse(((Label) dialog.getDialogPane().lookup("#recordError")).getText().isBlank());
                ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
            });
            assertTrue(dialog.showAndWait().isEmpty());
            return null;
        });
    }
    @Test void removalModeUsesPatternAndDisablesValueAndTtl() throws Exception {
        onFx(() -> {
            var dialog = new RecordDialog(null, "example.com", null);
            ((TextField) dialog.getDialogPane().lookup("#recordName")).setText("old");
            ((TextField) dialog.getDialogPane().lookup("#recordType")).setText("TXT");
            box(dialog, "recordRemoval").setSelected(true);
            ((TextField) dialog.getDialogPane().lookup("#recordPattern")).setText("old*");
            assertTrue(dialog.getDialogPane().lookup("#recordRdata").isDisabled());
            assertTrue(dialog.getDialogPane().lookup("#recordTtl").isDisabled());
            Platform.runLater(() -> button(dialog, "applyRecordButton").fire());
            var result = dialog.showAndWait().orElseThrow();
            assertTrue(result.replacement().deletionMarker());
            assertEquals("old*", result.replacement().rdataPattern());
            assertNull(result.replacement().rdata());
            return null;
        });
    }
    @Test void soaDetailsAreVisibleButCannotBeEdited() throws Exception {
        onFx(() -> {
            var soa = new DnsRecord("example.com.", "SOA", "ns.example.com. hostmaster.example.com. 1 60 60 60 60", 300);
            var dialog = new RecordDialog(null, "example.com", new RecordRow(null, soa, "Live only"));
            assertTrue(button(dialog, "editRecordButton").isDisabled());
            assertTrue(button(dialog, "applyRecordButton").isDisabled());
            assertTrue(((Label) dialog.getDialogPane().lookup("#recordDetails")).getText().contains("Live TTL: 300"));
            return null;
        });
    }
    private static CheckBox box(RecordDialog dialog, String id) { return (CheckBox) dialog.getDialogPane().lookup("#" + id); }
    private static Button button(RecordDialog dialog, String id) {
        return dialog.getDialogPane().getButtonTypes().stream().map(type -> (Button) dialog.getDialogPane().lookupButton(type))
                .filter(button -> id.equals(button.getId())).findFirst().orElseThrow();
    }
    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
}
