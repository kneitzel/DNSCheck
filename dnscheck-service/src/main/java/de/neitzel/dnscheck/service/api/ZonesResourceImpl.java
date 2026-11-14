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

/**
 * HTTP API for zone catalog entries, managed records, ad-hoc records, deletion markers, and
 * per-zone reconciliation.
 */
@ApplicationScoped
public class ZonesResourceImpl implements ZonesResource {

    /**
     * Lists, creates, updates, transfers, and deletes zone catalog entries.
     */
    @Inject
    ZoneCatalogService zoneCatalogService;

    /**
     * Creates, modifies, deletes, promotes, and demotes records in a zone.
     */
    @Inject
    ZoneRecordService zoneRecordService;

    /**
     * Adds and removes deletion markers on a zone definition.
     */
    @Inject
    ZoneDeletionMarkerService zoneDeletionMarkerService;

    /**
     * Starts a reconciliation run for one zone catalog entry.
     */
    @Inject
    ReconciliationService reconciliationService;

    /**
     * Supplies the service-level dry-run default when a zone reconciliation request omits an
     * override.
     */
    @Inject
    DnsCheckConfig config;

    /**
     * Handles GET /api/v1/zones by listing zone catalog entries the current operator may read.
     *
     * @return Visible zone catalog entries and the zones directory.
     */
    @Override
    public ZoneCatalogResponse listZones() {
        return ZoneCatalogApiMapper.toResponse(zoneCatalogService);
    }

    /**
     * Handles POST /api/v1/zones by creating a zone catalog entry. Requires the zone-create global
     * right, or the admin global right, together with ownership of the target owner folder.
     *
     * @param request Supplies the owner folder, zone name, nameserver, and key file.
     * @return HTTP 201 response containing the created zone catalog entry.
     */
    @Override
    public RestResponse<ZoneCatalogEntry> createZone(CreateZoneRequest request) {
        ZoneCatalogEntry entry = ZoneCatalogApiMapper.toEntry(zoneCatalogService.createZone(
                request.getOwner(), request.getZone(), request.getNameserver(), request.getKeyFile()));
        return RestResponse.status(Response.Status.CREATED, entry);
    }

    /**
     * Handles PATCH /api/v1/zones/{owner}/{zoneName} by updating the nameserver and key file on a
     * zone catalog entry. Requires the zone right write on that entry.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone in that owner folder.
     * @param request Supplies the replacement nameserver and key file.
     * @return Updated zone catalog entry.
     */
    @Override
    public ZoneCatalogEntry updateZone(String owner, String zoneName, UpdateZoneRequest request) {
        return ZoneCatalogApiMapper.toEntry(zoneCatalogService.updateZone(
                owner, zoneName, request.getNameserver(), request.getKeyFile()));
    }

    /**
     * Handles DELETE /api/v1/zones/{owner}/{zoneName} by removing a zone catalog entry. Requires the
     * zone right write on that entry.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone in that owner folder.
     * @return HTTP 204 response after the zone catalog entry is removed.
     */
    @Override
    public Response deleteZone(String owner, String zoneName) {
        zoneCatalogService.deleteZone(owner, zoneName);
        return Response.noContent().build();
    }

    /**
     * Handles POST /api/v1/zones/{owner}/{zoneName}/transfer by moving a zone catalog entry to
     * another owner folder. Supplemental zone grants for that entry move with it. Requires the admin
     * global right, or that the entry sits in the current operator's personal owner folder.
     *
     * @param owner Names the owner folder that currently holds the zone catalog entry.
     * @param zoneName Names the zone to transfer.
     * @param request Supplies the destination owner folder.
     * @return Zone catalog entry after the transfer.
     */
    @Override
    public ZoneCatalogEntry transferZone(String owner, String zoneName, TransferZoneRequest request) {
        return ZoneCatalogApiMapper.toEntry(zoneCatalogService.transferZone(
                owner, zoneName, request.getTargetOwner()));
    }

    /**
     * Handles POST /api/v1/zones/{owner}/{zoneName}/records by creating a DNS record. Requires the
     * zone right write on that zone catalog entry. When enforcement is enabled, the call adds a
     * managed record. When enforcement is disabled or omitted, the call creates an ad-hoc record in
     * the live zone.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone that receives the record.
     * @param request Supplies the record name, type, rdata, TTL, and whether the record is enforced.
     * @return Zone view after the record is created.
     */
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

