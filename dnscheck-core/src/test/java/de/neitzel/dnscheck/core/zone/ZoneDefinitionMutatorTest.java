package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests update, deletion, and ownership transfer of a zone definition.
 */
class ZoneDefinitionMutatorTest {

    /**
     * Owner folder that holds the zone definitions under test.
     */
    private static final String OWNER = "admin.u";

    /**
     * Temporary zone catalog root.
     */
    @TempDir
    Path zonesDir;

    /**
     * Updating a zone definition stores the new nameserver and key file path.
     */
    @Test
    void updateChangesNameserverAndKeyFile() throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "example.com", "ns1.example.com", "admin.u/example.com.key");

        ZoneDefinitionMutator mutator = new ZoneDefinitionMutator();
        ZoneCatalogEntry updated =
                mutator.update(zonesDir, OWNER, "example.com", "ns2.example.com", "admin.u/example.com.new.key");

        assertEquals("ns2.example.com", updated.nameserver());
        Path zoneFile = zonesDir.resolve("admin.u/example.com.json");
        assertEquals("ns2.example.com", new ZoneDefinitionStore().read(zoneFile).nameserver());
        assertEquals("admin.u/example.com.new.key", new ZoneDefinitionStore().read(zoneFile).keyFile());
    }

    /**
     * Deleting a zone definition removes its file from the owner folder.
     */
    @Test
    void deleteRemovesZoneDefinitionFile() throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "gone.test", "ns1.gone.test", "admin.u/gone.test.key");
        Path zoneFile = zonesDir.resolve("admin.u/gone.test.json");
        assertFalse(Files.notExists(zoneFile));

        new ZoneDefinitionMutator().delete(zonesDir, OWNER, "gone.test");

        assertFalse(Files.exists(zoneFile));
    }

    /**
     * Updating a zone definition that is not in the catalog is rejected.
     */
    @Test
    void updateMissingZoneThrows() {
        assertThrows(ZoneDefinitionNotFoundException.class, () ->
                new ZoneDefinitionMutator().update(zonesDir, OWNER, "missing.test", "ns1.missing.test", null));
    }

    /**
     * Transfer moves the zone definition from the operator owner folder into the group owner folder.
     */
    @Test
    void transferMovesZoneToNewOwnerFolder() throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "example.com", "ns1.example.com", "admin.u/example.com.key");

        ZoneCatalogEntry moved =
                new ZoneDefinitionMutator().transfer(zonesDir, OWNER, "example.com", "dns-team.g");

        assertEquals("dns-team.g", moved.ownerFolder());
        assertEquals("dns-team.g/example.com.json", moved.filename());
        assertFalse(Files.exists(zonesDir.resolve("admin.u/example.com.json")));
        assertEquals(
                "ns1.example.com",
                new ZoneDefinitionStore().read(zonesDir.resolve("dns-team.g/example.com.json")).nameserver());
    }

    /**
     * Transfer is rejected when the destination owner folder already has that zone definition.
     */
    @Test
    void transferRejectsWhenTargetExists() throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "example.com", "ns1.example.com", "admin.u/example.com.key");
        creator.create(zonesDir, "dns-team.g", "example.com", "ns1.example.com", "dns-team.g/example.com.key");

        assertThrows(ZoneAlreadyExistsException.class, () ->
                new ZoneDefinitionMutator().transfer(zonesDir, OWNER, "example.com", "dns-team.g"));
    }
}
