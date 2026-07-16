package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ZoneRecordLifecycle {

    private final ZoneNameNormalizer nameNormalizer;
    private final ZoneDefinitionStore store;

    public ZoneRecordLifecycle() {
        this(new ZoneNameNormalizer(), new ZoneDefinitionStore());
    }

    ZoneRecordLifecycle(ZoneNameNormalizer nameNormalizer, ZoneDefinitionStore store) {
        this.nameNormalizer = nameNormalizer;
        this.store = store;
    }

    public record LifecycleResult(ZoneDefinition viewDefinition, List<PlannedChange> dnsChanges) {}

    public LifecycleResult promote(
            ZoneDefinition definition, LiveZone liveZone, String name, String type, String rdata, Integer ttl)
            throws IOException {
        String nameFqdn = nameNormalizer.normalize(name, definition.zone());
        String recordType = type.toUpperCase();
        DnsRecord liveRecord = findLiveRecord(liveZone, nameFqdn, recordType, rdata)
                .orElseThrow(() -> new NoSuchRecordException("live record not found"));

        if (definition.findManagedRecord(nameFqdn, recordType, rdata).isPresent()) {
            throw new InvalidRecordStateException("record is already managed");
        }

        RecordSpec spec = new RecordSpec(
                nameFqdn, recordType, liveRecord.rdata(), null, false, TtlPolicy.fromJsonValue(ttl));
        ZoneDefinition updated = definition.withRecord(spec);
        store.write(updated);
        return new LifecycleResult(updated, List.of());
    }

    public LifecycleResult demote(ZoneDefinition definition, String name, String type, String rdata)
            throws IOException {
        String nameFqdn = nameNormalizer.normalize(name, definition.zone());
        String recordType = type.toUpperCase();
        if (definition.findManagedRecord(nameFqdn, recordType, rdata).isEmpty()) {
            throw new NoSuchRecordException("managed record not found");
        }

        ZoneDefinition updated = definition.withoutManagedRecord(nameFqdn, recordType, rdata);
        store.write(updated);
        return new LifecycleResult(updated, List.of());
    }

    public LifecycleResult delete(ZoneDefinition definition, LiveZone liveZone, String name, String type, String rdata)
            throws IOException {
        String nameFqdn = nameNormalizer.normalize(name, definition.zone());
        String recordType = type.toUpperCase();
        boolean inDefinition = definition.findManagedRecord(nameFqdn, recordType, rdata).isPresent();
        Optional<DnsRecord> liveRecord = findLiveRecord(liveZone, nameFqdn, recordType, rdata);

        if (!inDefinition && liveRecord.isEmpty()) {
            throw new NoSuchRecordException("record not found");
        }

        ZoneDefinition updated = definition;
        if (inDefinition) {
            updated = definition.withoutManagedRecord(nameFqdn, recordType, rdata);
            store.write(updated);
        }

        List<PlannedChange> changes = new ArrayList<>();
        liveRecord.ifPresent(record -> changes.add(new PlannedChange(
                ChangeAction.DELETE,
                nameFqdn,
                recordType,
                record.rdata(),
                null,
                "delete record")));
        return new LifecycleResult(updated, List.copyOf(changes));
    }

    public LifecycleResult modify(
            ZoneDefinition definition,
            LiveZone liveZone,
            String name,
            String type,
            String rdata,
            String newRdata,
            Integer newTtl)
            throws IOException {
        if (newRdata == null && newTtl == null) {
            throw new InvalidRecordStateException("newRdata or newTtl is required");
        }

        String nameFqdn = nameNormalizer.normalize(name, definition.zone());
        String recordType = type.toUpperCase();
        Optional<RecordSpec> managed = definition.findManagedRecord(nameFqdn, recordType, rdata);
        if (managed.isPresent()) {
            return modifyManaged(definition, liveZone, nameFqdn, recordType, rdata, managed.get(), newRdata, newTtl);
        }
        return modifyAdHoc(definition, liveZone, nameFqdn, recordType, rdata, newRdata, newTtl);
    }

    private LifecycleResult modifyManaged(
            ZoneDefinition definition,
            LiveZone liveZone,
            String nameFqdn,
            String recordType,
            String rdata,
            RecordSpec existing,
            String newRdata,
            Integer newTtl)
            throws IOException {
        String targetRdata = newRdata != null ? newRdata : existing.rdata();
        TtlPolicy ttlPolicy = newTtl != null ? TtlPolicy.fromJsonValue(newTtl) : existing.ttlPolicy();
        RecordSpec updatedSpec =
                new RecordSpec(nameFqdn, recordType, targetRdata, null, false, ttlPolicy);
        ZoneDefinition updatedDefinition =
                definition.withReplacedManagedRecord(nameFqdn, recordType, rdata, updatedSpec);
        store.write(updatedDefinition);

        long desiredTtl = ttlPolicy.resolveSeconds(liveZone.defaultTtl());
        List<PlannedChange> changes = new ArrayList<>();
        Optional<DnsRecord> currentLive = findLiveRecord(liveZone, nameFqdn, recordType, rdata);

        if (!Objects.equals(targetRdata, rdata)) {
            currentLive.ifPresent(record -> changes.add(new PlannedChange(
                    ChangeAction.DELETE,
                    nameFqdn,
                    recordType,
                    record.rdata(),
                    null,
                    "modify record rdata")));
            changes.add(new PlannedChange(
                    ChangeAction.ADD,
                    nameFqdn,
                    recordType,
                    targetRdata,
                    desiredTtl,
                    "modify record rdata"));
        } else if (ttlPolicy.isEnforced()) {
            if (currentLive.isEmpty()) {
                changes.add(new PlannedChange(
                        ChangeAction.ADD,
                        nameFqdn,
                        recordType,
                        targetRdata,
                        desiredTtl,
                        "modify managed record TTL"));
            } else if (currentLive.get().ttl() != desiredTtl) {
                changes.add(new PlannedChange(
                        ChangeAction.UPDATE,
                        nameFqdn,
                        recordType,
                        targetRdata,
                        desiredTtl,
                        "modify managed record TTL"));
            }
        }

        return new LifecycleResult(updatedDefinition, List.copyOf(changes));
    }

    private LifecycleResult modifyAdHoc(
            ZoneDefinition definition,
            LiveZone liveZone,
            String nameFqdn,
            String recordType,
            String rdata,
            String newRdata,
            Integer newTtl) {
        DnsRecord currentLive = findLiveRecord(liveZone, nameFqdn, recordType, rdata)
                .orElseThrow(() -> new NoSuchRecordException("live record not found"));

        String targetRdata = newRdata != null ? newRdata : currentLive.rdata();
        long targetTtl = newTtl != null ? newTtl.longValue() : currentLive.ttl();
        List<PlannedChange> changes = new ArrayList<>();

        if (!Objects.equals(targetRdata, currentLive.rdata())) {
            changes.add(new PlannedChange(
                    ChangeAction.DELETE,
                    nameFqdn,
                    recordType,
                    currentLive.rdata(),
                    null,
                    "modify ad-hoc record rdata"));
            changes.add(new PlannedChange(
                    ChangeAction.ADD,
                    nameFqdn,
                    recordType,
                    targetRdata,
                    targetTtl,
                    "modify ad-hoc record rdata"));
        } else if (newTtl != null && currentLive.ttl() != targetTtl) {
            changes.add(new PlannedChange(
                    ChangeAction.UPDATE,
                    nameFqdn,
                    recordType,
                    targetRdata,
                    targetTtl,
                    "modify ad-hoc record TTL"));
        }

        if (changes.isEmpty()) {
            throw new InvalidRecordStateException("no live changes requested");
        }

        return new LifecycleResult(definition, List.copyOf(changes));
    }

    private static Optional<DnsRecord> findLiveRecord(
            LiveZone liveZone, String nameFqdn, String type, String rdata) {
        return liveZone.records().stream()
                .filter(record -> record.nameFqdn().equalsIgnoreCase(nameFqdn)
                        && record.type().equalsIgnoreCase(type)
                        && Objects.equals(record.rdata(), rdata))
                .findFirst();
    }
}
