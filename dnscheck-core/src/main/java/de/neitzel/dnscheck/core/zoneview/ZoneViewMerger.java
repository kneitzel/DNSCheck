package de.neitzel.dnscheck.core.zoneview;

import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.util.GlobMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a zone view by classifying each managed record against the live zone, then appending
 * deletion matches and ad-hoc records.
 */
public final class ZoneViewMerger {

    /**
     * Merges the zone definition with the live zone.
     * Managed records are classified first and claim an exact live match so it is not also shown as ad-hoc.
     * A live record with the same name and type but different rdata is drift and stays unclaimed, so it can
     * still appear as ad-hoc. Live records that match a deletion marker are marked for deletion.
     * Rows are ordered by owner name, type, and rdata.
     *
     * @param definition the zone definition that holds managed records and deletion markers.
     * @param liveZone the live zone retrieved by AXFR.
     * @return the zone view for that zone.
     */
    public ZoneView merge(ZoneDefinition definition, LiveZone liveZone) {
        List<ZoneViewRecord> records = new ArrayList<>();
        Set<String> claimedLiveKeys = new HashSet<>();

        for (RecordSpec managedRecord : definition.managedRecords()) {
            records.add(classifyManaged(managedRecord, liveZone, claimedLiveKeys));
        }

        Set<String> deletionKeys = new HashSet<>();
        for (DnsRecord liveRecord : liveZone.records()) {
            if (!matchesDeletionMarker(liveRecord, definition)) {
                continue;
            }
            records.add(ZoneViewRecord.markedForDeletion(liveRecord));
            deletionKeys.add(liveRecord.key());
        }

        for (DnsRecord liveRecord : liveZone.records()) {
            if (claimedLiveKeys.contains(liveRecord.key()) || deletionKeys.contains(liveRecord.key())) {
                continue;
            }
            records.add(ZoneViewRecord.adHoc(liveRecord));
        }

        records.sort(Comparator.comparing(ZoneViewRecord::nameFqdn, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ZoneViewRecord::type, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(record -> record.rdata() == null ? "" : record.rdata(), String.CASE_INSENSITIVE_ORDER));

        return new ZoneView(definition.zone(), liveZone.defaultTtl(), List.copyOf(records));
    }

    /**
     * Classifies one managed record as enforced, drifted, or missing, and claims the matching live record
     * when the rdata matches. A TTL difference is drift only when the TTL policy enforces a value.
     *
     * @param spec the managed record to classify.
     * @param liveZone the live zone retrieved by AXFR.
     * @param claimedLiveKeys live-record keys already represented by an enforced or TTL-drifted row.
     *                        An exact match is added to this set.
     * @return the zone view row for the managed record.
     */
    private ZoneViewRecord classifyManaged(RecordSpec spec, LiveZone liveZone, Set<String> claimedLiveKeys) {
        long desiredTtl = spec.ttlPolicy().resolveSeconds(liveZone.defaultTtl());
        List<DnsRecord> sameNameAndType = liveZone.records().stream()
                .filter(record -> sameNameAndType(record, spec))
                .toList();

        DnsRecord exactMatch = sameNameAndType.stream()
                .filter(record -> Objects.equals(record.rdata(), spec.rdata()))
                .findFirst()
                .orElse(null);

        if (exactMatch != null) {
            claimedLiveKeys.add(exactMatch.key());
            if (spec.ttlPolicy().isEnforced() && exactMatch.ttl() != desiredTtl) {
                return ZoneViewRecord.drifted(
                        spec.nameFqdn(),
                        spec.type(),
                        exactMatch.rdata(),
                        exactMatch.ttl(),
                        spec.rdata(),
                        desiredTtl);
            }
            return ZoneViewRecord.enforced(exactMatch);
        }

        if (sameNameAndType.isEmpty()) {
            return ZoneViewRecord.missing(spec.nameFqdn(), spec.type(), spec.rdata(), desiredTtl);
        }

        DnsRecord conflictingLive = sameNameAndType.getFirst();
        return ZoneViewRecord.drifted(
                spec.nameFqdn(),
                spec.type(),
                conflictingLive.rdata(),
                conflictingLive.ttl(),
                spec.rdata(),
                desiredTtl);
    }

    /**
     * Reports whether any deletion marker matches the live record's owner, type, and rdata.
     *
     * @param liveRecord the live record to test.
     * @param definition the zone definition that holds the deletion markers.
     * @return {@code true} when a deletion marker matches.
     */
    private static boolean matchesDeletionMarker(DnsRecord liveRecord, ZoneDefinition definition) {
        for (RecordSpec marker : definition.deletionMarkers()) {
            if (!sameNameAndType(liveRecord, marker)) {
                continue;
            }
            if (GlobMatcher.matches(marker.rdataPattern(), liveRecord.rdata())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compares owner name and record type without case.
     *
     * @param record the live record.
     * @param spec the managed record or deletion marker.
     * @return {@code true} when the owner name and type match.
     */
    private static boolean sameNameAndType(DnsRecord record, RecordSpec spec) {
        return record.nameFqdn().equalsIgnoreCase(spec.nameFqdn())
                && record.type().equalsIgnoreCase(spec.type());
    }
}
