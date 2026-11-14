package de.neitzel.dnscheck.core.reconcile;

/**
 * Result of reconciling a single zone within a run.
 */
public enum ZoneOutcome {
    /**
     * The live zone already matched the expected records, so no change was required.
     */
    SUCCESS,
    /**
     * Drift was found and the planned nsupdate changes were applied.
     */
    RECONCILED,
    /**
     * Drift was found during a dry-run and the live zone was left unchanged.
     */
    DRIFT_DETECTED,
    /**
     * An error prevented reconciliation of this zone from completing.
     */
    FAILED
}
