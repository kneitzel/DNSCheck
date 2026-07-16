package de.neitzel.dnscheck.core.zoneview;

import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneViewMergerTest {

    private final ZoneViewMerger merger = new ZoneViewMerger();

    @Test
    void classifiesEnforcedAdHocMissingDriftedAndDeletionMarkerRecords() {
        ZoneDefinition definition = new ZoneDefinition(
                "example.com",
                "ns1.example.com",
                "example.com.key",
                List.of(
                        managed("mail.example.com.", "A", "192.0.2.10", new TtlPolicy.Ignore()),
                        managed("missing.example.com.", "TXT", "\"gone\"", new TtlPolicy.Ignore()),
                        managed("drift.example.com.", "A", "192.0.2.20", new TtlPolicy.Ignore()),
                        new RecordSpec("bad.example.com.", "TXT", null, "*remove*", true, new TtlPolicy.Ignore())),
                Path.of("example.com.json"));

        LiveZone liveZone = new LiveZone(
                "example.com",
                3600,
                List.of(
                        new DnsRecord("mail.example.com.", "A", "192.0.2.10", 300),
                        new DnsRecord("extra.example.com.", "A", "192.0.2.99", 300),
                        new DnsRecord("drift.example.com.", "A", "192.0.2.21", 300),
                        new DnsRecord("bad.example.com.", "TXT", "please-remove-me", 300)));

        ZoneView view = merger.merge(definition, liveZone);

        assertEquals("example.com", view.zone());
        assertStatus(view, "mail.example.com.", "A", "192.0.2.10", RecordStatus.ENFORCED);
        assertStatus(view, "missing.example.com.", "TXT", null, RecordStatus.MISSING);
        assertStatus(view, "drift.example.com.", "A", "192.0.2.21", RecordStatus.DRIFTED);
        assertStatus(view, "extra.example.com.", "A", "192.0.2.99", RecordStatus.AD_HOC);
        assertStatus(view, "bad.example.com.", "TXT", "please-remove-me", RecordStatus.MARKED_FOR_DELETION);

        ZoneViewRecord drifted = find(view, "drift.example.com.", "A", "192.0.2.21");
        assertEquals("192.0.2.20", drifted.expectedRdata());
    }

    private static void assertStatus(
            ZoneView view, String name, String type, String rdata, RecordStatus status) {
        assertTrue(view.records().stream().anyMatch(record ->
                record.nameFqdn().equalsIgnoreCase(name)
                        && record.type().equalsIgnoreCase(type)
                        && (rdata == null ? record.rdata() == null : rdata.equals(record.rdata()))
                        && record.status() == status));
    }

    private static ZoneViewRecord find(ZoneView view, String name, String type, String rdata) {
        return view.records().stream()
                .filter(record -> record.nameFqdn().equalsIgnoreCase(name)
                        && record.type().equalsIgnoreCase(type)
                        && rdata.equals(record.rdata()))
                .findFirst()
                .orElseThrow();
    }

    private static RecordSpec managed(String name, String type, String rdata, TtlPolicy ttlPolicy) {
        return new RecordSpec(name, type, rdata, null, false, ttlPolicy);
    }
}
