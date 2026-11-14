package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.zone.ZoneCatalogEntry;
import de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogResponse;
import de.neitzel.dnscheck.service.zone.ZoneCatalogService;

import java.util.List;

/**
 * Maps zone catalog entries onto the zone catalog API models.
 */
public final class ZoneCatalogApiMapper {

    /**
     * Prevents instantiation of this mapper.
     */
    private ZoneCatalogApiMapper() {}

    /**
     * Maps the zone catalog visible to the current operator, including the zones directory.
     *
     * @param service Supplies the zones directory and the zone catalog entries the operator may read.
     * @return Catalog payload for the list-zones response.
     */
    public static ZoneCatalogResponse toResponse(ZoneCatalogService service) {
        ZoneCatalogResponse response = new ZoneCatalogResponse();
        response.setZonesDir(service.zonesDir());
        response.setEntries(toEntries(service.listVisibleZones()));
        return response;
    }

    /**
     * Maps one zone catalog entry, including its owner folder, validity, nameserver, and key file.
     *
     * @param entry Supplies the catalog entry discovered under an owner folder.
     * @return API catalog entry for zone list, create, update, and transfer responses.
     */
    public static de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry toEntry(ZoneCatalogEntry entry) {
        de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry apiEntry =
                new de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry();
        apiEntry.setOwnerFolder(entry.ownerFolder());
        apiEntry.setZone(entry.zone());
        apiEntry.setFilename(entry.filename());
        apiEntry.setValid(entry.valid());
        apiEntry.setError(entry.error());
        apiEntry.setNameserver(entry.nameserver());
        apiEntry.setKeyFile(entry.keyFile());
        return apiEntry;
    }

    /**
     * Maps zone catalog entries into the list carried by the catalog payload.
     *
     * @param entries Supplies the catalog entries visible to the current operator.
     * @return API catalog entries in the same order.
     */
    private static List<de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry> toEntries(
            List<ZoneCatalogEntry> entries) {
        return entries.stream().map(ZoneCatalogApiMapper::toEntry).toList();
    }
}
