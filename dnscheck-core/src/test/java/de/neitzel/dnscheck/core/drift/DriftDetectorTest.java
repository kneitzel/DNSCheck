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

class DriftDetectorTest {

    private final DriftDetector detector = new DriftDetector();

    @Test
    void detectsMissingManagedRecord() {
        ZoneDefinition definition = zone(List.of(
                managed("example.com.", "TXT", "\"v=spf1 -all\"", new TtlPolicy.Ignore())));

        List<PlannedChange> changes = detector.detect(definition, liveZone(List.of()));

        assertEquals(1, changes.size());
        assertEquals(ChangeAction.ADD, changes.getFirst().action());
    }

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

    @Test
    void ignoresTtlWhenNotEnforced() {
        ZoneDefinition definition = zone(List.of(
                managed("mail.example.com.", "A", "192.0.2.10", new TtlPolicy.Ignore())));

        LiveZone liveZone = liveZone(List.of(new DnsRecord("mail.example.com.", "A", "192.0.2.10", 3600)));

        assertTrue(detector.detect(definition, liveZone).isEmpty());
    }

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

    private static ZoneDefinition zone(List<RecordSpec> records) {
        return new ZoneDefinition("example.com", "ns1.example.com", "example.com.key", records, Path.of("example.com.json"));
    }

    private static RecordSpec managed(String name, String type, String rdata, TtlPolicy ttlPolicy) {
        return new RecordSpec(name, type, rdata, null, false, ttlPolicy);
    }

    private static LiveZone liveZone(List<DnsRecord> records) {
        return new LiveZone("example.com", 3600, records);
    }
}
