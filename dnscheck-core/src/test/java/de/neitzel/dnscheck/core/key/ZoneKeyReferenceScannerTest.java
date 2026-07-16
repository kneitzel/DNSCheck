package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.zone.ZoneCatalog;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneKeyReferenceScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void findsInUseReferencesAndMissingKeys() throws Exception {
        Path zonesDir = tempDir.resolve("zones");
        Path keysDir = tempDir.resolve("keys");
        Files.createDirectories(zonesDir);
        Files.createDirectories(keysDir.resolve("admin.u"));
        Files.writeString(
                keysDir.resolve("admin.u/present.key"),
                """
                key "present" {
                  algorithm hmac-sha256;
                  secret "abc";
                };
                """);

        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, "admin.u", "one.example", "ns1.one.example", "admin.u/present.key");
        creator.create(zonesDir, "admin.u", "two.example", "ns1.two.example", "admin.u/missing.key");
        creator.create(zonesDir, "dns-team.g", "two.example", "ns1.team.example", "admin.u/missing.key");

        ZoneCatalog catalog = new ZoneCatalog(zonesDir);

        List<ZoneKeyReferenceScanner.KeyReference> refs =
                ZoneKeyReferenceScanner.findReferences(catalog, "admin.u/missing.key");
        assertEquals(2, refs.size());

        List<ZoneKeyReferenceScanner.MissingKeySummary> missing =
                ZoneKeyReferenceScanner.findMissing(catalog, keysDir);
        assertEquals(1, missing.size());
        assertEquals("admin.u/missing.key", missing.getFirst().scopedKeyPath());
        assertEquals(2, missing.getFirst().affectedEntryCount());

        assertTrue(ZoneKeyReferenceScanner.keyExists(keysDir, "admin.u/present.key"));
        assertFalse(ZoneKeyReferenceScanner.keyExists(keysDir, "admin.u/missing.key"));
    }
}
