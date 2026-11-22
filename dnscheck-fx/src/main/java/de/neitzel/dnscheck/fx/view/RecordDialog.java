package de.neitzel.dnscheck.fx.view;

import de.neitzel.fx.i18n.Messages;
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
    private final CheckBox removal = new CheckBox();
    private final CheckBox store = new CheckBox();
    private final CheckBox online = new CheckBox();
    private final Label error = new Label();
    private boolean editing;
    private RecordChange validated;

    public RecordDialog(Window owner, String zone, RecordRow original) {
        this(owner, zone, original, de.neitzel.dnscheck.fx.DnsCheckFxApp.languages(null).forClass(RecordDialog.class));
    }

    RecordDialog(Window owner, String zone, RecordRow original, Messages resources) {
        this.zone = zone;
        this.original = original;
        online.setText(resources.text("dialog.applyLive"));
        store.setText(resources.text("dialog.store"));
        removal.setText(resources.text("dialog.remove"));
        if (owner != null) initOwner(owner);
        setTitle(original == null ? resources.text("dialog.newRecord") : resources.text("dialog.details"));
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
        ttl.setPromptText(resources.text("dialog.ttlHint"));
        pattern.setText(draft.getRdataPattern()); pattern.setId("recordPattern");
        pattern.setPromptText(resources.text("dialog.patternHint"));
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
        fields.addRow(0, new Label(resources.text("dialog.name")), name);
        fields.addRow(1, new Label(resources.text("dialog.type")), type);
        fields.addRow(2, new Label(resources.text("dialog.value")), rdata);
        fields.addRow(3, new Label(resources.text("dialog.ttl")), ttl);
        fields.addRow(4, new Label(resources.text("dialog.pattern")), pattern);
        String details = original == null ? "New record in " + zone : original.status() + "\n"
                + (original.stored() == null ? "Not stored in the domain file."
                    : "File value: " + original.value() + "\nFile TTL policy: " + original.ttl())
                + (original.live() == null ? "\nNo live value available."
                    : "\nLive value: " + original.live().rdata() + "\nLive TTL: " + original.live().ttl());
        Label description = new Label(details);
        description.setWrapText(true); description.setId("recordDetails");
        Label behavior = new Label(resources.text("dialog.behavior"));
        behavior.setWrapText(true);
        error.setWrapText(true); error.getStyleClass().add("dns-error"); error.setId("recordError");
        VBox content = new VBox(12, description, fields, removal, store, online, behavior, error);
        content.setPadding(new Insets(12)); content.setPrefWidth(610);
        getDialogPane().setContent(content);
        ButtonType edit = new ButtonType(resources.text("dialog.edit"), ButtonBar.ButtonData.OTHER);
        ButtonType apply = new ButtonType(resources.text("dialog.apply"), ButtonBar.ButtonData.OK_DONE);
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
