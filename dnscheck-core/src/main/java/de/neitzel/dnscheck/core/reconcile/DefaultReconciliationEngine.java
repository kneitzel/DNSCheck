package de.neitzel.dnscheck.core.reconcile;

import de.neitzel.dnscheck.core.dns.LiveZoneClient;
import de.neitzel.dnscheck.core.dns.NsupdateClient;
import de.neitzel.dnscheck.core.drift.DriftDetector;
import de.neitzel.dnscheck.core.key.BindKeyFileParser;
import de.neitzel.dnscheck.core.key.TsigKey;
import de.neitzel.dnscheck.core.model.PlannedChange;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.ownership.ScopedStoragePaths;
import de.neitzel.dnscheck.core.zone.ZoneCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DefaultReconciliationEngine implements ReconciliationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultReconciliationEngine.class);

    private final Path zonesDirectory;
    private final Path keysDirectory;
    private final ZoneCatalog zoneCatalog;
    private final BindKeyFileParser keyFileParser;
    private final LiveZoneClient liveZoneClient;
    private final DriftDetector driftDetector;
    private final NsupdateClient nsupdateClient;

    public DefaultReconciliationEngine(Path zonesDirectory, Path keysDirectory) {
        this(
                zonesDirectory,
                keysDirectory,
                new ZoneCatalog(zonesDirectory),
                new BindKeyFileParser(),
                new LiveZoneClient(),
                new DriftDetector(),
                new NsupdateClient());
    }

    DefaultReconciliationEngine(
            Path zonesDirectory,
            Path keysDirectory,
            ZoneCatalog zoneCatalog,
            BindKeyFileParser keyFileParser,
            LiveZoneClient liveZoneClient,
            DriftDetector driftDetector,
            NsupdateClient nsupdateClient) {
        this.zonesDirectory = zonesDirectory;
        this.keysDirectory = keysDirectory;
        this.zoneCatalog = zoneCatalog;
        this.keyFileParser = keyFileParser;
        this.liveZoneClient = liveZoneClient;
        this.driftDetector = driftDetector;
        this.nsupdateClient = nsupdateClient;
    }

    @Override
    public ReconciliationRunResult reconcileAll(boolean dryRun) {
        List<ZoneReconciliationResult> zoneResults = new ArrayList<>();
        try {
            for (ZoneDefinition definition : zoneCatalog.loadAll()) {
                zoneResults.add(reconcileDefinition(definition, dryRun));
            }
        } catch (IOException e) {
            log.error("Failed to load zone catalog from {}", zonesDirectory, e);
            zoneResults.add(ZoneReconciliationResult.failed("*", e.getMessage()));
        }
        return new ReconciliationRunResult(zoneResults);
    }

    @Override
    public ZoneReconciliationResult reconcileCatalogEntry(String ownerFolder, String zoneName, boolean dryRun) {
        CatalogEntryIdentity identity = CatalogEntryIdentity.of(ownerFolder, zoneName);
        String catalogRef = identity.catalogRef();
        try {
            Optional<ZoneDefinition> definition = zoneCatalog.find(identity);
            if (definition.isEmpty()) {
                return ZoneReconciliationResult.failed(catalogRef, "No zone definition found for " + catalogRef);
            }
            return reconcileDefinition(definition.get(), dryRun);
        } catch (IOException e) {
            log.error("Failed to reconcile catalog entry {}", catalogRef, e);
            return ZoneReconciliationResult.failed(catalogRef, e.getMessage());
        }
    }

    private ZoneReconciliationResult reconcileDefinition(ZoneDefinition definition, boolean dryRun)
            throws IOException {
        String catalogRef = catalogRefFor(definition);
        String zone = definition.zone();
        try {
            TsigKey tsigKey = keyFileParser.parse(resolveKeyFile(definition.keyFile()));
            var liveZone = liveZoneClient.load(zone, definition.nameserver(), tsigKey);
            List<PlannedChange> changes = driftDetector.detect(definition, liveZone);
            List<String> descriptions = changes.stream().map(PlannedChange::describe).toList();

            if (changes.isEmpty()) {
                return ZoneReconciliationResult.success(catalogRef);
            }
            if (dryRun) {
                return ZoneReconciliationResult.driftDetected(catalogRef, descriptions);
            }

            nsupdateClient.apply(zone, definition.nameserver(), tsigKey, changes);
            return ZoneReconciliationResult.reconciled(catalogRef, descriptions);
        } catch (Exception e) {
            log.error("Reconciliation failed for catalog entry {}", catalogRef, e);
            return ZoneReconciliationResult.failed(catalogRef, e.getMessage());
        }
    }

    private Path resolveKeyFile(String keyFile) {
        if (keyFile.contains("/")) {
            return ScopedStoragePaths.resolveKeyFile(keysDirectory, keyFile);
        }
        return keysDirectory.resolve(keyFile);
    }

    private static String catalogRefFor(ZoneDefinition definition) {
        Path zoneFile = definition.sourcePath();
        Path ownerDir = zoneFile.getParent();
        if (ownerDir == null) {
            return definition.zone();
        }
        String ownerFolder = ownerDir.getFileName().toString();
        return ownerFolder + "/" + definition.zone();
    }
}
