package de.neitzel.dnscheck.core.zoneview;

/**
 * Per-record status in the zone view.
 */
public enum RecordStatus {
    /**
     * The managed record is present in the live zone with matching rdata and an acceptable TTL.
     */
    ENFORCED,
    /**
     * The record exists only in the live zone and is not part of the zone definition.
     */
    AD_HOC,
    /**
     * The managed record is in the zone definition, but the live rdata or enforced TTL differs.
     */
    DRIFTED,
    /**
     * The managed record is in the zone definition and absent from the live zone.
     */
    MISSING,
    /**
     * A live record matches a deletion marker.
     */
    MARKED_FOR_DELETION
}
