package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZoneDefinitionMutatorTest {

    private static final String OWNER = "admin.u";

    @TempDir
    Path zonesDir;

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

    @Test
    void deleteRemovesZoneDefinitionFile() throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "gone.test", "ns1.gone.test", "admin.u/gone.test.key");
        Path zoneFile = zonesDir.resolve("admin.u/gone.test.json");
        assertFalse(Files.notExists(zoneFile));

        new ZoneDefinitionMutator().delete(zonesDir, OWNER, "gone.test");

        assertFalse(Files.exists(zoneFile));
    }

    @Test
    void updateMissingZoneThrows() {
        assertThrows(ZoneDefinitionNotFoundException.class, () ->
                new ZoneDefinitionMutator().update(zonesDir, OWNER, "missing.test", "ns1.missing.test", null));
    }

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

    @Test
    void transferRejectsWhenTargetExists() throws Exception {
        ZoneDefinitionCreator creator = new ZoneDefinitionCreator();
        creator.create(zonesDir, OWNER, "example.com", "ns1.example.com", "admin.u/example.com.key");
        creator.create(zonesDir, "dns-team.g", "example.com", "ns1.example.com", "dns-team.g/example.com.key");

        assertThrows(ZoneAlreadyExistsException.class, () ->
                new ZoneDefinitionMutator().transfer(zonesDir, OWNER, "example.com", "dns-team.g"));
    }
}
