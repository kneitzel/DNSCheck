package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneCatalogIntrospectionTest {

    @Test
    void introspectReturnsValidAndInvalidEntries(@TempDir Path tempDir) throws Exception {
        Path ownerDir = tempDir.resolve("admin.u");
        Files.createDirectories(ownerDir);
        Files.writeString(
                ownerDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                ownerDir.resolve("wrong.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(ownerDir.resolve("broken.json"), "{ this is not json");
        Files.writeString(
                tempDir.resolve("legacy-flat.json"),
                """
                {
                  "zone": "legacy.test",
                  "nameserver": "ns1.legacy.test",
                  "keyFile": "legacy.test.key",
                  "records": []
                }
                """);

        List<ZoneCatalogEntry> entries = new ZoneCatalog(tempDir).introspect();

        assertEquals(3, entries.size());

        ZoneCatalogEntry valid = findByFilename(entries, "admin.u/example.com.json");
        assertTrue(valid.valid());
        assertEquals("admin.u", valid.ownerFolder());
        assertEquals("example.com", valid.zone());
        assertEquals("ns1.example.com", valid.nameserver());
        assertEquals("admin.u/example.com.key", valid.keyFile());

        ZoneCatalogEntry broken = findByFilename(entries, "admin.u/broken.json");
        assertEquals("broken", broken.zone());
        assertFalse(broken.valid());
        assertNotNull(broken.error());

        ZoneCatalogEntry filenameMismatch = findByFilename(entries, "admin.u/wrong.json");
        assertEquals("example.com", filenameMismatch.zone());
        assertFalse(filenameMismatch.valid());
        assertTrue(filenameMismatch.error().contains("does not match filename"));
    }

    private static ZoneCatalogEntry findByFilename(List<ZoneCatalogEntry> entries, String filename) {
        return entries.stream()
                .filter(entry -> entry.filename().equals(filename))
                .findFirst()
                .orElseThrow();
    }
}
