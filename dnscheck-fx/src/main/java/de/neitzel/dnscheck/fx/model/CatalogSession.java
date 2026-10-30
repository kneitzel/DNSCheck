package de.neitzel.dnscheck.fx.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The zone catalog open on the desk.
 * <p>
 * Directory fields are edited in the View and written through to this Model. {@code entries} is
 * the list the combo box watches after the View has loaded.
 */
public class CatalogSession {

    private String zonesDirectory = "";
    private String keysDirectory = "";
    private List<CatalogEntryModel> entries = new ArrayList<>();
    private CatalogEntryModel selected;
    private String outcome = "";

    /**
     * Creates an empty catalog session.
     */
    public CatalogSession() {
    }

    /**
     * Returns the zone catalog directory.
     *
     * @return the directory
     */
    public String getZonesDirectory() {
        return zonesDirectory;
    }

    /**
     * Sets the zone catalog directory.
     *
     * @param zonesDirectory the directory
     */
    public void setZonesDirectory(String zonesDirectory) {
        this.zonesDirectory = zonesDirectory;
    }

    /**
     * Returns the TSIG keys directory.
     *
     * @return the directory
     */
    public String getKeysDirectory() {
        return keysDirectory;
    }

    /**
     * Sets the TSIG keys directory.
     *
     * @param keysDirectory the directory
     */
    public void setKeysDirectory(String keysDirectory) {
        this.keysDirectory = keysDirectory;
    }

    /**
     * Returns the catalog entries.
     * <p>
     * After the View has loaded, this is the list the View is watching.
     *
     * @return the entries
     */
    public List<CatalogEntryModel> getEntries() {
        return entries;
    }

    /**
     * Sets the catalog entries.
     *
     * @param entries the entries
     */
    public void setEntries(List<CatalogEntryModel> entries) {
        this.entries = entries;
    }

    /**
     * Returns the catalog entry chosen in the combo box.
     *
     * @return the selected entry, or {@code null} when none is selected
     */
    public CatalogEntryModel getSelected() {
        return selected;
    }

    /**
     * Sets the catalog entry chosen in the combo box.
     *
     * @param selected the selected entry
     */
    public void setSelected(CatalogEntryModel selected) {
        this.selected = selected;
    }

    /**
     * Returns the latest reconciliation text.
     *
     * @return the text
     */
    public String getOutcome() {
        return outcome;
    }

    /**
     * Sets the latest reconciliation text.
     *
     * @param outcome the text
     */
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
