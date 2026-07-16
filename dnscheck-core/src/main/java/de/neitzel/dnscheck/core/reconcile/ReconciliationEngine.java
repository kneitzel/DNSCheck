package de.neitzel.dnscheck.core.reconcile;

public interface ReconciliationEngine {

    ReconciliationRunResult reconcileAll(boolean dryRun);

    ZoneReconciliationResult reconcileCatalogEntry(String ownerFolder, String zoneName, boolean dryRun);
}
