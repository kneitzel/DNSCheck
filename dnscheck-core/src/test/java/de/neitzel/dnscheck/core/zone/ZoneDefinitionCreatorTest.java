package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneDefinitionCreatorTest {

    private static final String OWNER = "admin.u";

    @Test
    void createWritesEmptyZoneDefinition(@TempDir Path zonesDir) throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        ZoneCatalogEntry entry = creator.create(zonesDir, OWNER, "new.test", "ns1.new.test", "admin.u/new.test.key");

        assertEquals("admin.u", entry.ownerFolder());
        assertEquals("new.test", entry.zone());
        assertEquals("admin.u/new.test.json", entry.filename());
        assertTrue(entry.valid());
        Path zoneFile = zonesDir.resolve("admin.u/new.test.json");
        assertTrue(Files.isRegularFile(zoneFile));
        String json = Files.readString(zoneFile);
        assertTrue(json.contains("\"zone\" : \"new.test\"") || json.contains("\"zone\": \"new.test\""));
        assertTrue(json.contains("\"records\""));
        assertTrue(json.contains("\"nameserver\""));
        assertTrue(json.contains("\"keyFile\""));
    }

    @Test
    void createRejectsDuplicateZone(@TempDir Path zonesDir) throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "dup.test", "ns1.dup.test", "admin.u/dup.test.key");

        assertThrows(ZoneAlreadyExistsException.class, () ->
                creator.create(zonesDir, OWNER, "dup.test", "ns1.dup.test", "admin.u/dup.test.key"));
    }
}
