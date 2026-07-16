package de.neitzel.dnscheck.core.zoneview;

import java.util.List;

/**
 * Merged live and managed view of a single DNS zone.
 */
public record ZoneView(String zone, long defaultTtl, List<ZoneViewRecord> records) {
}
