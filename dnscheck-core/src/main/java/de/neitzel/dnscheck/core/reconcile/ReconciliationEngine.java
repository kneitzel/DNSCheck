package de.neitzel.dnscheck.core.reconcile;

/**
 * Runs reconciliation for the zone catalog or for one zone catalog entry.
 */
public interface ReconciliationEngine {

    /**
     * Reconciles every valid zone definition in the zone catalog.
     * A failure in one zone does not stop the remaining zones.
     *
     * @param dryRun {@code true} to detect drift without sending nsupdate commands.
     * @return the zone outcome of each zone definition that was processed.
     */
    ReconciliationRunResult reconcileAll(boolean dryRun);

    /**
     * Reconciles one zone catalog entry, identified by its owner folder and zone name.
     *
     * @param ownerFolder the owner folder that contains the zone definition.
     * @param zoneName the DNS zone name.
     * @param dryRun {@code true} to detect drift without sending nsupdate commands.
     * @return the zone outcome for that catalog entry.
     */
    ZoneReconciliationResult reconcileCatalogEntry(String ownerFolder, String zoneName, boolean dryRun);
}
