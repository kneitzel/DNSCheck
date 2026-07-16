package de.neitzel.dnscheck.core.model;

/**
 * A single DNS change planned during reconciliation.
 */
public record PlannedChange(
        ChangeAction action,
        String nameFqdn,
        String type,
        String rdata,
        Long ttl,
        String reason) {

    public String describe() {
        return action + " " + nameFqdn + " " + type + " " + rdata
                + (ttl != null ? " ttl=" + ttl : "")
                + " (" + reason + ")";
    }
}
