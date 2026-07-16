package de.neitzel.dnscheck.core.model;

/**
 * A DNS record in canonical form for comparison and updates.
 */
public record DnsRecord(String nameFqdn, String type, String rdata, long ttl) {

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

    public String key() {
        return nameFqdn + "|" + type.toUpperCase() + "|" + rdata;
    }
}
