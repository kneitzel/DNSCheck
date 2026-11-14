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

/**
 * Promotes, demotes, deletes, and modifies records.
 * Enforcement changes are written to the zone definition, and live-zone changes are returned for the caller to apply.
 */
public final class ZoneRecordLifecycle {

    /**
     * Normalizes owner names to FQDN before they are compared with the zone definition and the live zone.
     */
    private final ZoneNameNormalizer nameNormalizer;

    /**
     * Writes the zone definition when promotion, demotion, deletion, or modification changes enforcement.
     */
    private final ZoneDefinitionStore store;

    /**
     * Creates a lifecycle with a new name normalizer and zone definition store.
     */
    public ZoneRecordLifecycle() {
        this(new ZoneNameNormalizer(), new ZoneDefinitionStore());
    }

    /**
     * Creates a lifecycle that uses the supplied normalizer and store.
     *
     * @param nameNormalizer the normalizer for owner names.
     * @param store the store that writes enforcement changes.
     */
    ZoneRecordLifecycle(ZoneNameNormalizer nameNormalizer, ZoneDefinitionStore store) {
        this.nameNormalizer = nameNormalizer;
        this.store = store;
    }

    /**
     * The zone definition an operator should display after a lifecycle action, plus the nsupdate changes still to apply.
     *
     * @param viewDefinition the zone definition after the action. It is unchanged when an ad-hoc modification
     *                       only affects the live zone.
     * @param dnsChanges the planned changes for the live zone. Empty when the action only changed the zone definition.
     */
    public record LifecycleResult(ZoneDefinition viewDefinition, List<PlannedChange> dnsChanges) {}

    /**
     * Adds an existing live record to the zone definition so reconciliation will enforce it.
     * Promotion does not change the live zone.
     *
     * @param definition the zone definition to update.
     * @param liveZone the live zone that must already contain the record.
     * @param name the owner name as supplied by the operator.
     * @param type the DNS record type.
     * @param rdata the live rdata to promote.
     * @param ttl the TTL policy to store. Omitted ignores TTL, {@code -1} uses the zone default TTL,
     *            and a positive value is enforced.
     * @return the updated zone definition and no DNS changes.
     * @throws IOException when the zone definition cannot be written.
     * @throws NoSuchRecordException when the live zone has no matching record.
     * @throws InvalidRecordStateException when the record is already a managed record.
     * @throws IllegalArgumentException when the name or TTL is not valid.
     */
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

    /**
     * Removes a managed record from the zone definition and leaves it in the live zone as an ad-hoc record.
     *
     * @param definition the zone definition to update.
     * @param name the owner name as supplied by the operator.
     * @param type the DNS record type.
     * @param rdata the rdata of the managed record to demote.
     * @return the updated zone definition and no DNS changes.
     * @throws IOException when the zone definition cannot be written.
     * @throws NoSuchRecordException when no managed record matches.
     * @throws IllegalArgumentException when the name is not valid.
     */
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

    /**
     * Removes the record from the live zone.
     * When it is also a managed record, it is removed from the zone definition.
     * A record that is only ad-hoc is deleted from DNS and the zone definition is left unchanged.
     *
     * @param definition the zone definition that may contain the managed record.
     * @param liveZone the live zone that may contain the record.
     * @param name the owner name as supplied by the operator.
     * @param type the DNS record type.
     * @param rdata the rdata to delete.
     * @return the zone definition after any removal, and a delete change when the record is present in the live zone.
     * @throws IOException when a managed record cannot be removed from the zone definition file.
     * @throws NoSuchRecordException when the record is in neither the zone definition nor the live zone.
     * @throws IllegalArgumentException when the name is not valid.
     */
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

    /**
     * Changes rdata or TTL.
     * A managed record is updated in the zone definition and the corresponding live changes are planned.
     * An ad-hoc record is changed only in the live zone. At least one of the new rdata and the new TTL is required.
     *
     * @param definition the zone definition that may contain the managed record.
     * @param liveZone the live zone that may contain the record.
     * @param name the owner name as supplied by the operator.
     * @param type the DNS record type.
     * @param rdata the current rdata that identifies the record.
     * @param newRdata the replacement rdata, or {@code null} to keep the current rdata.
     * @param newTtl the replacement TTL policy value, or {@code null} to keep the current TTL.
     *               For a managed record, omitted means ignore TTL and {@code -1} uses the zone default TTL.
     * @return the updated view and the planned live changes.
     * @throws IOException when a managed record's zone definition cannot be written.
     * @throws InvalidRecordStateException when neither new value is set, or an ad-hoc change would not alter
     *                                     the live record.
     * @throws NoSuchRecordException when an ad-hoc change names a record that is not in the live zone.
     * @throws IllegalArgumentException when the name or TTL is not valid.
     */
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

    /**
     * Replaces a managed record in the zone definition and plans the live changes.
     * An rdata change deletes the old rdata and adds the new rdata. A TTL-only change plans an update when
     * the TTL policy is enforced and the live TTL differs, or an add when the live record is missing.
     *
     * @param definition the zone definition that contains the managed record.
     * @param liveZone the live zone used to decide which DNS changes are still required.
     * @param nameFqdn the fully qualified owner name.
     * @param recordType the DNS record type, in upper case.
     * @param rdata the current expected rdata.
     * @param existing the managed record being replaced.
     * @param newRdata the replacement rdata, or {@code null} to keep {@code existing}'s rdata.
     * @param newTtl the replacement TTL policy value, or {@code null} to keep the current TTL policy.
     * @return the updated zone definition and the planned live changes.
     * @throws IOException when the zone definition cannot be written.
     * @throws IllegalArgumentException when {@code newTtl} is not a valid TTL policy value.
     */
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

    /**
     * Plans live changes for a record that is not managed.
     * The zone definition is not written. Fails when the live record is absent or the requested values
     * would not change it.
     *
     * @param definition the unchanged zone definition returned to the caller.
     * @param liveZone the live zone that must contain the ad-hoc record.
     * @param nameFqdn the fully qualified owner name.
     * @param recordType the DNS record type, in upper case.
     * @param rdata the current live rdata.
     * @param newRdata the replacement rdata, or {@code null} to keep the live rdata.
     * @param newTtl the replacement TTL in seconds, or {@code null} to keep the live TTL.
     * @return the original zone definition and the planned live changes.
     * @throws NoSuchRecordException when the live zone has no matching record.
     * @throws InvalidRecordStateException when the requested values would not change the live record.
     */
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

    /**
     * Finds a live record with the same owner name, type, and rdata.
     * Name and type are compared without case.
     *
     * @param liveZone the live zone to search.
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param rdata the rdata that must match exactly.
     * @return the matching live record, or empty when none matches.
     */
    private static Optional<DnsRecord> findLiveRecord(
            LiveZone liveZone, String nameFqdn, String type, String rdata) {
        return liveZone.records().stream()
                .filter(record -> record.nameFqdn().equalsIgnoreCase(nameFqdn)
                        && record.type().equalsIgnoreCase(type)
                        && Objects.equals(record.rdata(), rdata))
                .findFirst();
    }
}
