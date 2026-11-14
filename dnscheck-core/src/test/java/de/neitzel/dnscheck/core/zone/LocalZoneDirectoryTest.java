package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.reconcile.DefaultReconciliationEngine;
import de.neitzel.dnscheck.core.reconcile.ZoneOutcome;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalZoneDirectoryTest {
    @TempDir Path temp;

    private Path definition(String zone) throws Exception {
        Files.writeString(temp.resolve("shared.key"), """
                key "test" { algorithm hmac-sha256; secret "dGVzdA=="; };
                """);
        Path file = temp.resolve(zone + ".domain");
        Files.writeString(file, """
                {"zone":"%s", "nameserver":"ns.example.com", "keyFile":"shared.key", "records":[
                  {"name":"www", "type":"A", "rdata":"192.0.2.1", "ttl":null, "delete":false, "rdataPattern":""},
                  {"name":"mail", "type":"A", "rdata":"192.0.2.2", "ttl":-1},
                  {"name":"old", "type":"TXT", "delete":true, "rdataPattern":"old*"}
                ]}
                """.formatted(zone));
        return file;
    }

    @Test
    void flatDefinitionsShareKeysAndRoundTripTheirSemantics() throws Exception {
        definition("example.com");
        definition("example.net");
        Files.writeString(temp.resolve("ignored.json"), "{}");
        Files.createDirectories(temp.resolve("owner.u"));
        Files.writeString(temp.resolve("owner.u/nested.domain"), "{}");
        var directory = new LocalZoneDirectory(temp);
        assertEquals(2, directory.entries().size());
        assertTrue(directory.entries().stream().allMatch(LocalZoneDirectory.Entry::valid));
        var original = directory.read("example.com");
        directory.write(original);
        var saved = directory.read("example.com");
        assertEquals(original.zone(), saved.zone());
        assertEquals(original.records().stream().map(r -> r.nameFqdn()).toList(),
                saved.records().stream().map(r -> r.nameFqdn()).toList());
        assertInstanceOf(TtlPolicy.Ignore.class, saved.records().get(0).ttlPolicy());
        assertInstanceOf(TtlPolicy.UseZoneDefault.class, saved.records().get(1).ttlPolicy());
        assertTrue(saved.records().get(2).deletionMarker());
        assertEquals("old*", saved.records().get(2).rdataPattern());
    }

    @Test
    void reportsInvalidFilesAndContinuesWithValidZonesWithoutContactingDns() throws Exception {
        definition("example.com");
        Files.writeString(temp.resolve("bad.domain"), "invalid JSON");
        var engine = mock(DefaultReconciliationEngine.class);
        when(engine.reconcileDefinition(any(), any(), eq(true)))
                .thenReturn(ZoneReconciliationResult.success("example.com"));
        var results = new LocalZoneDirectory(temp, engine).reconcileAll(true).zoneResults();
        assertEquals(2, results.size());
        assertEquals(ZoneOutcome.FAILED, results.get(0).outcome());
        assertEquals("bad", results.get(0).zoneName());
        assertEquals(ZoneOutcome.SUCCESS, results.get(1).outcome());
        verify(engine).reconcileDefinition(any(), any(), eq(true));
    }

    @Test
    void unknownFieldsAtEitherLevelAndMissingKeysAreExplicitErrors() throws Exception {
        Path file = definition("example.com");
        String json = Files.readString(file);
        var directory = new LocalZoneDirectory(temp);
        Files.writeString(file, json.replace("\"zone\":", "\"unexpected\":true, \"zone\":"));
        assertFalse(directory.entries().getFirst().valid());
        assertThrows(Exception.class, () -> directory.read("example.com"));
        Files.writeString(file, json.replace("\"ttl\":null", "\"unknownRecordField\":true"));
        assertFalse(directory.entries().getFirst().valid());
        Files.writeString(file, json);
        Files.delete(temp.resolve("shared.key"));
        assertFalse(directory.entries().getFirst().valid());
        assertEquals(ZoneOutcome.FAILED, directory.reconcile("example.com", true).outcome());
    }

    @Test
    void rejectsEscapingKeyPathsAndSymlinksAndZoneSymlinks() throws Exception {
        Path file = definition("example.com");
        String json = Files.readString(file);
        var directory = new LocalZoneDirectory(temp);
        for (String key : new String[]{"../outside.key", "/outside.key", "sub/key.key"}) {
            Files.writeString(file, json.replace("shared.key", key));
            assertFalse(directory.entries().getFirst().valid());
        }
        Files.writeString(file, json);
        Path outside = Files.createTempFile("dnscheck-outside", ".key");
        try {
            Files.delete(temp.resolve("shared.key"));
            Files.createSymbolicLink(temp.resolve("shared.key"), outside);
            assertFalse(directory.entries().getFirst().valid());
            Files.delete(file);
            Files.createSymbolicLink(file, outside);
            assertFalse(directory.entries().getFirst().valid());
        } finally {
            Files.delete(outside);
        }
        assertThrows(Exception.class, () -> directory.read("../example.com"));
    }

    @Test
    void emptyDirectoriesAndMissingTargetsFail() throws Exception {
        var directory = new LocalZoneDirectory(temp);
        assertThrows(java.io.IOException.class, directory::entries);
        assertEquals(ZoneOutcome.FAILED, directory.reconcileAll(true).zoneResults().getFirst().outcome());
        assertEquals(ZoneOutcome.FAILED, directory.reconcile("missing.example", true).outcome());
    }

    @Test
    void filenameMustMatchZoneAndUnknownFieldsAddedAfterOpeningPreventOverwrite() throws Exception {
        Path file = definition("example.com");
        var directory = new LocalZoneDirectory(temp);
        var original = directory.read("example.com");
        String changed = Files.readString(file).replace("\"zone\":", "\"unknown\":1, \"zone\":");
        Files.writeString(file, changed);
        assertThrows(Exception.class, () -> directory.write(original));
        assertEquals(changed, Files.readString(file));
        Files.writeString(file, "{\"zone\":\"other.com\",\"nameserver\":\"ns\",\"keyFile\":\"shared.key\"}");
        assertFalse(directory.entries().getFirst().valid());
    }
}
