package de.neitzel.dnscheck.core.model;

/**
 * A record entry from a zone definition: either a managed record or a deletion marker.
 *
 * @param nameFqdn the fully qualified owner name stored in the zone definition.
 * @param type the DNS record type.
 * @param rdata the expected rdata of a managed record, or {@code null} for a deletion marker.
 * @param rdataPattern the glob matched against live rdata for a deletion marker.
 *                     Omitted means every rdata value for the name and type matches.
 * @param deletionMarker {@code true} when this entry removes matching live records instead of
 *                       enforcing one expected record.
 * @param ttlPolicy how TTL is compared and corrected for a managed record.
 *                  Deletion markers do not enforce TTL.
 */
public record RecordSpec(
        String nameFqdn,
        String type,
        String rdata,
        String rdataPattern,
        boolean deletionMarker,
        TtlPolicy ttlPolicy) {

    /**
     * Requires an owner name and record type.
     * A managed record must carry rdata and must not carry an rdata pattern.
     * A deletion marker must not carry rdata.
     *
     * @throws IllegalArgumentException when those constraints are broken.
     */
    public RecordSpec {
        if (nameFqdn == null || nameFqdn.isBlank()) {
            throw new IllegalArgumentException("nameFqdn is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (deletionMarker) {
            if (rdata != null && !rdata.isBlank()) {
                throw new IllegalArgumentException("deletion markers must not set rdata");
            }
        } else if (rdata == null || rdata.isBlank()) {
            throw new IllegalArgumentException("managed records require rdata");
        }
        if (!deletionMarker && rdataPattern != null && !rdataPattern.isBlank()) {
            throw new IllegalArgumentException("rdataPattern is only valid for deletion markers");
        }
    }

    /**
     * Reports whether this entry is enforced as an expected record.
     *
     * @return {@code true} when this entry is a managed record rather than a deletion marker.
     */
    public boolean isManagedRecord() {
        return !deletionMarker;
    }
}
