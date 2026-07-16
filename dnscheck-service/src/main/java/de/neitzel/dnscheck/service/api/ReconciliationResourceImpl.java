package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.HealthResponse;
import de.neitzel.dnscheck.service.api.generated.model.ReconciliationStatusResponse;
import de.neitzel.dnscheck.service.api.generated.resources.ReconciliationResource;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import de.neitzel.dnscheck.service.reconcile.ReconciliationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReconciliationResourceImpl implements ReconciliationResource {

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    DnsCheckConfig config;

    @Override
    public HealthResponse getHealth() {
        return ReconciliationApiMapper.toHealthResponse();
    }

    @Override
    public ReconciliationStatusResponse getReconciliationStatus() {
        return reconciliationService.lastRunStatusForOperator();
    }

    @Override
    public ReconciliationStatusResponse triggerReconciliation(Boolean dryRun) {
        boolean effectiveDryRun = dryRun != null ? dryRun : config.dryRun();
        return reconciliationService.reconcileAll(effectiveDryRun);
    }
}
