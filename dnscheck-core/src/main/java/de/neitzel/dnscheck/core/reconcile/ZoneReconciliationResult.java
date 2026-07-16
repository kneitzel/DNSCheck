package de.neitzel.dnscheck.core.reconcile;

import java.util.List;

public record ZoneReconciliationResult(
        String zoneName,
        ZoneOutcome outcome,
        List<String> plannedChanges,
        String errorMessage) {

    public ZoneReconciliationResult {
        plannedChanges = List.copyOf(plannedChanges);
    }

    public static ZoneReconciliationResult success(String zoneName) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.SUCCESS, List.of(), null);
    }

    public static ZoneReconciliationResult reconciled(String zoneName, List<String> plannedChanges) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.RECONCILED, plannedChanges, null);
    }

    public static ZoneReconciliationResult driftDetected(String zoneName, List<String> plannedChanges) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.DRIFT_DETECTED, plannedChanges, null);
    }

    public static ZoneReconciliationResult failed(String zoneName, String errorMessage) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.FAILED, List.of(), errorMessage);
    }
}
