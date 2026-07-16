package de.neitzel.dnscheck.core.reconcile;

import de.neitzel.dnscheck.core.dns.LiveZoneClient;
import de.neitzel.dnscheck.core.dns.NsupdateClient;
import de.neitzel.dnscheck.core.drift.DriftDetector;
import de.neitzel.dnscheck.core.key.BindKeyFileParser;
import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.zone.ZoneCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultReconciliationEngineTest {

    @Test
    void dryRunDoesNotApplyUpdates(@TempDir Path tempDir) throws Exception {
        Path zonesDir = tempDir.resolve("zones");
        Path keysDir = tempDir.resolve("keys");
        Path ownerDir = zonesDir.resolve("admin.u");
        Files.createDirectories(ownerDir);
        Files.createDirectories(keysDir.resolve("admin.u"));

        Files.writeString(
                ownerDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": [
                    {
                      "name": "mail",
                      "type": "A",
                      "rdata": "192.0.2.10"
                    }
                  ]
                }
                """);
        Files.writeString(
                keysDir.resolve("admin.u/example.com.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        ZoneCatalog catalog = new ZoneCatalog(zonesDir);
        BindKeyFileParser keyParser = new BindKeyFileParser();
        LiveZoneClient liveZoneClient = mock(LiveZoneClient.class);
        DriftDetector driftDetector = new DriftDetector();
        NsupdateClient nsupdateClient = mock(NsupdateClient.class);

        when(liveZoneClient.load(eq("example.com"), eq("ns1.example.com"), any(TsigKey.class)))
                .thenReturn(new LiveZone("example.com", 3600, List.of()));

        DefaultReconciliationEngine engine = new DefaultReconciliationEngine(
                zonesDir, keysDir, catalog, keyParser, liveZoneClient, driftDetector, nsupdateClient);

        ZoneReconciliationResult result = engine.reconcileCatalogEntry("admin.u", "example.com", true);

        assertEquals(ZoneOutcome.DRIFT_DETECTED, result.outcome());
        assertEquals("admin.u/example.com", result.zoneName());
        assertEquals(1, result.plannedChanges().size());
        verify(nsupdateClient, never()).apply(any(), any(), any(), any());
    }
}
