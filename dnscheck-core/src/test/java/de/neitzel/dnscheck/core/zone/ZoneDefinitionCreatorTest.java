package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests creation of a zone definition in an owner folder.
 */
class ZoneDefinitionCreatorTest {

    /**
     * Owner folder used when creating zone definitions.
     */
    private static final String OWNER = "admin.u";

    /**
     * Creating a zone writes a valid zone definition with the zone name, nameserver, key file, and an empty managed record list.
     */
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

    /**
     * Creating a zone definition that already exists in the owner folder is rejected.
     */
    @Test
    void createRejectsDuplicateZone(@TempDir Path zonesDir) throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "dup.test", "ns1.dup.test", "admin.u/dup.test.key");

        assertThrows(ZoneAlreadyExistsException.class, () ->
                creator.create(zonesDir, OWNER, "dup.test", "ns1.dup.test", "admin.u/dup.test.key"));
    }
}
