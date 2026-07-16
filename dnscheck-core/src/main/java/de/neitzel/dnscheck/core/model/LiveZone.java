package de.neitzel.dnscheck.core.model;

import java.util.List;

/**
 * Current zone contents retrieved via AXFR.
 */
public record LiveZone(String zone, long defaultTtl, List<DnsRecord> records) {

    public LiveZone {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("zone is required");
        }
        records = List.copyOf(records);
    }
}
