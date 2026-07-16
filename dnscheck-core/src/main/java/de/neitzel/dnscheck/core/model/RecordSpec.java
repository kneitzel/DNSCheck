package de.neitzel.dnscheck.core.model;

/**
 * A record entry from a zone definition: either a managed record or a deletion marker.
 */
public record RecordSpec(
        String nameFqdn,
        String type,
        String rdata,
        String rdataPattern,
        boolean deletionMarker,
        TtlPolicy ttlPolicy) {

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

    public boolean isManagedRecord() {
        return !deletionMarker;
    }
}
