package de.neitzel.dnscheck.core.reconcile;

import java.util.List;

/**
 * The zone outcome for one zone catalog entry inside a reconciliation run.
 *
 * @param zoneName the catalog reference or zone name this outcome describes.
 * @param outcome whether the zone was unchanged, reconciled, reported in a dry-run, or failed.
 * @param plannedChanges descriptions of the drift that was corrected or that a dry-run would correct.
 * @param errorMessage the failure detail when the outcome is failed, otherwise {@code null}.
 */
public record ZoneReconciliationResult(
        String zoneName,
        ZoneOutcome outcome,
        List<String> plannedChanges,
        String errorMessage) {

    /**
     * Stores an immutable copy of the planned-change descriptions.
     */
    public ZoneReconciliationResult {
        plannedChanges = List.copyOf(plannedChanges);
    }

    /**
     * Builds a success outcome for a zone that had no drift.
     *
     * @param zoneName the catalog reference of the zone.
     * @return an outcome with no planned changes.
     */
    public static ZoneReconciliationResult success(String zoneName) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.SUCCESS, List.of(), null);
    }

    /**
     * Builds an outcome for drift that was corrected in the live zone.
     *
     * @param zoneName the catalog reference of the zone.
     * @param plannedChanges descriptions of the changes that were applied.
     * @return a reconciled zone outcome.
     */
    public static ZoneReconciliationResult reconciled(String zoneName, List<String> plannedChanges) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.RECONCILED, plannedChanges, null);
    }

    /**
     * Builds a dry-run outcome for drift that was detected and not applied.
     *
     * @param zoneName the catalog reference of the zone.
     * @param plannedChanges descriptions of the changes that would be applied.
     * @return a drift-detected zone outcome.
     */
    public static ZoneReconciliationResult driftDetected(String zoneName, List<String> plannedChanges) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.DRIFT_DETECTED, plannedChanges, null);
    }

    /**
     * Builds a failed outcome when reconciliation of the zone could not complete.
     *
     * @param zoneName the catalog reference of the zone, or a placeholder when the catalog itself
     *                 could not be loaded.
     * @param errorMessage the error that prevented completion.
     * @return a failed zone outcome with no planned changes.
     */
    public static ZoneReconciliationResult failed(String zoneName, String errorMessage) {
        return new ZoneReconciliationResult(zoneName, ZoneOutcome.FAILED, List.of(), errorMessage);
    }
}
