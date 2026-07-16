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

@ApplicationScoped
public class ReconciliationService {

    private final AtomicReference<ReconciliationStatusResponse> lastRunStatus = new AtomicReference<>();

    @Inject
    DnsCheckConfig config;

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    @Inject
    KeyUsageService keyUsageService;

    @Scheduled(every = "{dnscheck.schedule.interval}")
    void scheduledReconcile() {
        reconcileAllInternal(config.dryRun());
    }

    public ReconciliationStatusResponse reconcileAll(boolean dryRun) {
        operatorRightsService.requireAdmin();
        DefaultReconciliationEngine engine = newEngine();
        ReconciliationRunResult result = engine.reconcileAll(dryRun);
        ReconciliationStatusResponse status =
                ReconciliationApiMapper.toStatusResponse(result, dryRun, config.zones().dir());
        lastRunStatus.set(status);
        return status;
    }

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

    public ReconciliationStatusResponse lastRunStatusForOperator() {
        ReconciliationStatusResponse status = lastRunStatus.get();
        if (status == null) {
            return ReconciliationApiMapper.neverRun(config.zones().dir());
        }
        return filterZoneOutcomes(status);
    }

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

    /** Used by scheduled reconciliation only; bypasses rights checks. */
    ReconciliationStatusResponse reconcileAllInternal(boolean dryRun) {
        DefaultReconciliationEngine engine = newEngine();
        ReconciliationRunResult result = engine.reconcileAll(dryRun);
        ReconciliationStatusResponse status =
                ReconciliationApiMapper.toStatusResponse(result, dryRun, config.zones().dir());
        lastRunStatus.set(status);
        return status;
    }

    private DefaultReconciliationEngine newEngine() {
        return new DefaultReconciliationEngine(
                Path.of(config.zones().dir()), Path.of(config.keys().dir()));
    }

    public ReconciliationStatusResponse lastRunStatus() {
        return lastRunStatusForOperator();
    }
}
