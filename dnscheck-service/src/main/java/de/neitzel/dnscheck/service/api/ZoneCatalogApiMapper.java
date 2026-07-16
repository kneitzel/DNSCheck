package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.zone.ZoneCatalogEntry;
import de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogResponse;
import de.neitzel.dnscheck.service.zone.ZoneCatalogService;

import java.util.List;

public final class ZoneCatalogApiMapper {

    private ZoneCatalogApiMapper() {}

    public static ZoneCatalogResponse toResponse(ZoneCatalogService service) {
        ZoneCatalogResponse response = new ZoneCatalogResponse();
        response.setZonesDir(service.zonesDir());
        response.setEntries(toEntries(service.listVisibleZones()));
        return response;
    }

    public static de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry toEntry(ZoneCatalogEntry entry) {
        de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry apiEntry =
                new de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry();
        apiEntry.setOwnerFolder(entry.ownerFolder());
        apiEntry.setZone(entry.zone());
        apiEntry.setFilename(entry.filename());
        apiEntry.setValid(entry.valid());
        apiEntry.setError(entry.error());
        apiEntry.setNameserver(entry.nameserver());
        return apiEntry;
    }

    private static List<de.neitzel.dnscheck.service.api.generated.model.ZoneCatalogEntry> toEntries(
            List<ZoneCatalogEntry> entries) {
        return entries.stream().map(ZoneCatalogApiMapper::toEntry).toList();
    }
}
