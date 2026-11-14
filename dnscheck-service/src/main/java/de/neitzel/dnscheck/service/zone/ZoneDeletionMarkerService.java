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

/**
 * Adds and removes deletion markers on a zone definition, then returns the updated zone view.
 */
@ApplicationScoped
public class ZoneDeletionMarkerService {

    /**
     * Opens the zone catalog and resolves the keys directory used to load the live zone.
     */
    @Inject
    ZoneCatalogSupport zoneCatalogSupport;

    /**
     * Requires write on the zone catalog entry before a deletion marker is changed.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Loads the live zone so the returned zone view can show record status after the marker change.
     */
    @Inject
    LiveZoneLoader liveZoneLoader;

    /**
     * Writes the updated zone definition back to its owner folder.
     */
    private final ZoneDefinitionStore store = new ZoneDefinitionStore();

    /**
     * Normalizes a record name to an FQDN in the zone before a deletion marker is stored.
     */
    private final ZoneNameNormalizer nameNormalizer = new ZoneNameNormalizer();

    /**
     * Merges the zone definition with the live zone into a zone view.
     */
    private final ZoneViewMerger merger = new ZoneViewMerger();

    /**
     * Adds a deletion marker to the zone definition.
     * A later reconciliation run removes live records whose name, type, and rdata pattern match.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type the deletion marker matches.
     * @param rdataPattern Rdata pattern matched against live rdata. A null or blank pattern matches every rdata value for that name and type.
     * @return Zone view after the deletion marker is stored, including record status.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws WebApplicationException When the same deletion marker is already present.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent while loading the live zone.
     * @throws UncheckedIOException When the zone definition or live zone cannot be read or written.
     */
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

    /**
     * Removes a deletion marker from the zone definition.
     * Later reconciliation runs skip that marker when removing live records.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param name Record name, as an FQDN or relative to the zone.
     * @param type DNS record type of the deletion marker.
     * @param rdataPattern Rdata pattern of the deletion marker. A null or blank pattern matches a marker that applies to every rdata value.
     * @return Zone view after the deletion marker is removed, including record status.
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks write on the zone catalog entry.
     * @throws NotFoundException When the zone catalog entry or deletion marker does not exist.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent while loading the live zone.
     * @throws UncheckedIOException When the zone definition or live zone cannot be read or written.
     */
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

    /**
     * Loads the zone definition for one zone catalog entry.
     *
     * @param ownerFolder Name of the owner folder that contains the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @return Zone definition for that entry.
     * @throws NotFoundException When the zone catalog entry does not exist.
     * @throws IOException When the zone catalog cannot be read.
     */
    private ZoneDefinition loadDefinition(String ownerFolder, String zoneName) throws IOException {
        return zoneCatalogSupport
                .openCatalog()
                .find(ownerFolder, zoneName)
                .orElseThrow(NotFoundException::new);
    }

    /**
     * Merges the zone definition with its live zone into a zone view.
     *
     * @param definition Zone definition whose managed records and deletion markers are merged.
     * @return Zone view showing record status against the live zone.
     * @throws de.neitzel.dnscheck.core.key.MissingKeyFileException When the zone definition's key file is absent.
     * @throws IOException When the live zone cannot be loaded.
     */
    private ZoneView mergedView(ZoneDefinition definition) throws IOException {
        var liveZone = liveZoneLoader.load(definition, zoneCatalogSupport.keysDir());
        return merger.merge(definition, liveZone);
    }

    /**
     * Returns the rdata pattern to store on a deletion marker.
     * A null or blank pattern matches every rdata value for the marker's name and type.
     *
     * @param rdataPattern Rdata pattern from the request, or {@code null} when the caller omits it.
     * @return The pattern to store, or {@code null} when every rdata value should match.
     */
    private static String normalizePattern(String rdataPattern) {
        if (rdataPattern == null || rdataPattern.isBlank()) {
            return null;
        }
        return rdataPattern;
    }

    /**
     * Builds a conflict response for a deletion marker that is already present.
     *
     * @param message Explanation returned to the operator.
     * @return Conflict exception the API returns to the operator.
     */
    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(message, Response.Status.CONFLICT);
    }
}
