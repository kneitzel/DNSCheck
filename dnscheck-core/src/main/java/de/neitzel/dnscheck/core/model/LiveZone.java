package de.neitzel.dnscheck.core.model;

import java.util.List;

/**
 * Current zone contents retrieved via AXFR.
 *
 * @param zone the DNS zone name this snapshot belongs to.
 * @param defaultTtl the zone default TTL, taken from the SOA minimum field.
 * @param records the transferred records; reconciliation omits SOA, while desktop inspection includes it.
 */
public record LiveZone(String zone, long defaultTtl, List<DnsRecord> records) {

    /**
     * Requires a zone name and stores an immutable copy of the live records.
     *
     * @throws IllegalArgumentException when {@code zone} is blank.
     */
    public LiveZone {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        records = List.copyOf(records);
    }
}
