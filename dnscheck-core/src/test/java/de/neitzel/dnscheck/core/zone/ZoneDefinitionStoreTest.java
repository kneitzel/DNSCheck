package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZoneDefinitionStoreTest {

    @Test
    void readsZoneDefinitionAndNormalizesNames(@TempDir Path tempDir) throws Exception {
        Path zoneFile = tempDir.resolve("example.com.json");
        Files.writeString(
                zoneFile,
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "example.com.key",
                  "records": [
                    {
                      "name": "@",
                      "type": "TXT",
                      "rdata": "\\"v=spf1 -all\\""
                    },
                    {
                      "name": "_bad",
                      "type": "TXT",
                      "rdataPattern": "*stale*",
                      "delete": true
                    }
                  ]
                }
                """);

        ZoneDefinition definition = new ZoneDefinitionStore().read(zoneFile);

        assertEquals("example.com", definition.zone());
        assertEquals("example.com.", definition.managedRecords().getFirst().nameFqdn());
        assertEquals("_bad.example.com.", definition.deletionMarkers().getFirst().nameFqdn());
    }

    @Test
    void rejectsFilenameMismatch(@TempDir Path tempDir) throws Exception {
        Path zoneFile = tempDir.resolve("wrong.json");
        Files.writeString(
                zoneFile,
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "example.com.key",
                  "records": []
                }
                """);

        assertThrows(IllegalArgumentException.class, () -> new ZoneDefinitionStore().read(zoneFile));
    }
}
