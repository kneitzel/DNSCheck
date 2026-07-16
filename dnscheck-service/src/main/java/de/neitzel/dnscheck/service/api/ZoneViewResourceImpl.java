package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.ZoneViewResponse;
import de.neitzel.dnscheck.service.api.generated.resources.ZoneViewResource;
import de.neitzel.dnscheck.service.zone.ZoneViewService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ZoneViewResourceImpl implements ZoneViewResource {

    @Inject
    ZoneViewService zoneViewService;

    @Override
    public ZoneViewResponse getZoneView(String owner, String zoneName) {
        return ZoneViewApiMapper.toResponse(zoneViewService.getZoneView(owner, zoneName));
    }
}
