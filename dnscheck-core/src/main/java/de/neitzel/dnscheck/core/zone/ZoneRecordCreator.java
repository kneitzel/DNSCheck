package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;

import java.io.IOException;

public final class ZoneRecordCreator {

    private final ZoneNameNormalizer nameNormalizer;
    private final ZoneDefinitionStore store;

    public ZoneRecordCreator() {
        this(new ZoneNameNormalizer(), new ZoneDefinitionStore());
    }

    ZoneRecordCreator(ZoneNameNormalizer nameNormalizer, ZoneDefinitionStore store) {
        this.nameNormalizer = nameNormalizer;
        this.store = store;
    }

    public RecordSpec buildRecordSpec(String zone, String name, String type, String rdata, Integer ttl) {
        String nameFqdn = nameNormalizer.normalize(name, zone);
        TtlPolicy ttlPolicy = TtlPolicy.fromJsonValue(ttl);
        return new RecordSpec(nameFqdn, type.toUpperCase(), rdata, null, false, ttlPolicy);
    }

    public ZoneDefinition enforceRecord(ZoneDefinition definition, RecordSpec spec) throws IOException {
        ZoneDefinition updated = definition.withRecord(spec);
        store.write(updated);
        return updated;
    }

    public PlannedChange buildAddChange(RecordSpec spec, long zoneDefaultTtl, boolean enforce) {
        long ttl = spec.ttlPolicy().resolveSeconds(zoneDefaultTtl);
        String reason = enforce ? "create enforced record" : "create ad-hoc record";
        return new PlannedChange(ChangeAction.ADD, spec.nameFqdn(), spec.type(), spec.rdata(), ttl, reason);
    }
}
