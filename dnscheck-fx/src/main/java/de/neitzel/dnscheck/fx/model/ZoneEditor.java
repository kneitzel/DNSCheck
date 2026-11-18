package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A local definition being edited. The original source path is retained on save. */
@Getter
@Setter
public class ZoneEditor {
    private String zone = "";
    private String nameserver = "";
    private String keyFile = "";
    private String sourcePath = "";
    private List<RecordEditor> records = new ArrayList<>();
    private List<RecordRow> recordRows = new ArrayList<>();
    private String outcome = "";
    private boolean applying;
    @Setter(AccessLevel.NONE)
    private ZoneDefinition diskDefinition;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ZoneDefinition savedDefinition;

    public static ZoneEditor from(ZoneDefinition definition) {
        ZoneEditor editor = new ZoneEditor();
        editor.setZone(definition.zone());
        editor.setNameserver(definition.nameserver());
        editor.setKeyFile(definition.keyFile());
        editor.setSourcePath(definition.sourcePath().toString());
        editor.setRecords(new ArrayList<>(definition.records().stream().map(RecordEditor::from).toList()));
        editor.markSaved();
        editor.diskDefinition = definition;
        return editor;
    }

    public void markSaved() {
        savedDefinition = toDefinition();
        diskDefinition = savedDefinition;
    }

    /** Accept a completed record operation without replacing the editor's ViewModel identity. */
    public void acceptDefinition(ZoneDefinition definition) {
        nameserver = definition.nameserver();
        keyFile = definition.keyFile();
        records.clear();
        definition.records().stream().map(RecordEditor::from).forEach(records::add);
        markSaved();
        diskDefinition = definition;
    }

    public boolean isDirty() {
        try {
            return !toDefinition().equals(savedDefinition);
        } catch (RuntimeException invalidEdits) {
            return true;
        }
    }

    public ZoneDefinition toDefinition() {
        String server = nameserver == null ? "" : nameserver.trim();
        String key = keyFile == null ? "" : keyFile.trim();
        List<RecordSpec> specs = new ArrayList<>();
        int index = 1;
        for (RecordEditor record : records) {
            try {
                specs.add(record.toSpec());
            } catch (RuntimeException failure) {
                String detail = failure.getMessage() == null ? "invalid record" : failure.getMessage();
                throw new IllegalArgumentException("Record " + index + ": " + detail, failure);
            }
            index++;
        }
        return new ZoneDefinition(zone, server, key, specs, Path.of(sourcePath));
    }

}
