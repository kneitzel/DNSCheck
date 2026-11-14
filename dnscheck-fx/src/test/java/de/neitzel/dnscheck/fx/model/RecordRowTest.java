package de.neitzel.dnscheck.fx.model;

import de.neitzel.dnscheck.core.model.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecordRowTest {
    private static final RecordSpec STORED = new RecordSpec("www.example.com.", "A", "192.0.2.1", null, false, new TtlPolicy.Explicit(300));
    private static final RecordSpec REMOVAL = new RecordSpec("old.example.com.", "TXT", null, "old*", true, new TtlPolicy.Ignore());
    private static ZoneDefinition definition(List<RecordSpec> specs) {
        return new ZoneDefinition("example.com", "ns.example.com", "test.key", specs, Path.of("example.com.domain"));
    }
    @Test void mergesExactMatchesButKeepsEveryLiveValueAndUnmatchedRemoval() {
        var matching = new DnsRecord("www.example.com.", "A", "192.0.2.1", 600);
        var other = new DnsRecord("www.example.com.", "A", "192.0.2.2", 300);
        var rows = RecordRow.merge(definition(List.of(STORED, REMOVAL)), new LiveZone("example.com", 3600, List.of(matching, other)));
        assertEquals(3, rows.size());
        var enforced = rows.stream().filter(row -> row.kind() == RecordRow.Kind.ENFORCED).findFirst().orElseThrow();
        assertEquals(matching, enforced.live());
        assertEquals("TTL drift", enforced.status());
        assertEquals(1, rows.stream().filter(row -> row.kind() == RecordRow.Kind.LIVE).count());
        assertTrue(rows.stream().anyMatch(row -> row.kind() == RecordRow.Kind.REMOVAL && row.live() == null));
    }
    @Test void showsEveryRemovalMatchAndAllRecordsIncludingSoa() {
        var one = new DnsRecord("old.example.com.", "TXT", "old-one", 300);
        var two = new DnsRecord("old.example.com.", "TXT", "old-two", 300);
        var soa = new DnsRecord("example.com.", "SOA", "ns.example.com. hostmaster.example.com. 1 60 60 60 60", 300);
        var rows = RecordRow.merge(definition(List.of(REMOVAL)), new LiveZone("example.com", 3600, List.of(one, two, soa)));
        assertEquals(3, rows.size());
        assertEquals(2, rows.stream().filter(row -> row.kind() == RecordRow.Kind.REMOVAL).count());
        assertTrue(rows.stream().filter(row -> row.type().equals("SOA")).findFirst().orElseThrow().readOnly());
    }
    @Test void unavailableLiveDataIsNotReportedAsMissingDns() {
        var rows = RecordRow.merge(definition(List.of(STORED, REMOVAL)), null);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.status().equals("Live not loaded")));
    }
}
