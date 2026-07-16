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

public final class LiveZoneClient {

    private final ZoneNameNormalizer nameNormalizer;

    public LiveZoneClient() {
        this(new ZoneNameNormalizer());
    }

    LiveZoneClient(ZoneNameNormalizer nameNormalizer) {
        this.nameNormalizer = nameNormalizer;
    }

    @SuppressWarnings("unchecked")
    public LiveZone load(String zone, String nameserver, TsigKey tsigKey) throws IOException {
        Name zoneName = nameNormalizer.toDnsName(ensureTrailingDot(zone));
        ZoneTransferIn transfer = ZoneTransferIn.newAXFR(
                zoneName,
                nameserver,
                resolveTsig(tsigKey));
        try {
            transfer.run();
        } catch (ZoneTransferException e) {
            throw new IOException("AXFR failed for zone " + zone, e);
        }
        List<Record> axfrRecords = transfer.getAXFR();
        Zone dnsZone = new Zone(zoneName, axfrRecords.toArray(Record[]::new));
        long defaultTtl = extractDefaultTtl(dnsZone);
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

    private static long extractDefaultTtl(Zone zone) {
        Record soa = zone.getSOA();
        if (soa instanceof SOARecord soaRecord) {
            return soaRecord.getMinimum();
        }
        return soa.getTTL();
    }

    private DnsRecord toDnsRecord(Record record) {
        return new DnsRecord(
                record.getName().toString(),
                Type.string(record.getType()),
                record.rdataToString(),
                record.getTTL());
    }

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

    private static String ensureTrailingDot(String zone) {
        return zone.endsWith(".") ? zone : zone + ".";
    }
}
