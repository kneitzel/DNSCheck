package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests promotion, demotion, modification, and deletion of managed records.
 */
class ZoneRecordLifecycleTest {

    /**
     * Promotion copies the ad-hoc live record into the zone definition and does not plan a DNS change.
     */
    @Test
    void promoteCopiesLiveRecordIntoDefinition(@TempDir Path zonesDir) throws Exception {
        Path zoneFile = zonesDir.resolve("example.com.json");
        writeZone(zoneFile, "[]");
        ZoneDefinitionStore store = new ZoneDefinitionStore();
        ZoneRecordLifecycle lifecycle = new ZoneRecordLifecycle(new ZoneNameNormalizer(), store);
        ZoneDefinition definition = store.read(zoneFile);
        LiveZone liveZone = new LiveZone(
                "example.com",
                3600,
                List.of(new DnsRecord("extra.example.com.", "A", "192.0.2.99", 300)));

        ZoneRecordLifecycle.LifecycleResult result =
                lifecycle.promote(definition, liveZone, "extra", "A", "192.0.2.99", null);

        assertEquals(1, result.viewDefinition().managedRecords().size());
        assertTrue(result.dnsChanges().isEmpty());
        assertTrue(Files.readString(zoneFile).contains("extra.example.com."));
    }

    /**
     * Demotion removes the managed record from the zone definition and does not plan a DNS change.
     */
    @Test
    void demoteRemovesManagedRecordFromDefinition(@TempDir Path zonesDir) throws Exception {
        Path zoneFile = zonesDir.resolve("example.com.json");
        writeZone(
                zoneFile,
                """
                [
                  {
                    "name": "mail",
                    "type": "A",
                    "rdata": "192.0.2.10"
                  }
                ]
                """);
        ZoneDefinitionStore store = new ZoneDefinitionStore();
        ZoneRecordLifecycle lifecycle = new ZoneRecordLifecycle(new ZoneNameNormalizer(), store);
        ZoneDefinition definition = store.read(zoneFile);

        ZoneRecordLifecycle.LifecycleResult result =
                lifecycle.demote(definition, "mail", "A", "192.0.2.10");

        assertTrue(result.viewDefinition().managedRecords().isEmpty());
        assertTrue(result.dnsChanges().isEmpty());
        assertTrue(!Files.readString(zoneFile).contains("192.0.2.10"));
    }

    /**
     * Deletion removes the managed record from the zone definition and plans one DNS change.
     */
    @Test
    void deleteBuildsDeleteChangeAndRemovesManagedRecord(@TempDir Path zonesDir) throws Exception {
        Path zoneFile = zonesDir.resolve("example.com.json");
        writeZone(
                zoneFile,
                """
                [
                  {
                    "name": "mail",
                    "type": "A",
                    "rdata": "192.0.2.10"
                  }
                ]
                """);
        ZoneDefinitionStore store = new ZoneDefinitionStore();
        ZoneRecordLifecycle lifecycle = new ZoneRecordLifecycle(new ZoneNameNormalizer(), store);
        ZoneDefinition definition = store.read(zoneFile);
        LiveZone liveZone = new LiveZone(
                "example.com",
                3600,
                List.of(new DnsRecord("mail.example.com.", "A", "192.0.2.10", 300)));

        ZoneRecordLifecycle.LifecycleResult result =
                lifecycle.delete(definition, liveZone, "mail", "A", "192.0.2.10");

        assertTrue(result.viewDefinition().managedRecords().isEmpty());
        assertEquals(1, result.dnsChanges().size());
        assertTrue(!Files.readString(zoneFile).contains("192.0.2.10"));
    }

    /**
     * Modification writes the new rdata into the zone definition and plans DNS changes for the live zone.
     */
    @Test
    void modifyUpdatesManagedRecordAndBuildsRdataChange(@TempDir Path zonesDir) throws Exception {
        Path zoneFile = zonesDir.resolve("example.com.json");
        writeZone(
                zoneFile,
                """
                [
                  {
                    "name": "mail",
                    "type": "A",
                    "rdata": "192.0.2.10"
                  }
                ]
                """);
        ZoneDefinitionStore store = new ZoneDefinitionStore();
        ZoneRecordLifecycle lifecycle = new ZoneRecordLifecycle(new ZoneNameNormalizer(), store);
        ZoneDefinition definition = store.read(zoneFile);
        LiveZone liveZone = new LiveZone(
                "example.com",
                3600,
                List.of(new DnsRecord("mail.example.com.", "A", "192.0.2.10", 300)));

        ZoneRecordLifecycle.LifecycleResult result = lifecycle.modify(
                definition, liveZone, "mail", "A", "192.0.2.10", "192.0.2.11", null);

        assertEquals("192.0.2.11", result.viewDefinition().managedRecords().getFirst().rdata());
        assertEquals(2, result.dnsChanges().size());
        assertTrue(Files.readString(zoneFile).contains("192.0.2.11"));
    }

    /**
     * Promotion of a record that is already a managed record is rejected.
     */
    @Test
    void promoteRejectsAlreadyManagedRecord(@TempDir Path zonesDir) throws Exception {
        Path zoneFile = zonesDir.resolve("example.com.json");
        writeZone(
                zoneFile,
                """
                [
                  {
                    "name": "mail",
                    "type": "A",
                    "rdata": "192.0.2.10"
                  }
                ]
                """);
        ZoneDefinitionStore store = new ZoneDefinitionStore();
        ZoneRecordLifecycle lifecycle = new ZoneRecordLifecycle(new ZoneNameNormalizer(), store);
        ZoneDefinition definition = store.read(zoneFile);
        LiveZone liveZone = new LiveZone(
                "example.com",
                3600,
                List.of(new DnsRecord("mail.example.com.", "A", "192.0.2.10", 300)));

        assertThrows(
                InvalidRecordStateException.class,
                () -> lifecycle.promote(definition, liveZone, "mail", "A", "192.0.2.10", null));
    }

    /**
     * Writes a zone definition fixture whose managed records are supplied as JSON.
     *
     * @param zoneFile zone definition file to write.
     * @param recordsJson JSON array of records for the zone definition.
     */
    private static void writeZone(Path zoneFile, String recordsJson) throws Exception {
        Files.writeString(
                zoneFile,
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "example.com.key",
                  "records": %s
                }
                """.formatted(recordsJson));
    }
}
