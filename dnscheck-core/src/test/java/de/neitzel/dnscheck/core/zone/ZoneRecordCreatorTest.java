package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests building a managed record and enforcing it on a zone definition.
 */
class ZoneRecordCreatorTest {

    /**
     * A relative record name becomes an FQDN on the built managed record.
     */
    @Test
    void buildRecordSpecNormalizesRelativeName() {
        ZoneRecordCreator creator = new ZoneRecordCreator();
        RecordSpec spec = creator.buildRecordSpec("example.com", "wiki", "A", "192.0.2.50", null);
        assertEquals("wiki.example.com.", spec.nameFqdn());
        assertEquals("A", spec.type());
        assertEquals("192.0.2.50", spec.rdata());
    }

    /**
     * Enforcing a managed record adds it to the zone definition and writes the FQDN into the file.
     */
    @Test
    void enforceRecordWritesDefinition(@TempDir Path zonesDir) throws Exception {
        Path zoneFile = zonesDir.resolve("example.com.json");
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
        ZoneDefinitionStore store = new ZoneDefinitionStore();
        ZoneRecordCreator creator = new ZoneRecordCreator(new ZoneNameNormalizer(), store);
        ZoneDefinition definition = store.read(zoneFile);
        RecordSpec spec = creator.buildRecordSpec("example.com", "wiki", "A", "192.0.2.50", 300);

        ZoneDefinition updated = creator.enforceRecord(definition, spec);

        assertEquals(1, updated.records().size());
        assertTrue(Files.readString(zoneFile).contains("wiki.example.com."));
    }

    /**
     * An add change for a managed record with no TTL uses the zone default TTL.
     */
    @Test
    void buildAddChangeUsesZoneDefaultTtlWhenOmitted() {
        ZoneRecordCreator creator = new ZoneRecordCreator();
        RecordSpec spec = creator.buildRecordSpec("example.com", "wiki", "A", "192.0.2.50", null);
        PlannedChange change = creator.buildAddChange(spec, 3600, true);
        assertEquals(ChangeAction.ADD, change.action());
        assertEquals("wiki.example.com.", change.nameFqdn());
        assertEquals(3600L, change.ttl());
    }
}
