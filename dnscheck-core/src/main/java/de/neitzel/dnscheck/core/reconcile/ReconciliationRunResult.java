package de.neitzel.dnscheck.core.reconcile;

import java.util.List;

/**
 * The zone outcomes collected by one reconciliation run.
 *
 * @param zoneResults the per-zone outcomes, in catalog order. The list is stored immutably.
 */
public record ReconciliationRunResult(List<ZoneReconciliationResult> zoneResults) {

    /**
     * Stores an immutable copy of the zone outcomes.
     */
    public ReconciliationRunResult {
        zoneResults = List.copyOf(zoneResults);
    }
}
