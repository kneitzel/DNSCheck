package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.fx.model.ZoneEditor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Save, explicitly discard, or cancel before replacing an edited definition. */
public final class UnsavedChanges {
    private UnsavedChanges() {}

    public static boolean confirm(ZoneEditor editor, ZoneFiles files, Window owner) {
        if (editor != null && editor.isApplying()) {
            Alert pending = new Alert(Alert.AlertType.INFORMATION, "Wait for the current record change to finish.", ButtonType.OK);
            if (owner != null) pending.initOwner(owner);
            pending.showAndWait();
            return false;
        }
        if (editor == null || !editor.isDirty()) return true;
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Save changes to " + editor.getZone() + " before continuing?", save, discard, ButtonType.CANCEL);
        alert.setHeaderText("Unsaved changes");
        if (owner != null) alert.initOwner(owner);
        ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == discard) return true;
        if (choice != save) return false;
        try {
            files.save(editor);
            return true;
        } catch (Exception failure) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Could not save: " + failure.getMessage(), ButtonType.OK);
            if (owner != null) error.initOwner(owner);
            error.showAndWait();
            return false;
        }
    }
}
