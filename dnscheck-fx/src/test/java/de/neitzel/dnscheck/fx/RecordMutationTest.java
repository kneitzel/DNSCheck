package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.dns.*;
import de.neitzel.dnscheck.core.model.*;
import de.neitzel.dnscheck.core.zone.LocalZoneDirectory;
import de.neitzel.dnscheck.fx.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecordMutationTest {
    @TempDir Path temp;
    private final LiveZoneClient client = mock(LiveZoneClient.class);
    private final NsupdateClient updater = mock(NsupdateClient.class);
    private final ZoneFiles files = new ZoneFiles(client, updater);

    private ZoneDefinition fixture() throws Exception {
        Files.writeString(temp.resolve("test.key"), """
                key "test" { algorithm hmac-sha256; secret "dGVzdA=="; };
                """);
        Files.writeString(temp.resolve("example.com.domain"), """
                {"zone":"example.com","nameserver":"ns.example.com","keyFile":"test.key","records":[
                {"name":"www","type":"A","rdata":"192.0.2.1","ttl":300,"rdataPattern":""}]}
                """);
        return new LocalZoneDirectory(temp).read("example.com");
    }
    private static RecordSpec spec(String name, String value, TtlPolicy ttl) {
        return new RecordSpec(name, "A", value, null, false, ttl);
    }
    private static LiveZone live(DnsRecord... records) { return new LiveZone("example.com", 3600, List.of(records)); }
    private void responds(LiveZone live) throws Exception {
        when(client.loadIncludingSoa(anyString(), anyString(), any())).thenReturn(live);
    }

    @Test void fileOnlyEditWritesNoDnsAndNewRecordWithNeitherFlagIsRejected() throws Exception {
        var definition = fixture();
        var added = spec("mail", "192.0.2.2", new TtlPolicy.Ignore());
        var result = files.applyRecord(definition, new RecordChange(null, added, true, false));
        assertEquals(2, new LocalZoneDirectory(temp).read("example.com").records().size());
        assertFalse(result.onlineAttempted());
        assertEquals(new LocalZoneDirectory(temp).read("example.com"), result.definition());
        var next = files.applyRecord(result.definition(), new RecordChange(null,
                spec("second", "192.0.2.3", new TtlPolicy.Ignore()), true, false));
        assertEquals(3, next.definition().records().size());
        verifyNoInteractions(client, updater);
        assertThrows(IllegalArgumentException.class, () -> new RecordChange(null, added, false, false));
    }
    @Test void onlineOnlyCreationDoesNotWriteTheFileAndUsesZoneDefaultTtl() throws Exception {
        var definition = fixture();
        responds(live());
        String before = Files.readString(definition.sourcePath());
        var result = files.applyRecord(definition, new RecordChange(null, spec("mail", "192.0.2.2", new TtlPolicy.UseZoneDefault()), false, true));
        assertEquals(before, Files.readString(definition.sourcePath()));
        assertTrue(result.onlineApplied());
        verify(updater).apply(eq("example.com"), anyString(), any(), argThat(changes ->
                changes.size() == 1 && changes.getFirst().action() == ChangeAction.ADD && changes.getFirst().ttl() == 3600));
    }
    @Test void uncheckingFileStorageDemotesWithoutDeletingLiveDns() throws Exception {
        var definition = fixture();
        var row = new RecordRow(definition.records().getFirst(), null, "Missing");
        files.applyRecord(definition, new RecordChange(row, row.stored(), false, false));
        assertTrue(new LocalZoneDirectory(temp).read("example.com").records().isEmpty());
        verifyNoInteractions(client, updater);
    }
    @Test void combinedEditReplacesOnlySelectedLiveRdataAndPreservesIgnoredTtl() throws Exception {
        var definition = fixture();
        var old = new DnsRecord("www.example.com.", "A", "192.0.2.1", 600);
        var unrelated = new DnsRecord("www.example.com.", "A", "192.0.2.9", 600);
        responds(live(old, unrelated));
        var row = new RecordRow(definition.records().getFirst(), old, "TTL drift");
        var result = files.applyRecord(definition, new RecordChange(row, spec("www", "192.0.2.2", new TtlPolicy.Ignore()), true, true));
        assertTrue(result.onlineApplied());
        assertEquals("192.0.2.2", new LocalZoneDirectory(temp).read("example.com").records().getFirst().rdata());
        verify(updater).apply(anyString(), anyString(), any(), argThat(changes -> changes.size() == 2
                && changes.get(0).action() == ChangeAction.DELETE && changes.get(0).rdata().equals("192.0.2.1")
                && changes.get(1).action() == ChangeAction.ADD && changes.get(1).ttl() == 600));
    }
    @Test void removalPatternDeletesOnlyMatchingRecordsAndCanBeStored() throws Exception {
        var definition = fixture();
        responds(live(new DnsRecord("old.example.com.", "TXT", "old-one", 300),
                new DnsRecord("old.example.com.", "TXT", "keep", 300), new DnsRecord("other.example.com.", "TXT", "old-two", 300)));
        var marker = new RecordSpec("old.example.com.", "TXT", null, "old*", true, new TtlPolicy.Ignore());
        files.applyRecord(definition, new RecordChange(null, marker, true, true));
        assertEquals(2, new LocalZoneDirectory(temp).read("example.com").records().size());
        verify(updater).apply(anyString(), anyString(), any(), argThat(changes -> changes.size() == 1
                && changes.getFirst().action() == ChangeAction.DELETE && changes.getFirst().rdata().equals("old-one")));
    }
    @Test void onlineFailureLeavesSavedFileIntentAndReportsPartialCompletion() throws Exception {
        var definition = fixture();
        responds(live());
        doThrow(new IOException("offline")).when(updater).apply(anyString(), anyString(), any(), anyList());
        var result = files.applyRecord(definition, new RecordChange(null, spec("mail", "192.0.2.2", new TtlPolicy.Ignore()), true, true));
        assertEquals(2, new LocalZoneDirectory(temp).read("example.com").records().size());
        assertFalse(result.onlineApplied());
        assertTrue(result.message().contains("Domain file saved."));
        assertTrue(result.message().contains("offline"));
        assertNull(result.live());
    }
    @Test void rejectsFileConflictsAndStaleLiveRecordsWithoutBroadUpdates() throws Exception {
        var definition = fixture();
        Files.writeString(definition.sourcePath(), Files.readString(definition.sourcePath()).replace("192.0.2.1", "192.0.2.3"));
        var request = new RecordChange(null, spec("mail", "192.0.2.2", new TtlPolicy.Ignore()), true, true);
        assertThrows(IOException.class, () -> files.applyRecord(definition, request));
        verifyNoInteractions(client, updater);
        var current = new LocalZoneDirectory(temp).read("example.com");
        var stale = new RecordRow(null, new DnsRecord("gone.example.com.", "A", "192.0.2.4", 300), "Live only");
        responds(live());
        var result = files.applyRecord(current, new RecordChange(stale, spec("gone", "192.0.2.5", new TtlPolicy.Ignore()), false, true));
        assertFalse(result.onlineApplied());
        verifyNoInteractions(updater);
    }
    @Test void validationRejectsOutOfZoneNamesInvalidRdataSoaAndTraversal() throws Exception {
        var definition = fixture();
        for (var invalid : List.of(spec("outside.example.net.", "192.0.2.2", new TtlPolicy.Ignore()),
                spec("www", "not-an-address", new TtlPolicy.Ignore()))) {
            assertThrows(Exception.class, () -> files.applyRecord(definition, new RecordChange(null, invalid, true, true)));
        }
        assertThrows(Exception.class, () -> ZoneFiles.validateRecord("example.com", new RecordSpec("@", "SOA", "data", null, false, new TtlPolicy.Ignore())));
        verifyNoInteractions(client, updater);
        assertEquals(1, new LocalZoneDirectory(temp).read("example.com").records().size());
    }
}
