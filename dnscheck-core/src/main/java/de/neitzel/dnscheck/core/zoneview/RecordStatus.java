package de.neitzel.dnscheck.core.zoneview;

/**
 * Per-record status in the zone view.
 */
public enum RecordStatus {
    ENFORCED,
    AD_HOC,
    DRIFTED,
    MISSING,
    MARKED_FOR_DELETION
}
