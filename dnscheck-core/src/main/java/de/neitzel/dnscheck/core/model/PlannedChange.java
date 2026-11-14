package de.neitzel.dnscheck.core.model;

/**
 * A single DNS change planned during reconciliation.
 *
 * @param action the nsupdate operation to apply.
 * @param nameFqdn the fully qualified owner name of the record to change.
 * @param type the DNS record type to change.
 * @param rdata the record data to add, replace, or delete.
 * @param ttl the TTL in seconds for an add or update, or {@code null} when the change does not set a TTL.
 * @param reason a short explanation of the drift or operator action that produced this change.
 */
public record PlannedChange(
        ChangeAction action,
        String nameFqdn,
        String type,
        String rdata,
        Long ttl,
        String reason) {

    /**
     * Formats this change for a zone outcome.
     *
     * @return a single-line description of the action, owner, type, rdata, optional TTL, and reason.
     */
    public String describe() {
        return action + " " + nameFqdn + " " + type + " " + rdata
                + (ttl != null ? " ttl=" + ttl : "")
                + " (" + reason + ")";
    }
}
