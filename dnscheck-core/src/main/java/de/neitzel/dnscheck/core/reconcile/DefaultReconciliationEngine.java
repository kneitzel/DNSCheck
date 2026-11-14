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

/**
 * Reconciles the zone catalog by loading each zone definition, transferring the live zone,
 * detecting drift, and applying nsupdate unless the run is a dry-run.
 */
public final class DefaultReconciliationEngine implements ReconciliationEngine {

    /**
     * Records catalog load failures and per-zone reconciliation errors.
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultReconciliationEngine.class);

    /**
     * Zone catalog root scanned for owner folders and zone definitions.
     */
    private final Path zonesDirectory;

    /**
     * Keys directory used to resolve the TSIG key file named by each zone definition.
     */
    private final Path keysDirectory;

    /**
     * Loads zone definitions for a full run and for a single catalog entry.
     */
    private final ZoneCatalog zoneCatalog;

    /**
     * Parses the TSIG key file before AXFR and nsupdate.
     */
    private final BindKeyFileParser keyFileParser;

    /**
     * Loads the live zone by AXFR.
     */
    private final LiveZoneClient liveZoneClient;

    /**
     * Plans the changes that would remove drift.
     */
    private final DriftDetector driftDetector;

    /**
     * Applies planned changes when the run is not a dry-run.
     */
    private final NsupdateClient nsupdateClient;

    /**
     * Creates an engine that reads zone definitions and key files from the given directories
     * and uses the default catalog, parser, and DNS clients.
     *
     * @param zonesDirectory the zone catalog root.
     * @param keysDirectory the keys directory.
     */
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

    /**
     * Creates an engine that uses the supplied catalog, parser, and DNS clients.
     *
     * @param zonesDirectory the zone catalog root, kept for failure logs.
     * @param keysDirectory the keys directory used to resolve TSIG key files.
     * @param zoneCatalog the catalog that loads zone definitions.
     * @param keyFileParser the parser for TSIG key files.
     * @param liveZoneClient the client that loads a live zone.
     * @param driftDetector the detector that plans drift corrections.
     * @param nsupdateClient the client that applies planned changes.
     */
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

    /**
     * Reconciles every valid zone definition.
     * A catalog load failure becomes one failed zone outcome and stops the run.
     * A failure inside one zone is recorded on that zone, and the other zones still run.
     *
     * @param dryRun {@code true} to report drift without sending nsupdate commands.
     * @return the zone outcomes for this run.
     */
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

    /**
     * Reconciles one zone catalog entry.
     * A missing definition or an I/O failure becomes a failed zone outcome.
     *
     * @param ownerFolder the owner folder that contains the zone definition.
     * @param zoneName the DNS zone name.
     * @param dryRun {@code true} to report drift without sending nsupdate commands.
     * @return the zone outcome for that catalog entry.
     */
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

    /**
     * Loads the TSIG key, transfers the live zone, and either applies drift or reports it.
     * Any failure is returned as a failed zone outcome rather than thrown to the caller.
     *
     * @param definition the zone definition to reconcile.
     * @param dryRun {@code true} to report drift without sending nsupdate commands.
     * @return the zone outcome for this zone definition.
     * @throws IOException declared for callers. Failures from this method are caught and returned
     *                     as a failed zone outcome.
     */
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

    /**
     * Resolves the zone definition's key file.
     * A path containing {@code /} is scoped under the keys directory; otherwise it is a file name in that directory.
     *
     * @param keyFile the key path stored in the zone definition.
     * @return the key file path.
     * @throws IllegalArgumentException when a scoped path is unsafe.
     */
    private Path resolveKeyFile(String keyFile) {
        if (keyFile.contains("/")) {
            return ScopedStoragePaths.resolveKeyFile(keysDirectory, keyFile);
        }
        return keysDirectory.resolve(keyFile);
    }

    /**
     * Builds the catalog reference from the zone definition file's owner folder and zone name.
     * When the file has no parent directory, the zone name is used alone.
     *
     * @param definition the zone definition being reconciled.
     * @return the catalog reference shown on the zone outcome.
     */
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
