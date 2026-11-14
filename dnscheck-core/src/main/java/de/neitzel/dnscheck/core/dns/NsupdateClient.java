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

/**
 * Applies planned changes to a live zone with one authenticated dynamic update.
 */
public final class NsupdateClient {

    /**
     * How long a single nsupdate exchange may take before it is abandoned.
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Converts owner names and the zone name into dnsjava names.
     */
    private final ZoneNameNormalizer nameNormalizer;

    /**
     * Creates a client that uses a new zone name normalizer.
     */
    public NsupdateClient() {
        this(new ZoneNameNormalizer());
    }

    /**
     * Creates a client that uses the supplied zone name normalizer.
     *
     * @param nameNormalizer the normalizer used for the zone name and record owner names.
     */
    NsupdateClient(ZoneNameNormalizer nameNormalizer) {
        this.nameNormalizer = nameNormalizer;
    }

    /**
     * Sends every planned change in one update to the nameserver.
     * An empty change list does nothing. The update uses TCP and the TSIG key.
     *
     * @param zone the DNS zone to update.
     * @param nameserver the authoritative nameserver to contact.
     * @param tsigKey the TSIG key used to authenticate the update.
     * @param changes the planned changes for this zone, in the order they should be applied.
     * @throws IOException when the nameserver cannot be reached or the response rcode is not {@code NOERROR}.
     * @throws IllegalArgumentException when a change uses an unsupported record type or invalid rdata.
     */
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

    /**
     * Appends one planned change to the update.
     * An update deletes the current rdata and adds it again with the planned TTL.
     * A delete removes that rdata.
     *
     * @param update the dynamic update being built for the zone.
     * @param change the planned change to append.
     * @throws IOException when the replacement record cannot be built.
     * @throws IllegalArgumentException when the record type is not supported or the rdata is invalid.
     * @throws IllegalStateException when the action is not add, update, or delete.
     */
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

    /**
     * Builds an IN-class record for an add or update.
     * A missing TTL falls back to 3600 seconds.
     *
     * @param owner the absolute owner name.
     * @param type the dnsjava record type code.
     * @param ttl the TTL in seconds, or {@code null} to use the fallback.
     * @param rdata the record data.
     * @return the record to add.
     * @throws IOException when the record cannot be parsed for a reason other than rdata syntax.
     * @throws IllegalArgumentException when {@code rdata} is not valid for the record type.
     */
    private static Record createRecord(Name owner, int type, Long ttl, String rdata) throws IOException {
        long recordTtl = ttl != null ? ttl : 3600L;
        try {
            return Record.fromString(owner, type, DClass.IN, recordTtl, rdata, Name.root);
        } catch (org.xbill.DNS.TextParseException e) {
            throw new IllegalArgumentException("Invalid rdata: " + rdata, e);
        }
    }

    /**
     * Appends a trailing dot so the zone name is absolute for the update.
     *
     * @param zone the zone name.
     * @return the zone name with exactly one trailing dot.
     */
    private static String ensureTrailingDot(String zone) {
        return zone.endsWith(".") ? zone : zone + ".";
    }
}
