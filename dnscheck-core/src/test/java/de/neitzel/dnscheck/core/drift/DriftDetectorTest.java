package de.neitzel.dnscheck.core.drift;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests drift detection between a zone definition and a live zone.
 */
class DriftDetectorTest {

    /**
     * Detector used by the drift scenarios.
     */
    private final DriftDetector detector = new DriftDetector();

    /**
     * A managed record that is absent from the live zone is planned as an addition.
     */
    @Test
    void detectsMissingManagedRecord() {
        ZoneDefinition definition = zone(List.of(
                managed("example.com.", "TXT", "\"v=spf1 -all\"", new TtlPolicy.Ignore())));

        List<PlannedChange> changes = detector.detect(definition, liveZone(List.of()));

        assertEquals(1, changes.size());
        assertEquals(ChangeAction.ADD, changes.getFirst().action());
    }

    /**
     * A managed record with an enforced TTL is planned as an update when the live TTL differs.
     */
    @Test
    void detectsTtlMismatchWhenEnforced() {
        ZoneDefinition definition = zone(List.of(
                managed("mail.example.com.", "A", "192.0.2.10", new TtlPolicy.Explicit(300))));

        LiveZone liveZone = liveZone(List.of(new DnsRecord("mail.example.com.", "A", "192.0.2.10", 3600)));

        var changes = detector.detect(definition, liveZone);

        assertEquals(1, changes.size());
        assertEquals(ChangeAction.UPDATE, changes.getFirst().action());
        assertEquals(300L, changes.getFirst().ttl());
    }

    /**
     * TTL is left unchanged when the managed record's TTL policy does not enforce it.
     */
    @Test
    void ignoresTtlWhenNotEnforced() {
        ZoneDefinition definition = zone(List.of(
                managed("mail.example.com.", "A", "192.0.2.10", new TtlPolicy.Ignore())));

        LiveZone liveZone = liveZone(List.of(new DnsRecord("mail.example.com.", "A", "192.0.2.10", 3600)));

        assertTrue(detector.detect(definition, liveZone).isEmpty());
    }

    /**
     * A live record that matches a deletion marker's rdata pattern is planned for deletion.
     */
    @Test
    void detectsDeletionMarkerMatch() {
        ZoneDefinition definition = zone(List.of(
                new RecordSpec("bad.example.com.", "TXT", null, "*remove-me*", true, new TtlPolicy.Ignore())));

        LiveZone liveZone = liveZone(List.of(
                new DnsRecord("bad.example.com.", "TXT", "please-remove-me", 300)));

        var changes = detector.detect(definition, liveZone);

        assertEquals(1, changes.size());
        assertEquals(ChangeAction.DELETE, changes.getFirst().action());
    }

    /**
     * Builds a zone definition fixture for the example.com zone.
     *
     * @param records managed records and deletion markers to include.
     * @return the zone definition.
     */
    private static ZoneDefinition zone(List<RecordSpec> records) {
        return new ZoneDefinition("example.com", "ns1.example.com", "example.com.key", records, Path.of("example.com.json"));
    }

    /**
     * Builds a managed record fixture that is not a deletion marker.
     *
     * @param name record name, already an FQDN.
     * @param type DNS record type.
     * @param rdata record rdata.
     * @param ttlPolicy TTL policy for the managed record.
     * @return the managed record.
     */
    private static RecordSpec managed(String name, String type, String rdata, TtlPolicy ttlPolicy) {
        return new RecordSpec(name, type, rdata, null, false, ttlPolicy);
    }

    /**
     * Builds a live zone fixture whose zone default TTL is 3600 seconds.
     *
     * @param records records present in the live zone.
     * @return the live zone.
     */
    private static LiveZone liveZone(List<DnsRecord> records) {
        return new LiveZone("example.com", 3600, records);
    }
}
