package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.zoneview.ZoneView;
import de.neitzel.dnscheck.core.zoneview.ZoneViewMerger;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ZoneViewService {

    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    LiveZoneLoader liveZoneLoader;

    private final ZoneViewMerger merger = new ZoneViewMerger();

    public ZoneView getZoneView(String ownerFolder, String zoneName) {
        operatorRightsService.requireRead(ownerFolder, zoneName);
        try {
            Optional<ZoneDefinition> definition =
                    zoneCatalogSupport.openCatalog().find(ownerFolder, zoneName);
            if (definition.isEmpty()) {
                throw new NotFoundException();
            }
            var liveZone = liveZoneLoader.load(definition.get(), zoneCatalogSupport.keysDir());
            return merger.merge(definition.get(), liveZone);
        } catch (IOException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", e.getMessage());
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_GATEWAY).entity(body).build());
        }
    }
}
