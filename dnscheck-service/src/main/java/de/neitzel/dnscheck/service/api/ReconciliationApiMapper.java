package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.reconcile.ReconciliationRunResult;
import de.neitzel.dnscheck.core.reconcile.ZoneReconciliationResult;
import de.neitzel.dnscheck.service.api.generated.model.HealthResponse;
import de.neitzel.dnscheck.service.api.generated.model.ReconciliationStatusResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneOutcome;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public final class ReconciliationApiMapper {

    private ReconciliationApiMapper() {}

    public static HealthResponse toHealthResponse() {
        return new HealthResponse().status(HealthResponse.StatusEnum.UP);
    }

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

    public static ReconciliationStatusResponse neverRun(String zonesDir) {
        return new ReconciliationStatusResponse()
                .status(ReconciliationStatusResponse.StatusEnum.NEVER_RUN)
                .dryRun(false)
                .zonesDir(zonesDir)
                .zoneOutcomes(List.of());
    }

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
