package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.ZoneDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One zone definition open for editing.
 * <p>
 * The zone name and owner folder come from the catalog entry. Nameserver, key file, and records
 * are edited in the View and written through to this Model.
 */
public class ZoneEditor {

    private String ownerFolder = "";
    private String zone = "";
    private String nameserver = "";
    private String keyFile = "";
    private String sourcePath = "";
    private List<RecordEditor> records = new ArrayList<>();
    private String outcome = "";

    /**
     * Creates an empty zone editor.
     */
    public ZoneEditor() {
    }

    /**
     * Copies a zone definition into an editor.
     *
     * @param ownerFolder the owner folder that holds the file
     * @param definition  the zone definition
     * @return the editor
     */
    public static ZoneEditor from(String ownerFolder, ZoneDefinition definition) {
        ZoneEditor editor = new ZoneEditor();
        editor.setOwnerFolder(ownerFolder);
        editor.setZone(definition.zone());
        editor.setNameserver(definition.nameserver());
        editor.setKeyFile(definition.keyFile());
        editor.setSourcePath(definition.sourcePath().toString());
        List<RecordEditor> rows = new ArrayList<>();
        for (RecordSpec record : definition.records()) {
            rows.add(RecordEditor.from(record));
        }
        editor.setRecords(rows);
        return editor;
    }

    /**
     * Returns the zone definition this editor would write.
     *
     * @return the zone definition
     */
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

    /**
     * Returns the owner folder.
     *
     * @return the owner folder
     */
    public String getOwnerFolder() {
        return ownerFolder;
    }

    /**
     * Sets the owner folder.
     *
     * @param ownerFolder the owner folder
     */
    public void setOwnerFolder(String ownerFolder) {
        this.ownerFolder = ownerFolder;
    }

    /**
     * Returns the zone name.
     *
     * @return the zone name
     */
    public String getZone() {
        return zone;
    }

    /**
     * Sets the zone name.
     *
     * @param zone the zone name
     */
    public void setZone(String zone) {
        this.zone = zone;
    }

    /**
     * Returns the nameserver.
     *
     * @return the nameserver
     */
    public String getNameserver() {
        return nameserver;
    }

    /**
     * Sets the nameserver.
     *
     * @param nameserver the nameserver
     */
    public void setNameserver(String nameserver) {
        this.nameserver = nameserver;
    }

    /**
     * Returns the scoped key file path.
     *
     * @return the key file path
     */
    public String getKeyFile() {
        return keyFile;
    }

    /**
     * Sets the scoped key file path.
     *
     * @param keyFile the key file path
     */
    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    /**
     * Returns the zone definition file.
     *
     * @return the file path
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * Sets the zone definition file.
     *
     * @param sourcePath the file path
     */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    /**
     * Returns the records.
     * <p>
     * After the View has loaded, this is the list the View is watching.
     *
     * @return the records
     */
    public List<RecordEditor> getRecords() {
        return records;
    }

    /**
     * Sets the records.
     *
     * @param records the records
     */
    public void setRecords(List<RecordEditor> records) {
        this.records = records;
    }

    /**
     * Returns the latest reconciliation text for this zone.
     *
     * @return the text
     */
    public String getOutcome() {
        return outcome;
    }

    /**
     * Sets the latest reconciliation text for this zone.
     *
     * @param outcome the text
     */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
