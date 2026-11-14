package de.neitzel.dnscheck.fx;

import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionStore;
import de.neitzel.dnscheck.fx.model.RecordEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saves a zone definition edited as ordinary java beans.
 */
class ZoneFilesTest {

    /**
     * A temporary directory for zone files.
     */
    @TempDir
    Path temp;

    /**
     * Saves edited rdata, a numeric TTL, and a deletion marker.
     */
    @Test
    void saveWritesEditedRdataTtlAndADeletionMarker() throws Exception {
        Path zones = temp.resolve("zones");
        Path owner = zones.resolve("admin.u");
        Files.createDirectories(owner);
        Path file = owner.resolve("example.com.json");
        Files.writeString(file, """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": [
                    { "name": "mail", "type": "A", "rdata": "192.0.2.10" }
                  ]
                }
                """);

        ZoneFiles files = new ZoneFiles();
        var entries = files.load(zones);
        assertEquals(1, entries.size());
        assertEquals("admin.u/example.com", entries.get(0).getCatalogRef());
        assertTrue(entries.get(0).isValid());

        var editor = files.open(zones, entries.get(0));
        assertEquals("mail.example.com.", editor.getRecords().get(0).getName());
        editor.getRecords().get(0).setRdata("192.0.2.11");
        editor.getRecords().get(0).setTtl("300");

        RecordEditor marker = new RecordEditor();
        marker.setName("old.example.com");
        marker.setType("TXT");
        marker.setDeletionMarker(true);
        marker.setRdataPattern("gone*");
        editor.getRecords().add(marker);
        files.save(editor);

        ZoneDefinition definition = new ZoneDefinitionStore().read(file);
        assertEquals("192.0.2.11", definition.records().get(0).rdata());
        assertInstanceOf(TtlPolicy.Explicit.class, definition.records().get(0).ttlPolicy());
        assertEquals(300L, ((TtlPolicy.Explicit) definition.records().get(0).ttlPolicy()).seconds());
        assertTrue(definition.records().get(1).deletionMarker());
        assertEquals("gone*", definition.records().get(1).rdataPattern());
    }

    /**
     * Rejects a TTL that is not a number.
     */
    @Test
    void saveRejectsATtlThatIsNotANumber() throws Exception {
        Path zones = temp.resolve("zones");
        Path owner = zones.resolve("admin.u");
        Files.createDirectories(owner);
        Path file = owner.resolve("example.com.json");
        Files.writeString(file, """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": [
                    { "name": "mail", "type": "A", "rdata": "192.0.2.10", "ttl": -1 }
                  ]
                }
                """);

        ZoneFiles files = new ZoneFiles();
        var editor = files.open(zones, files.load(zones).get(0));
        assertEquals("-1", editor.getRecords().get(0).getTtl());
        editor.getRecords().get(0).setTtl("nope");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> files.save(editor));
        assertTrue(failure.getMessage().startsWith("Record 1:"));
    }
}
