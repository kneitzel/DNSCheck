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

public final class ZoneViewMerger {

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

    private static boolean sameNameAndType(DnsRecord record, RecordSpec spec) {
        return record.nameFqdn().equalsIgnoreCase(spec.nameFqdn())
                && record.type().equalsIgnoreCase(spec.type());
    }
}
