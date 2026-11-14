package de.neitzel.dnscheck.core.drift;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.util.GlobMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares a zone definition with a live zone and plans the nsupdate changes that would remove the drift.
 * Live records that match neither a managed record nor a deletion marker are left alone.
 */
public final class DriftDetector {

    /**
     * Plans additions for missing managed records, TTL corrections where the TTL policy enforces a value,
     * and deletions for live records that match a deletion marker.
     *
     * @param definition the zone definition that holds the expected records and deletion markers.
     * @param liveZone the live zone retrieved by AXFR.
     * @return the planned changes, with managed records first and deletion markers after them.
     */
    public List<PlannedChange> detect(ZoneDefinition definition, LiveZone liveZone) {
        List<PlannedChange> changes = new ArrayList<>();

        for (RecordSpec managedRecord : definition.managedRecords()) {
            changes.addAll(detectManagedRecord(managedRecord, liveZone));
        }

        for (RecordSpec deletionMarker : definition.deletionMarkers()) {
            changes.addAll(detectDeletionMarker(deletionMarker, liveZone));
        }

        return List.copyOf(changes);
    }

    /**
     * Plans an add when no live record has the same owner, type, and rdata.
     * When the TTL policy is enforced and a matching live record has a different TTL, plans an update.
     *
     * @param spec the managed record to compare.
     * @param liveZone the live zone retrieved by AXFR.
     * @return the planned changes for this managed record, which may be empty.
     */
    private List<PlannedChange> detectManagedRecord(RecordSpec spec, LiveZone liveZone) {
        List<PlannedChange> changes = new ArrayList<>();
        List<DnsRecord> matches = liveZone.records().stream()
                .filter(record -> sameNameAndType(record, spec))
                .filter(record -> Objects.equals(record.rdata(), spec.rdata()))
                .toList();

        long desiredTtl = spec.ttlPolicy().resolveSeconds(liveZone.defaultTtl());

        if (matches.isEmpty()) {
            changes.add(new PlannedChange(
                    ChangeAction.ADD,
                    spec.nameFqdn(),
                    spec.type(),
                    spec.rdata(),
                    desiredTtl,
                    "managed record missing"));
            return changes;
        }

        if (spec.ttlPolicy().isEnforced()) {
            boolean ttlMismatch = matches.stream().anyMatch(record -> record.ttl() != desiredTtl);
            if (ttlMismatch) {
                changes.add(new PlannedChange(
                        ChangeAction.UPDATE,
                        spec.nameFqdn(),
                        spec.type(),
                        spec.rdata(),
                        desiredTtl,
                        "managed record TTL mismatch"));
            }
        }

        return changes;
    }

    /**
     * Plans a delete for each live record whose owner and type match the marker and whose rdata matches
     * the rdata pattern.
     *
     * @param marker the deletion marker to apply.
     * @param liveZone the live zone retrieved by AXFR.
     * @return one planned deletion per matching live record.
     */
    private List<PlannedChange> detectDeletionMarker(RecordSpec marker, LiveZone liveZone) {
        List<PlannedChange> changes = new ArrayList<>();
        for (DnsRecord record : liveZone.records()) {
            if (!sameNameAndType(record, marker)) {
                continue;
            }
            if (!GlobMatcher.matches(marker.rdataPattern(), record.rdata())) {
                continue;
            }
            changes.add(new PlannedChange(
                    ChangeAction.DELETE,
                    record.nameFqdn(),
                    record.type(),
                    record.rdata(),
                    null,
                    "deletion marker matched"));
        }
        return changes;
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
