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

/**
 * Builds a zone view that merges a zone definition with its live zone and each record's status.
 */
@ApplicationScoped
public class ZoneViewService {

    /**
     * Opens the zone catalog and resolves the keys directory used to load the live zone.
     */
    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    /**
     * Requires read on the zone catalog entry before the zone view is built.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Loads the live zone that is merged with the zone definition.
     */
    @Inject
    LiveZoneLoader liveZoneLoader;

    /**
     * Merges managed records, deletion markers, and live records into record status values.
     */
    private final ZoneViewMerger merger = new ZoneViewMerger();

    /**
     * Returns the zone view for one zone catalog entry.
     * The view shows which records are enforced, ad-hoc, drifted, missing, or marked for deletion.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @return Zone view for that zone catalog entry.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks read on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws WebApplicationException When the live zone cannot be loaded.
     */
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
