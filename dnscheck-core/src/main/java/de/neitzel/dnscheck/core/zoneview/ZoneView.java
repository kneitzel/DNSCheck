package de.neitzel.dnscheck.core.zoneview;

import java.util.List;

/**
 * Merged live and managed view of a single DNS zone.
 *
 * @param zone the DNS zone name.
 * @param defaultTtl the zone default TTL from the live zone, used when a TTL policy selects the default.
 * @param records the merged rows, covering enforced, drifted, missing, ad-hoc, and deletion-marked records.
 */
public record ZoneView(String zone, long defaultTtl, List<ZoneViewRecord> records) {
}
