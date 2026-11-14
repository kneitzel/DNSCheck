package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.reconcile.ReconciliationRunResult;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.service.api.generated.model.HealthResponse;
import de.neitzel.dnscheck.service.api.generated.model.ReconciliationStatusResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneOutcome;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Maps reconciliation runs and the health check onto API status models.
 */
public final class ReconciliationApiMapper {

    /**
     * Prevents instantiation of this mapper.
     */
    private ReconciliationApiMapper() {}

    /**
     * Reports the service as up for the health endpoint.
     *
     * @return Health payload whose status is up.
     */
    public static HealthResponse toHealthResponse() {
        return new HealthResponse().status(HealthResponse.StatusEnum.UP);
    }

    /**
     * Maps one reconciliation run onto the status payload, including each zone outcome and whether
     * the run was a dry-run. The run status is failed when any zone outcome failed, and completed
     * when none failed.
     *
     * @param result Supplies the zone outcomes produced by the reconciliation run.
     * @param dryRun Reports whether the run detected drift while leaving live DNS unchanged.
     * @param zonesDir Supplies the configured location of the zone catalog.
     * @return Status payload for that reconciliation run.
     */
    public static ReconciliationStatusResponse toStatusResponse(
            ReconciliationRunResult result, boolean dryRun, String zonesDir) {
        List<ZoneOutcome> zoneOutcomes =
                result.zoneResults().stream().map(ReconciliationApiMapper::toZoneOutcome).toList();

        ReconciliationStatusResponse.StatusEnum status = zoneOutcomes.stream()
                        .anyMatch(outcome -> outcome.getOutcome() == ZoneOutcome.OutcomeEnum.FAILED)
                ? ReconciliationStatusResponse.StatusEnum.FAILED
                : ReconciliationStatusResponse.StatusEnum.COMPLETED;

        return new ReconciliationStatusResponse()
                .status(status)
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .dryRun(dryRun)
                .zonesDir(zonesDir)
                .zoneOutcomes(zoneOutcomes);
    }

    /**
     * Builds the status payload used before any reconciliation run has been stored.
     *
     * @param zonesDir Supplies the configured location of the zone catalog.
     * @return Status payload whose status is never-run and whose zone outcome list is empty.
     */
    public static ReconciliationStatusResponse neverRun(String zonesDir) {
        return new ReconciliationStatusResponse()
                .status(ReconciliationStatusResponse.StatusEnum.NEVER_RUN)
                .dryRun(false)
                .zonesDir(zonesDir)
                .zoneOutcomes(List.of());
    }

    /**
     * Maps one zone result within a reconciliation run, including planned changes and any error.
     *
     * @param result Supplies the outcome for a single zone.
     * @return API zone outcome carried on the reconciliation status.
     */
    private static ZoneOutcome toZoneOutcome(ZoneReconciliationResult result) {
        ZoneOutcome zoneOutcome = new ZoneOutcome()
                .zone(result.zoneName())
                .outcome(ZoneOutcome.OutcomeEnum.fromString(result.outcome().name()))
                .plannedChanges(result.plannedChanges());
        if (result.errorMessage() != null) {
            zoneOutcome.error(result.errorMessage());
        }
        return zoneOutcome;
    }
}
