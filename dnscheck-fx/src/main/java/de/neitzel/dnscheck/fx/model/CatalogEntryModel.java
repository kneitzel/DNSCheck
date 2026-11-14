package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.zone.ZoneCatalogEntry;

/**
 * One zone catalog entry shown in the catalog table.
 */
public class CatalogEntryModel {

    /**
     * The owner folder that holds the zone file.
     */
    private String ownerFolder = "";

    /**
     * The zone name, which is the file name.
     */
    private String zone = "";

    /**
     * The nameserver the catalog entry names.
     */
    private String nameserver = "";

    /**
     * The key file the catalog entry names.
     */
    private String keyFile = "";

    /**
     * Whether the zone file loaded. An invalid file stays in the list with {@link #error}.
     */
    private boolean valid;

    /**
     * The load error, or an empty string when the entry is valid.
     */
    private String error = "";

    /**
     * {@code owner/zoneName}, used in notices.
     */
    private String catalogRef = "";

    /**
     * Creates an empty entry.
     */
    public CatalogEntryModel() {
    }

    /**
     * Copies one introspected catalog entry.
     *
     * @param entry the catalog entry
     * @return the Model
     */
    public static CatalogEntryModel from(ZoneCatalogEntry entry) {
        CatalogEntryModel model = new CatalogEntryModel();
        model.setOwnerFolder(entry.ownerFolder());
        model.setZone(entry.zone());
        model.setNameserver(text(entry.nameserver()));
        model.setKeyFile(text(entry.keyFile()));
        model.setValid(entry.valid());
        model.setError(text(entry.error()));
        model.setCatalogRef(entry.catalogRef());
        return model;
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
     * Returns whether the zone definition loaded.
     *
     * @return {@code true} when the entry is valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Sets whether the zone definition loaded.
     *
     * @param valid {@code true} when the entry is valid
     */
    public void setValid(boolean valid) {
        this.valid = valid;
    }

    /**
     * Returns the load error.
     *
     * @return the error text, or an empty string when the entry is valid
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the load error.
     *
     * @param error the error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Returns {@code owner/zoneName}.
     *
     * @return the catalog ref
     */
    public String getCatalogRef() {
        return catalogRef;
    }

    /**
     * Sets {@code owner/zoneName}.
     *
     * @param catalogRef the catalog ref
     */
    public void setCatalogRef(String catalogRef) {
        this.catalogRef = catalogRef;
    }

    /**
     * Returns {@code owner/zoneName}.
     *
     * @return the catalog ref
     */
    @Override
    public String toString() {
        return catalogRef;
    }

    /**
     * Returns the value, or an empty string when it is null.
     *
     * @param value the value
     * @return the value, or {@code ""}
     */
    private static String text(String value) {
        return value == null ? "" : value;
    }
}
