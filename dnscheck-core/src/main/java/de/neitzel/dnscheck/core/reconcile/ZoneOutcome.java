package de.neitzel.dnscheck.core.reconcile;

/**
 * Result of reconciling a single zone within a run.
 */
public enum ZoneOutcome {
    SUCCESS,
    RECONCILED,
    DRIFT_DETECTED,
    FAILED
}
