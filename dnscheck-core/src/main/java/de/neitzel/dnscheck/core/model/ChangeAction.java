package de.neitzel.dnscheck.core.model;

/**
 * The nsupdate operation chosen for one planned change.
 */
public enum ChangeAction {
    /**
     * Add a record that should be present in the live zone.
     */
    ADD,
    /**
     * Replace a live record so its TTL matches the value reconciliation intends to enforce.
     */
    UPDATE,
    /**
     * Remove a record from the live zone.
     */
    DELETE
}
