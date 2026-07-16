package de.neitzel.dnscheck.core.dns;

import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.zone.ZoneNameNormalizer;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;
import org.xbill.DNS.Update;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public final class NsupdateClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final ZoneNameNormalizer nameNormalizer;

    public NsupdateClient() {
        this(new ZoneNameNormalizer());
    }

    NsupdateClient(ZoneNameNormalizer nameNormalizer) {
        this.nameNormalizer = nameNormalizer;
    }

    public void apply(String zone, String nameserver, TsigKey tsigKey, List<PlannedChange> changes)
            throws IOException {
        if (changes.isEmpty()) {
            return;
        }

        Name zoneName = nameNormalizer.toDnsName(ensureTrailingDot(zone));
        Update update = new Update(zoneName);
        update.setTSIG(LiveZoneClient.resolveTsig(tsigKey));

        for (PlannedChange change : changes) {
            applyChange(update, change);
        }

        SimpleResolver resolver = new SimpleResolver(nameserver);
        resolver.setTCP(true);
        resolver.setTimeout(DEFAULT_TIMEOUT);
        Message response = resolver.send(update);
        int rcode = response.getRcode();
        if (rcode != Rcode.NOERROR) {
            throw new IOException("nsupdate failed with rcode " + Rcode.string(rcode));
        }
    }

    private void applyChange(Update update, PlannedChange change) throws IOException {
        Name owner = nameNormalizer.toDnsName(change.nameFqdn());
        int type = Type.value(change.type(), true);
        if (type < 0) {
            throw new IllegalArgumentException("Unsupported record type: " + change.type());
        }

        switch (change.action()) {
            case DELETE -> update.delete(owner, type, change.rdata());
            case ADD -> update.add(createRecord(owner, type, change.ttl(), change.rdata()));
            case UPDATE -> {
                update.delete(owner, type, change.rdata());
                update.add(createRecord(owner, type, change.ttl(), change.rdata()));
            }
            default -> throw new IllegalStateException("Unsupported action: " + change.action());
        }
    }

    private static Record createRecord(Name owner, int type, Long ttl, String rdata) throws IOException {
        long recordTtl = ttl != null ? ttl : 3600L;
        try {
            return Record.fromString(owner, type, DClass.IN, recordTtl, rdata, Name.root);
        } catch (org.xbill.DNS.TextParseException e) {
            throw new IllegalArgumentException("Invalid rdata: " + rdata, e);
        }
    }

    private static String ensureTrailingDot(String zone) {
        return zone.endsWith(".") ? zone : zone + ".";
    }
}
