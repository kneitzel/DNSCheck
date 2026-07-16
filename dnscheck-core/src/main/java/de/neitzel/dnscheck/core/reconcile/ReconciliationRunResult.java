package de.neitzel.dnscheck.core.reconcile;

import java.util.List;

public record ReconciliationRunResult(List<ZoneReconciliationResult> zoneResults) {

    public ReconciliationRunResult {
        zoneResults = List.copyOf(zoneResults);
    }
}
