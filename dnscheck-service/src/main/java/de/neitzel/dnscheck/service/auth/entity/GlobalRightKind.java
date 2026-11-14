package de.neitzel.dnscheck.service.auth.entity;

/**
 * Global right that applies across the zone catalog.
 */
public enum GlobalRightKind {
    /**
     * Lets an operator manage operators, groups, and all zone rights.
     */
    ADMIN,

    /**
     * Lets an operator create new zone definitions.
     */
    ZONE_CREATE
}
