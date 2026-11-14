package de.neitzel.dnscheck.core.zoneview;

import de.neitzel.dnscheck.core.model.DnsRecord;

/**
 * One row in the zone view merging live DNS and zone definition data.
 *
 * @param nameFqdn the fully qualified owner name.
 * @param type the DNS record type.
 * @param status the record status in this zone view.
 * @param rdata the live rdata, or {@code null} when the record is missing from the live zone.
 * @param ttl the live TTL in seconds, or {@code null} when the record is missing from the live zone.
 * @param expectedRdata the rdata from the managed record when the row shows drift or a missing record,
 *                      otherwise {@code null}.
 * @param expectedTtl the TTL the TTL policy would enforce when the row shows drift or a missing record,
 *                    otherwise {@code null}.
 */
public record ZoneViewRecord(
        String nameFqdn,
        String type,
        RecordStatus status,
        String rdata,
        Long ttl,
        String expectedRdata,
        Long expectedTtl) {

    /**
     * Builds a row for a live record that matches its managed record.
     *
     * @param live the matching live record.
     * @return an enforced zone view row. Expected rdata and TTL are left unset.
     */
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

    /**
     * Builds a row for a live record that is not in the zone definition.
     *
     * @param live the ad-hoc live record.
     * @return an ad-hoc zone view row. Expected rdata and TTL are left unset.
     */
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

    /**
     * Builds a row for a managed record that AXFR did not return.
     *
     * @param nameFqdn the fully qualified owner name from the zone definition.
     * @param type the DNS record type.
     * @param expectedRdata the rdata the managed record requires.
     * @param expectedTtl the TTL the TTL policy would enforce, in seconds.
     * @return a missing zone view row. Live rdata and TTL are left unset.
     */
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

    /**
     * Builds a row whose live rdata or TTL differs from the managed record.
     *
     * @param nameFqdn the fully qualified owner name.
     * @param type the DNS record type.
     * @param liveRdata the rdata currently in the live zone.
     * @param liveTtl the TTL currently in the live zone, in seconds.
     * @param expectedRdata the rdata the managed record requires.
     * @param expectedTtl the TTL the TTL policy would enforce, in seconds.
     * @return a drifted zone view row.
     */
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

    /**
     * Builds a row for a live record that matches a deletion marker.
     *
     * @param live the live record that matched the marker.
     * @return a deletion-marked zone view row. Expected rdata and TTL are left unset.
     */
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
