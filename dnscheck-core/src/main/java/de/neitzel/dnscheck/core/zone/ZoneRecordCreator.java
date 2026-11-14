package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;

import java.io.IOException;

/**
 * Builds a managed record and can append it to a zone definition.
 */
public final class ZoneRecordCreator {

    /**
     * Normalizes the owner name to FQDN before the record is stored.
     */
    private final ZoneNameNormalizer nameNormalizer;

    /**
     * Writes the zone definition after a record is enforced.
     */
    private final ZoneDefinitionStore store;

    /**
     * Creates a creator with a new name normalizer and zone definition store.
     */
    public ZoneRecordCreator() {
        this(new ZoneNameNormalizer(), new ZoneDefinitionStore());
    }

    /**
     * Creates a creator that uses the supplied normalizer and store.
     *
     * @param nameNormalizer the normalizer for owner names.
     * @param store the store that writes an enforced record into the zone definition.
     */
    ZoneRecordCreator(ZoneNameNormalizer nameNormalizer, ZoneDefinitionStore store) {
        this.nameNormalizer = nameNormalizer;
        this.store = store;
    }

    /**
     * Builds a managed record from an operator-supplied name, type, rdata, and TTL.
     * The name is stored as an FQDN and the type is stored in upper case.
     * This does not create a deletion marker.
     *
     * @param zone the DNS zone the name is relative to.
     * @param name the owner name, which may be {@code @}, relative, or absolute.
     * @param type the DNS record type.
     * @param rdata the expected rdata.
     * @param ttl the TTL policy value. Omitted ignores TTL, {@code -1} uses the zone default TTL,
     *            and a positive value is enforced.
     * @return the managed record.
     * @throws IllegalArgumentException when the name, zone, type, rdata, or TTL is not valid for a managed record.
     */
    public RecordSpec buildRecordSpec(String zone, String name, String type, String rdata, Integer ttl) {
        String nameFqdn = nameNormalizer.normalize(name, zone);
        TtlPolicy ttlPolicy = TtlPolicy.fromJsonValue(ttl);
        return new RecordSpec(nameFqdn, type.toUpperCase(), rdata, null, false, ttlPolicy);
    }

    /**
     * Appends the managed record to the zone definition and writes the file.
     * This is how an enforced record is persisted.
     *
     * @param definition the zone definition to update.
     * @param spec the managed record to append.
     * @return the updated zone definition.
     * @throws IOException when the zone definition file cannot be written.
     */
    public ZoneDefinition enforceRecord(ZoneDefinition definition, RecordSpec spec) throws IOException {
        ZoneDefinition updated = definition.withRecord(spec);
        store.write(updated);
        return updated;
    }

    /**
     * Plans an add for a new record.
     * The TTL comes from the record's TTL policy and the zone default TTL.
     * The reason distinguishes an enforced record from an ad-hoc record.
     *
     * @param spec the record to add.
     * @param zoneDefaultTtl the zone default TTL used when the TTL policy selects the default.
     * @param enforce {@code true} when the record is also being added to the zone definition.
     * @return the planned add.
     */
    public PlannedChange buildAddChange(RecordSpec spec, long zoneDefaultTtl, boolean enforce) {
        long ttl = spec.ttlPolicy().resolveSeconds(zoneDefaultTtl);
        String reason = enforce ? "create enforced record" : "create ad-hoc record";
        return new PlannedChange(ChangeAction.ADD, spec.nameFqdn(), spec.type(), spec.rdata(), ttl, reason);
    }
}
