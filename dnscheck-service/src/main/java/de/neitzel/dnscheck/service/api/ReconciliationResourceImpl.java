package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.HealthResponse;
import de.neitzel.dnscheck.service.api.generated.model.ReconciliationStatusResponse;
import de.neitzel.dnscheck.service.api.generated.resources.ReconciliationResource;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import de.neitzel.dnscheck.service.reconcile.ReconciliationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * HTTP API for service health and reconciliation runs.
 */
@ApplicationScoped
public class ReconciliationResourceImpl implements ReconciliationResource {

    /**
     * Starts a reconciliation run and reads the last run visible to the current operator.
     */
    @Inject
    ReconciliationService reconciliationService;

    /**
     * Supplies the service-level dry-run default when a reconciliation request omits an override.
     */
    @Inject
    DnsCheckConfig config;

    /**
     * Handles GET /api/v1/health by reporting that the service is up.
     *
     * @return Health payload whose status is up.
     */
    @Override
    public HealthResponse getHealth() {
        return ReconciliationApiMapper.toHealthResponse();
    }

    /**
     * Handles GET /api/v1/status by returning the last reconciliation run. Zone outcomes are limited
     * to those the current operator may read. When no reconciliation run has been
     * stored yet, the status is never-run.
     *
     * @return Last reconciliation run status visible to the current operator.
     */
    @Override
    public ReconciliationStatusResponse getReconciliationStatus() {
        return reconciliationService.lastRunStatusForOperator();
    }

    /**
     * Handles POST /api/v1/reconcile by starting a reconciliation run for every zone catalog entry.
     * Requires the admin global right. An omitted dry-run flag uses the service default. A dry-run
     * reports drift and leaves live DNS unchanged.
     *
     * @param dryRun Overrides the service dry-run default for this reconciliation run when present.
     * @return Status of the reconciliation run, including each zone outcome.
     */
    @Override
    public ReconciliationStatusResponse triggerReconciliation(Boolean dryRun) {
        boolean effectiveDryRun = dryRun != null ? dryRun : config.dryRun();
        return reconciliationService.reconcileAll(effectiveDryRun);
    }
}
