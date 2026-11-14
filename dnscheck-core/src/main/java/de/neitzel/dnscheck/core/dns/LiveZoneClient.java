package de.neitzel.dnscheck.core.dns;

import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.zone.ZoneNameNormalizer;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.TSIG;
import org.xbill.DNS.Type;
import org.xbill.DNS.Zone;
import org.xbill.DNS.ZoneTransferException;
import org.xbill.DNS.ZoneTransferIn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a live zone by AXFR.
 * The SOA record is omitted from the record set, and its minimum field is kept as the zone default TTL.
 */
public final class LiveZoneClient {

    /**
     * Normalizes the zone name to the absolute form dnsjava expects for the transfer.
     */
    private final ZoneNameNormalizer nameNormalizer;

    /**
     * Creates a client that uses a new zone name normalizer.
     */
    public LiveZoneClient() {
        this(new ZoneNameNormalizer());
    }

    /**
     * Creates a client that uses the supplied zone name normalizer.
     *
     * @param nameNormalizer the normalizer used to build the AXFR zone name.
     */
    LiveZoneClient(ZoneNameNormalizer nameNormalizer) {
        this.nameNormalizer = nameNormalizer;
    }

    /**
     * Transfers the zone with AXFR authenticated by the TSIG key.
     * The returned live zone excludes the SOA record.
     *
     * @param zone the DNS zone to transfer.
     * @param nameserver the authoritative nameserver to contact.
     * @param tsigKey the TSIG key used to authenticate the transfer.
     * @return the live zone, including the zone default TTL.
     * @throws IOException when the transfer fails or the nameserver cannot be queried.
     */
    @SuppressWarnings("unchecked")
    public LiveZone load(String zone, String nameserver, TsigKey tsigKey) throws IOException {
        return load(zone, nameserver, tsigKey, false);
    }

    /** Desktop inspection includes the SOA record and bounds the transfer wait. */
    public LiveZone loadIncludingSoa(String zone, String nameserver, TsigKey tsigKey) throws IOException {
        return load(zone, nameserver, tsigKey, true);
    }

    private LiveZone load(String zone, String nameserver, TsigKey tsigKey, boolean includeSoa) throws IOException {
        Name zoneName = nameNormalizer.toDnsName(ensureTrailingDot(zone));
        ZoneTransferIn transfer = ZoneTransferIn.newAXFR(
                zoneName,
                nameserver,
                resolveTsig(tsigKey));
        if (includeSoa) transfer.setTimeout(java.time.Duration.ofSeconds(15));
        try {
            transfer.run();
        } catch (ZoneTransferException e) {
            throw new IOException("AXFR failed for zone " + zone, e);
        }
        List<Record> axfrRecords = transfer.getAXFR();
        Zone dnsZone = new Zone(zoneName, axfrRecords.toArray(Record[]::new));
        long defaultTtl = extractDefaultTtl(dnsZone);
        if (includeSoa) {
            // Preserve all AXFR records, including RRSIGs; the closing SOA repeats the opening SOA.
            return new LiveZone(zone, defaultTtl, axfrRecords.stream().distinct().map(this::toDnsRecord).toList());
        }
        List<DnsRecord> records = new ArrayList<>();
        for (RRset rrset : dnsZone) {
            for (Record record : rrset.rrs()) {
                if (record.getType() == Type.SOA) {
                    continue;
                }
                records.add(toDnsRecord(record));
            }
        }
        return new LiveZone(zone, defaultTtl, records);
    }

    /**
     * Reads the zone default TTL from the SOA minimum field.
     * When the SOA is not an SOA record, the record TTL is used instead.
     *
     * @param zone the dnsjava zone produced from the AXFR.
     * @return the zone default TTL in seconds.
     */
    private static long extractDefaultTtl(Zone zone) {
        Record soa = zone.getSOA();
        if (soa instanceof SOARecord soaRecord) {
            return soaRecord.getMinimum();
        }
        return soa.getTTL();
    }

    /**
     * Converts one dnsjava record to the canonical form used for drift and the zone view.
     *
     * @param record the transferred record.
     * @return the live record with its owner name, type mnemonic, rdata, and TTL.
     */
    private DnsRecord toDnsRecord(Record record) {
        return new DnsRecord(
                record.getName().toString(),
                Type.string(record.getType()),
                record.rdataToString(),
                record.getTTL());
    }

    /**
     * Builds a dnsjava TSIG from a TSIG key.
     * A trailing dot on the algorithm name is accepted.
     *
     * @param key the TSIG key from the key file.
     * @return the TSIG used to sign AXFR and nsupdate.
     * @throws IllegalArgumentException when the algorithm is not an HMAC-MD5 or HMAC-SHA variant this client supports.
     */
    static TSIG resolveTsig(TsigKey key) {
        Name algorithm = switch (key.algorithm().toLowerCase()) {
            case "hmac-md5", "hmac-md5." -> TSIG.HMAC_MD5;
            case "hmac-sha1", "hmac-sha1." -> TSIG.HMAC_SHA1;
            case "hmac-sha224", "hmac-sha224." -> TSIG.HMAC_SHA224;
            case "hmac-sha256", "hmac-sha256." -> TSIG.HMAC_SHA256;
            case "hmac-sha384", "hmac-sha384." -> TSIG.HMAC_SHA384;
            case "hmac-sha512", "hmac-sha512." -> TSIG.HMAC_SHA512;
            default -> throw new IllegalArgumentException("Unsupported TSIG algorithm: " + key.algorithm());
        };
        return new TSIG(algorithm, key.name(), key.secret());
    }

    /**
     * Appends a trailing dot so the zone name is absolute for AXFR.
     *
     * @param zone the zone name.
     * @return the zone name with exactly one trailing dot.
     */
    private static String ensureTrailingDot(String zone) {
        return zone.endsWith(".") ? zone : zone + ".";
    }
}
