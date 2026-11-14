package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.ZoneViewResponse;
import de.neitzel.dnscheck.service.api.generated.resources.ZoneViewResource;
import de.neitzel.dnscheck.service.zone.ZoneViewService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * HTTP API for the zone view of one zone catalog entry.
 */
@ApplicationScoped
public class ZoneViewResourceImpl implements ZoneViewResource {

    /**
     * Loads a zone view after the zone right read is checked.
     */
    @Inject
    ZoneViewService zoneViewService;

    /**
     * Handles GET /api/v1/zones/{owner}/{zoneName}/view by returning the zone view for one zone
     * catalog entry. Requires the zone right read on that entry.
     *
     * @param owner Names the owner folder that holds the zone catalog entry.
     * @param zoneName Names the zone in that owner folder.
     * @return Merged live records and managed records, each with its record status.
     */
    @Override
    public ZoneViewResponse getZoneView(String owner, String zoneName) {
        return ZoneViewApiMapper.toResponse(zoneViewService.getZoneView(owner, zoneName));
    }
}
