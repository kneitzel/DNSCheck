package de.neitzel.dnscheck.service.reconcile;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;
import de.neitzel.dnscheck.core.reconcile.DefaultReconciliationEngine;
import de.neitzel.dnscheck.core.reconcile.ReconciliationRunResult;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.api.ReconciliationApiMapper;
import de.neitzel.dnscheck.service.api.generated.model.ReconciliationStatusResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneOutcome;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import de.neitzel.dnscheck.service.keys.KeyUsageService;
import de.neitzel.dnscheck.service.zone.ZoneCatalogSupport;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs reconciliation for the zone catalog or one zone catalog entry and keeps the latest run status.
 */
@ApplicationScoped
public class ReconciliationService {

    /**
     * Latest reconciliation run status, including every zone outcome, before it is filtered for an operator.
     */
    private final AtomicReference<ReconciliationStatusResponse> lastRunStatus = new AtomicReference<>();

    /**
     * Service configuration for the zone catalog location, keys directory, schedule, and dry-run default.
     */
    @Inject
    DnsCheckConfig config;

    /**
     * Checks admin and zone rights before a reconciliation run and when filtering zone outcomes.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Opens the zone catalog so one zone catalog entry can be loaded before reconciliation.
     */
    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    /**
     * Checks that the zone definition's key file exists before that zone catalog entry is reconciled.
     */
    @Inject
    KeyUsageService keyUsageService;

    /**
     * Starts a scheduled reconciliation run for every zone catalog entry.
     * Uses the service dry-run default and bypasses operator rights checks.
     */
    @Scheduled(every = "{dnscheck.schedule.interval}")
    void scheduledReconcile() {
        reconcileAllInternal(config.dryRun());
    }

    /**
     * Runs reconciliation for every zone catalog entry.
     *
     * @param dryRun Whether this reconciliation run is a dry-run. A dry-run reports drift and leaves each live zone unchanged.
     * @return Status of the reconciliation run, including each zone outcome.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks the admin global right.
     */
    public ReconciliationStatusResponse reconcileAll(boolean dryRun) {
        operatorRightsService.requireAdmin();
        DefaultReconciliationEngine engine = newEngine();
        ReconciliationRunResult result = engine.reconcileAll(dryRun);
        ReconciliationStatusResponse status =
                ReconciliationApiMapper.toStatusResponse(result, dryRun, config.zones().dir());
        lastRunStatus.set(status);
        return status;
    }

    /**
     * Runs reconciliation for one zone catalog entry.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param dryRun Whether this reconciliation run is a dry-run. A dry-run reports drift and leaves the live zone unchanged.
     * @return Status of the reconciliation run containing that zone outcome.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog has no matching zone definition.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws UncheckedIOException When the zone catalog cannot be read.
     */
    public ReconciliationStatusResponse reconcileZone(String ownerFolder, String zoneName, boolean dryRun) {
        operatorRightsService.requireWrite(ownerFolder, zoneName);
        try {
            ZoneDefinition definition = zoneCatalogSupport
                    .openCatalog()
                    .find(ownerFolder, zoneName)
                    .orElseThrow(NotFoundException::new);
            keyUsageService.assertKeyPresent(definition.keyFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DefaultReconciliationEngine engine = newEngine();
        ZoneReconciliationResult result = engine.reconcileCatalogEntry(ownerFolder, zoneName, dryRun);
        ReconciliationStatusResponse status = ReconciliationApiMapper.toStatusResponse(
                new ReconciliationRunResult(List.of(result)), dryRun, config.zones().dir());
        lastRunStatus.set(status);
        return status;
    }

    /**
     * Returns the latest reconciliation run status with zone outcomes limited to entries the operator can read.
     *
     * @return Filtered status, or a status that records that no reconciliation run has completed in this process.
     */
    public ReconciliationStatusResponse lastRunStatusForOperator() {
        ReconciliationStatusResponse status = lastRunStatus.get();
        if (status == null) {
            return ReconciliationApiMapper.neverRun(config.zones().dir());
        }
        return filterZoneOutcomes(status);
    }

    /**
     * Keeps zone outcomes for zone catalog entries the current operator can read.
     *
     * @param status Full status stored for the latest reconciliation run.
     * @return Status whose zone outcomes are visible to the current operator.
     */
    private ReconciliationStatusResponse filterZoneOutcomes(ReconciliationStatusResponse status) {
        List<ZoneOutcome> zoneOutcomes = status.getZoneOutcomes();
        if (zoneOutcomes == null || zoneOutcomes.isEmpty()) {
            return status;
        }
        List<ZoneOutcome> visible = zoneOutcomes.stream()
                .filter(this::canReadOutcome)
                .toList();
        return new ReconciliationStatusResponse()
                .status(status.getStatus())
                .startedAt(status.getStartedAt())
                .dryRun(status.getDryRun())
                .zonesDir(status.getZonesDir())
                .zoneOutcomes(visible);
    }

    /**
     * Reports whether the current operator may see this zone outcome.
     * A missing, blank, {@code *}, or unscoped catalog reference is visible only with the admin global right.
     * Any other reference requires read on that zone catalog entry.
     *
     * @param outcome Zone outcome from the latest reconciliation run.
     * @return {@code true} when the operator may see that zone outcome.
     */
    private boolean canReadOutcome(ZoneOutcome outcome) {
        String catalogRef = outcome.getZone();
        if (catalogRef == null || catalogRef.isBlank() || "*".equals(catalogRef)) {
            return operatorRightsService.isAdmin();
        }
        if (!catalogRef.contains("/")) {
            return operatorRightsService.isAdmin();
        }
        CatalogEntryIdentity identity = CatalogEntryIdentity.parseCatalogRef(catalogRef);
        return operatorRightsService.canReadZone(
                identity.ownerFolder().folderName(), identity.zoneName());
    }

    /**
     * Used by scheduled reconciliation only; bypasses rights checks.
     *
     * @param dryRun Whether this reconciliation run is a dry-run. A dry-run reports drift and leaves each live zone unchanged.
     * @return Status of the reconciliation run, including each zone outcome.
     */
    ReconciliationStatusResponse reconcileAllInternal(boolean dryRun) {
        DefaultReconciliationEngine engine = newEngine();
        ReconciliationRunResult result = engine.reconcileAll(dryRun);
        ReconciliationStatusResponse status =
                ReconciliationApiMapper.toStatusResponse(result, dryRun, config.zones().dir());
        lastRunStatus.set(status);
        return status;
    }

    /**
     * Creates a reconciliation engine bound to the configured zone catalog and keys directory.
     *
     * @return Engine used for the next reconciliation run.
     */
    private DefaultReconciliationEngine newEngine() {
        return new DefaultReconciliationEngine(
                Path.of(config.zones().dir()), Path.of(config.keys().dir()));
    }

    /**
     * Returns the latest reconciliation run status visible to the current operator.
     *
     * @return Same filtered status as {@link #lastRunStatusForOperator()}.
     */
    public ReconciliationStatusResponse lastRunStatus() {
        return lastRunStatusForOperator();
    }
}
