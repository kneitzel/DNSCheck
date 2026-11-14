package de.neitzel.dnscheck.service.auth.entity;

/**
 * Zone right scoped to one zone catalog entry.
 */
public enum ZoneRightKind {
    /**
     * Allows viewing the zone view and reconciliation outcomes for that entry.
     */
    READ,

    /**
     * Allows managing records, promotion, demotion, deletion, and reconciliation for that entry.
     */
    WRITE
}
