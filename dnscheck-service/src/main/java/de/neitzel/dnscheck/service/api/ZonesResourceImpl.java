package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.CreateRecordRequest;
import de.neitzel.dnscheck.service.api.generated.model.CreateZoneRequest;
import de.neitzel.dnscheck.service.api.generated.model.DeletionMarkerRequest;
import de.neitzel.dnscheck.service.api.generated.model.ModifyRecordRequest;
import de.neitzel.dnscheck.service.api.generated.model.PromoteRecordRequest;
import de.neitzel.dnscheck.service.api.generated.model.RecordRef;
import de.neitzel.dnscheck.service.api.generated.model.TransferZoneRequest;
import de.neitzel.dnscheck.service.api.generated.model.UpdateZoneRequest;
import de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry;
import de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogResponse;
import de.neitzel.dnscheck.service.api.generated.model.ReconciliationStatusResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneViewResponse;
import de.neitzel.dnscheck.service.api.generated.resources.ZonesResource;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import de.neitzel.dnscheck.service.reconcile.ReconciliationService;
import de.neitzel.dnscheck.service.zone.ZoneCatalogService;
import de.neitzel.dnscheck.service.zone.ZoneDeletionMarkerService;
import de.neitzel.dnscheck.service.zone.ZoneRecordService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

@ApplicationScoped
public class ZonesResourceImpl implements ZonesResource {

    @Inject
    ZoneCatalogService zoneCatalogService;

    @Inject
    ZoneRecordService zoneRecordService;

    @Inject
    ZoneDeletionMarkerService zoneDeletionMarkerService;

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    DnsCheckConfig config;

    @Override
    public ZoneCatalogResponse listZones() {
        return ZoneCatalogApiMapper.toResponse(zoneCatalogService);
    }

    @Override
    public RestResponse<ZoneCatalogEntry> createZone(CreateZoneRequest request) {
        ZoneCatalogEntry entry = ZoneCatalogApiMapper.toEntry(zoneCatalogService.createZone(
                request.getOwner(), request.getZone(), request.getNameserver(), request.getKeyFile()));
        return RestResponse.status(Response.Status.CREATED, entry);
    }

    @Override
    public ZoneCatalogEntry updateZone(String owner, String zoneName, UpdateZoneRequest request) {
        return ZoneCatalogApiMapper.toEntry(zoneCatalogService.updateZone(
                owner, zoneName, request.getNameserver(), request.getKeyFile()));
    }

    @Override
    public Response deleteZone(String owner, String zoneName) {
        zoneCatalogService.deleteZone(owner, zoneName);
        return Response.noContent().build();
    }

    @Override
    public ZoneCatalogEntry transferZone(String owner, String zoneName, TransferZoneRequest request) {
        return ZoneCatalogApiMapper.toEntry(zoneCatalogService.transferZone(
                owner, zoneName, request.getTargetOwner()));
    }

    @Override
    public ZoneViewResponse createZoneRecord(String owner, String zoneName, CreateRecordRequest request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.createRecord(
                owner,
                zoneName,
                request.getName(),
                request.getType(),
                request.getRdata(),
                request.getTtl(),
                Boolean.TRUE.equals(request.getEnforce())));
    }

    @Override
    public ZoneViewResponse deleteZoneRecord(String owner, String zoneName, RecordRef request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.deleteRecord(
                owner, zoneName, request.getName(), request.getType(), request.getRdata()));
    }

    @Override
    public ZoneViewResponse modifyZoneRecord(String owner, String zoneName, ModifyRecordRequest request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.modifyRecord(
                owner,
                zoneName,
                request.getName(),
                request.getType(),
                request.getRdata(),
                request.getNewRdata(),
                request.getNewTtl()));
    }

    @Override
    public ZoneViewResponse promoteZoneRecord(String owner, String zoneName, PromoteRecordRequest request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.promoteRecord(
                owner, zoneName, request.getName(), request.getType(), request.getRdata(), request.getTtl()));
    }

    @Override
    public ZoneViewResponse demoteZoneRecord(String owner, String zoneName, RecordRef request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.demoteRecord(
                owner, zoneName, request.getName(), request.getType(), request.getRdata()));
    }

    @Override
    public ZoneViewResponse createDeletionMarker(String owner, String zoneName, DeletionMarkerRequest request) {
        return ZoneViewApiMapper.toResponse(zoneDeletionMarkerService.addDeletionMarker(
                owner, zoneName, request.getName(), request.getType(), request.getRdataPattern()));
    }

    @Override
    public ZoneViewResponse deleteDeletionMarker(String owner, String zoneName, DeletionMarkerRequest request) {
        return ZoneViewApiMapper.toResponse(zoneDeletionMarkerService.removeDeletionMarker(
                owner, zoneName, request.getName(), request.getType(), request.getRdataPattern()));
    }

    @Override
    public ReconciliationStatusResponse triggerZoneReconciliation(
            String owner, String zoneName, Boolean dryRun) {
        boolean effectiveDryRun = dryRun != null ? dryRun : config.dryRun();
        return reconciliationService.reconcileZone(owner, zoneName, effectiveDryRun);
    }
}
