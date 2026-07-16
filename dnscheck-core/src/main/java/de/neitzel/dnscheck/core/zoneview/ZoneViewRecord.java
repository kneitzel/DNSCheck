package de.neitzel.dnscheck.core.zoneview;

import de.neitzel.dnscheck.core.model.DnsRecord;

/**
 * One row in the zone view merging live DNS and zone definition data.
 */
public record ZoneViewRecord(
        String nameFqdn,
        String type,
        RecordStatus status,
        String rdata,
        Long ttl,
        String expectedRdata,
        Long expectedTtl) {

    public static ZoneViewRecord enforced(DnsRecord live) {
        return new ZoneViewRecord(
                live.nameFqdn(),
                live.type(),
                RecordStatus.ENFORCED,
                live.rdata(),
                live.ttl(),
                null,
                null);
    }

    public static ZoneViewRecord adHoc(DnsRecord live) {
        return new ZoneViewRecord(
                live.nameFqdn(),
                live.type(),
                RecordStatus.AD_HOC,
                live.rdata(),
                live.ttl(),
                null,
                null);
    }

    public static ZoneViewRecord missing(String nameFqdn, String type, String expectedRdata, Long expectedTtl) {
        return new ZoneViewRecord(
                nameFqdn,
                type,
                RecordStatus.MISSING,
                null,
                null,
                expectedRdata,
                expectedTtl);
    }

    public static ZoneViewRecord drifted(
            String nameFqdn,
            String type,
            String liveRdata,
            Long liveTtl,
            String expectedRdata,
            Long expectedTtl) {
        return new ZoneViewRecord(
                nameFqdn,
                type,
                RecordStatus.DRIFTED,
                liveRdata,
                liveTtl,
                expectedRdata,
                expectedTtl);
    }

    public static ZoneViewRecord markedForDeletion(DnsRecord live) {
        return new ZoneViewRecord(
                live.nameFqdn(),
                live.type(),
                RecordStatus.MARKED_FOR_DELETION,
                live.rdata(),
                live.ttl(),
                null,
                null);
    }
}
