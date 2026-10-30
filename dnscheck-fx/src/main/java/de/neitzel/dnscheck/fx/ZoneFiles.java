package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.reconcile.DefaultReconciliationEngine;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.core.zone.ZoneCatalog;
import de.neitzel.dnscheck.core.zone.ZoneCatalogEntry;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionStore;
import de.neitzel.dnscheck.fx.model.CatalogEntryModel;
import de.neitzel.dnscheck.fx.model.ZoneEditor;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the zone catalog on disk, and runs one reconciliation.
 */
@Singleton
public class ZoneFiles {

    private final ZoneDefinitionStore store = new ZoneDefinitionStore();

    /**
     * Creates the catalog access used by the desk.
     */
    public ZoneFiles() {
    }

    /**
     * Lists catalog entries, including files that failed to load.
     *
     * @param zonesDirectory the zone catalog directory
     * @return the entries, in catalog order
     * @throws IOException when the directory cannot be scanned
     */
    public List<CatalogEntryModel> load(Path zonesDirectory) throws IOException {
        List<CatalogEntryModel> entries = new ArrayList<>();
        for (ZoneCatalogEntry entry : new ZoneCatalog(zonesDirectory).introspect()) {
            entries.add(CatalogEntryModel.from(entry));
        }
        return entries;
    }

    /**
     * Loads one valid catalog entry for editing.
     *
     * @param zonesDirectory the zone catalog directory
     * @param entry          the catalog entry
     * @return the zone editor
     * @throws IOException when the zone definition cannot be read
     */
    public ZoneEditor open(Path zonesDirectory, CatalogEntryModel entry) throws IOException {
        return new ZoneCatalog(zonesDirectory)
                .find(entry.getOwnerFolder(), entry.getZone())
                .map(definition -> ZoneEditor.from(entry.getOwnerFolder(), definition))
                .orElseThrow(() -> new IOException("No zone definition for " + entry.getCatalogRef()));
    }

    /**
     * Writes the editor back to its zone definition file.
     *
     * @param editor the zone editor
     * @throws IOException when the file cannot be written
     */
    public void save(ZoneEditor editor) throws IOException {
        store.write(editor.toDefinition());
    }

    /**
     * Reconciles one catalog entry. A dry-run reports drift and does not send nsupdate.
     *
     * @param zonesDirectory the zone catalog directory
     * @param keysDirectory  the TSIG keys directory
     * @param ownerFolder    the owner folder
     * @param zone           the zone name
     * @param dryRun         {@code true} to leave live DNS unchanged
     * @return the zone outcome
     */
    public ZoneReconciliationResult reconcile(
            Path zonesDirectory, Path keysDirectory, String ownerFolder, String zone, boolean dryRun) {
        return new DefaultReconciliationEngine(zonesDirectory, keysDirectory)
                .reconcileCatalogEntry(ownerFolder, zone, dryRun);
    }
}
