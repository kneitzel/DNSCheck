package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.RecordSpec;
import de.neitzel.dnscheck.core.model.TtlPolicy;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.core.zone.ZoneDefinitionStore;
import de.neitzel.dnscheck.core.zone.ZoneNameNormalizer;
import de.neitzel.dnscheck.core.zoneview.ZoneView;
import de.neitzel.dnscheck.core.zoneview.ZoneViewMerger;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.UncheckedIOException;

@ApplicationScoped
public class ZoneDeletionMarkerService {

    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    LiveZoneLoader liveZoneLoader;

    private final ZoneDefinitionStore store = new ZoneDefinitionStore();
    private final ZoneNameNormalizer nameNormalizer = new ZoneNameNormalizer();
    private final ZoneViewMerger merger = new ZoneViewMerger();

    public ZoneView addDeletionMarker(
            String ownerFolder, String zoneName, String name, String type, String rdataPattern) {
        operatorRightsService.requireWrite(ownerFolder, zoneName);
        try {
            ZoneDefinition definition = loadDefinition(ownerFolder, zoneName);
            String nameFqdn = nameNormalizer.normalize(name, definition.zone());
            String normalizedType = type.toUpperCase();
            String normalizedPattern = normalizePattern(rdataPattern);
            if (definition.hasDeletionMarker(nameFqdn, normalizedType, normalizedPattern)) {
                throw conflict("Deletion marker already exists");
            }
            RecordSpec marker = new RecordSpec(
                    nameFqdn,
                    normalizedType,
                    null,
                    normalizedPattern,
                    true,
                    new TtlPolicy.Ignore());
            ZoneDefinition updated = definition.withRecord(marker);
            store.write(updated);
            return mergedView(updated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ZoneView removeDeletionMarker(
            String ownerFolder, String zoneName, String name, String type, String rdataPattern) {
        operatorRightsService.requireWrite(ownerFolder, zoneName);
        try {
            ZoneDefinition definition = loadDefinition(ownerFolder, zoneName);
            String nameFqdn = nameNormalizer.normalize(name, definition.zone());
            String normalizedType = type.toUpperCase();
            String normalizedPattern = normalizePattern(rdataPattern);
            ZoneDefinition updated = definition.withoutDeletionMarker(nameFqdn, normalizedType, normalizedPattern);
            if (updated == definition) {
                throw new NotFoundException();
            }
            store.write(updated);
            return mergedView(updated);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ZoneDefinition loadDefinition(String ownerFolder, String zoneName) throws IOException {
        return zoneCatalogSupport
                .openCatalog()
                .find(ownerFolder, zoneName)
                .orElseThrow(NotFoundException::new);
    }

    private ZoneView mergedView(ZoneDefinition definition) throws IOException {
        var liveZone = liveZoneLoader.load(definition, zoneCatalogSupport.keysDir());
        return merger.merge(definition, liveZone);
    }

    private static String normalizePattern(String rdataPattern) {
        if (rdataPattern == null || rdataPattern.isBlank()) {
            return null;
        }
        return rdataPattern;
    }

    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(message, Response.Status.CONFLICT);
    }
}
