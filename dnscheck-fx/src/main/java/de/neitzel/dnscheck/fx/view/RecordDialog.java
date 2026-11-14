package de.neitzel.dnscheck.fx.view;

import de.neitzel.dnscheck.fx.ZoneFiles;
import de.neitzel.dnscheck.fx.model.RecordChange;
import de.neitzel.dnscheck.fx.model.RecordEditor;
import de.neitzel.dnscheck.fx.model.RecordRow;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** One details/edit dialog for stored entries, live records, removal markers and new records. */
public class RecordDialog extends Dialog<RecordChange> {
    private final String zone;
    private final RecordRow original;
    private final TextField name = new TextField();
    private final TextField type = new TextField();
    private final TextArea rdata = new TextArea();
    private final TextField ttl = new TextField();
    private final TextField pattern = new TextField();
    private final CheckBox removal = new CheckBox("Removal entry (delete matching records)");
    private final CheckBox store = new CheckBox("Store entry in domain file (enforce)");
    private final CheckBox online = new CheckBox("Apply change to live DNS now");
    private final Label error = new Label();
    private boolean editing;
    private RecordChange validated;

    public RecordDialog(Window owner, String zone, RecordRow original) {
        this.zone = zone;
        this.original = original;
        if (owner != null) initOwner(owner);
        setTitle(original == null ? "New DNS record" : "DNS record details");
        setResizable(true);
        RecordEditor draft = original == null ? new RecordEditor() : original.stored() != null
                ? RecordEditor.from(original.stored()) : new RecordEditor();
        if (original == null) {
            draft.setName("@");
            draft.setType("A");
        } else if (original.stored() == null) {
            draft.setName(original.live().nameFqdn());
            draft.setType(original.live().type());
            draft.setRdata(original.live().rdata());
            draft.setTtl(Long.toString(original.live().ttl()));
        }
        name.setText(draft.getName()); name.setId("recordName");
        type.setText(draft.getType()); type.setId("recordType");
        rdata.setText(draft.getRdata()); rdata.setId("recordRdata");
        rdata.setPrefRowCount(3); rdata.setWrapText(true);
        ttl.setText(draft.getTtl()); ttl.setId("recordTtl");
        ttl.setPromptText("Empty: ignore TTL; -1: zone default");
        pattern.setText(draft.getRdataPattern()); pattern.setId("recordPattern");
        pattern.setPromptText("Empty matches all values; * and ? are glob wildcards");
        removal.setSelected(draft.isDeletionMarker()); removal.setId("recordRemoval");
        store.setSelected(original == null || original.stored() != null); store.setId("recordStore");
        online.setSelected(false); online.setId("recordOnline");
        GridPane fields = new GridPane();
        fields.setHgap(12); fields.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints(115);
        ColumnConstraints inputs = new ColumnConstraints();
        inputs.setHgrow(Priority.ALWAYS);
        inputs.setMinWidth(0);
        fields.getColumnConstraints().addAll(labels, inputs);
        rdata.setMinWidth(0);
        fields.addRow(0, new Label("Name"), name);
        fields.addRow(1, new Label("Type"), type);
        fields.addRow(2, new Label("Value"), rdata);
        fields.addRow(3, new Label("TTL policy"), ttl);
        fields.addRow(4, new Label("Removal pattern"), pattern);
        String details = original == null ? "New record in " + zone : original.status() + "\n"
                + (original.stored() == null ? "Not stored in the domain file."
                    : "File value: " + original.value() + "\nFile TTL policy: " + original.ttl())
                + (original.live() == null ? "\nNo live value available."
                    : "\nLive value: " + original.live().rdata() + "\nLive TTL: " + original.live().ttl());
        Label description = new Label(details);
        description.setWrapText(true); description.setId("recordDetails");
        Label behavior = new Label("File storage and immediate DNS changes are independent. Unchecking file storage removes an existing file entry; it does not delete live DNS. A removal entry applies its pattern when online changes are selected.");
        behavior.setWrapText(true);
        error.setWrapText(true); error.setStyle("-fx-text-fill: #a12622;"); error.setId("recordError");
        VBox content = new VBox(12, description, fields, removal, store, online, behavior, error);
        content.setPadding(new Insets(12)); content.setPrefWidth(610);
        getDialogPane().setContent(content);
        ButtonType edit = new ButtonType("Edit", ButtonBar.ButtonData.OTHER);
        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(edit, apply, ButtonType.CANCEL);
        Button editButton = (Button) getDialogPane().lookupButton(edit);
        Button applyButton = (Button) getDialogPane().lookupButton(apply);
        editButton.setId("editRecordButton"); applyButton.setId("applyRecordButton");
        editing = original == null;
        boolean readOnly = original != null && original.readOnly();
        editButton.setDisable(editing || readOnly);
        applyButton.setDisable(!editing || readOnly);
        if (readOnly) description.setText(details + "\nSOA records are read-only.");
        editButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); editing = true; updateFields(); editButton.setDisable(true); applyButton.setDisable(false);
        });
        removal.selectedProperty().addListener((observable, before, after) -> updateFields());
        applyButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                RecordEditor value = new RecordEditor();
                value.setName(name.getText()); value.setType(type.getText()); value.setRdata(rdata.getText());
                value.setTtl(ttl.getText()); value.setDeletionMarker(removal.isSelected()); value.setRdataPattern(pattern.getText());
                validated = new RecordChange(original, ZoneFiles.validateRecord(zone, value.toSpec()),
                        store.isSelected(), online.isSelected());
            } catch (Exception failure) {
                validated = null; error.setText(failure.getMessage()); event.consume();
            }
        });
        setResultConverter(button -> button == apply ? validated : null);
        updateFields();
    }

    private void updateFields() {
        name.setEditable(editing); type.setEditable(editing); rdata.setEditable(editing); ttl.setEditable(editing); pattern.setEditable(editing);
        removal.setDisable(!editing); store.setDisable(!editing); online.setDisable(!editing);
        rdata.setDisable(removal.isSelected()); ttl.setDisable(removal.isSelected()); pattern.setDisable(!removal.isSelected());
    }
}
