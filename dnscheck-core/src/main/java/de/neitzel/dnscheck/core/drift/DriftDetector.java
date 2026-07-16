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

public final class DriftDetector {

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

    private static boolean sameNameAndType(DnsRecord record, RecordSpec spec) {
        return record.nameFqdn().equalsIgnoreCase(spec.nameFqdn())
                && record.type().equalsIgnoreCase(spec.type());
    }
}
