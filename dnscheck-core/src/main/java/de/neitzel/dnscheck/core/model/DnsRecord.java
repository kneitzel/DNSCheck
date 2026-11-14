package de.neitzel.dnscheck.core.model;

/**
 * A DNS record in canonical form for comparison and updates.
 *
 * @param nameFqdn the fully qualified owner name, including the trailing dot.
 * @param type the DNS record type mnemonic, such as {@code A} or {@code TXT}.
 * @param rdata the record data compared against expected records and sent to nsupdate.
 * @param ttl the record TTL in seconds as held by the live zone or planned for an update.
 */
public record DnsRecord(String nameFqdn, String type, String rdata, long ttl) {

    /**
     * Requires an owner name, a record type, and rdata.
     *
     * @throws IllegalArgumentException when {@code nameFqdn} or {@code type} is blank, or when
     *                                  {@code rdata} is missing.
     */
    public DnsRecord {
        if (nameFqdn == null || nameFqdn.isBlank()) {
            throw new IllegalArgumentException("nameFqdn is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (rdata == null) {
            throw new IllegalArgumentException("rdata is required");
        }
    }

    /**
     * Builds the identity used to claim a live record while merging a zone view.
     * The key joins the owner name, the type in upper case, and the rdata with {@code |}.
     *
     * @return the comparison key for this live record.
     */
    public String key() {
        return nameFqdn + "|" + type.toUpperCase() + "|" + rdata;
    }
}