    /**
     * Handles DELETE /api/v1/zones/{owner}/{zoneName}/records by deleting a record from the live
     * zone. Requires the zone right write on that zone catalog entry. A managed record is also
     * removed from the zone definition.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone that contains the record.
     * @param request Identifies the record name, type, and rdata to delete.
     * @return Zone view after the record is deleted.
     */
    @Override
    public ZoneViewResponse deleteZoneRecord(String owner, String zoneName, RecordRef request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.deleteRecord(
                owner, zoneName, request.getName(), request.getType(), request.getRdata()));
    }

    /**
     * Handles PATCH /api/v1/zones/{owner}/{zoneName}/records by changing rdata and TTL on a managed
     * record or an ad-hoc record in the live zone. Requires the zone right write on that zone
     * catalog entry.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone that contains the record.
     * @param request Identifies the record and supplies the replacement rdata and TTL.
     * @return Zone view after the record is modified.
     */
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

    /**
     * Handles POST /api/v1/zones/{owner}/{zoneName}/records/promote by promoting an ad-hoc record
     * into the zone definition so reconciliation treats it as a managed record. Requires the zone
     * right write on that zone catalog entry.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone that contains the ad-hoc record.
     * @param request Identifies the ad-hoc record and supplies the TTL stored on the managed record.
     * @return Zone view after promotion.
     */
    @Override
    public ZoneViewResponse promoteZoneRecord(String owner, String zoneName, PromoteRecordRequest request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.promoteRecord(
                owner, zoneName, request.getName(), request.getType(), request.getRdata(), request.getTtl()));
    }

    /**
     * Handles POST /api/v1/zones/{owner}/{zoneName}/records/demote by removing a managed record from
     * the zone definition. Requires the zone right write on that zone catalog entry. The record
     * remains in the live zone as an ad-hoc record.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone that contains the managed record.
     * @param request Identifies the managed record to demote.
     * @return Zone view after demotion.
     */
    @Override
    public ZoneViewResponse demoteZoneRecord(String owner, String zoneName, RecordRef request) {
        return ZoneViewApiMapper.toResponse(zoneRecordService.demoteRecord(
                owner, zoneName, request.getName(), request.getType(), request.getRdata()));
    }

    /**
     * Handles POST /api/v1/zones/{owner}/{zoneName}/deletion-markers by adding a deletion marker to
     * the zone definition. Requires the zone right write on that zone catalog entry. A later
     * reconciliation run removes live records that match the marker.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone that receives the deletion marker.
     * @param request Supplies the record name, type, and optional rdata pattern.
     * @return Zone view after the deletion marker is added.
     */
    @Override
    public ZoneViewResponse createDeletionMarker(String owner, String zoneName, DeletionMarkerRequest request) {
        return ZoneViewApiMapper.toResponse(zoneDeletionMarkerService.addDeletionMarker(
                owner, zoneName, request.getName(), request.getType(), request.getRdataPattern()));
    }

    /**
     * Handles DELETE /api/v1/zones/{owner}/{zoneName}/deletion-markers by removing a deletion marker
     * from the zone definition. Requires the zone right write on that zone catalog entry.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone whose deletion marker is removed.
     * @param request Identifies the deletion marker by name, type, and optional rdata pattern.
     * @return Zone view after the deletion marker is removed.
     */
    @Override
    public ZoneViewResponse deleteDeletionMarker(String owner, String zoneName, DeletionMarkerRequest request) {
        return ZoneViewApiMapper.toResponse(zoneDeletionMarkerService.removeDeletionMarker(
                owner, zoneName, request.getName(), request.getType(), request.getRdataPattern()));
    }

    /**
     * Handles POST /api/v1/zones/{owner}/{zoneName}/reconcile by starting a reconciliation run for
     * one zone catalog entry. Requires the zone right write on that entry. An omitted dry-run flag
     * uses the service default. A dry-run reports drift and leaves live DNS unchanged.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone to reconcile.
     * @param dryRun Overrides the service dry-run default for this reconciliation run when present.
     * @return Status of the reconciliation run for that zone.
     */
    @Override
    public ReconciliationStatusResponse triggerZoneReconciliation(
            String owner, String zoneName, Boolean dryRun) {
        boolean effectiveDryRun = dryRun != null ? dryRun : config.dryRun();
        return reconciliationService.reconcileZone(owner, zoneName, effectiveDryRun);
    }
}
