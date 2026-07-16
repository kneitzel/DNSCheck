package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;

import java.util.Optional;

/**
 * One zone definition file discovered during catalog introspection.
 */
public record ZoneCatalogEntry(
        String ownerFolder,
        String zone,
        String filename,
        boolean valid,
        String error,
        String nameserver) {

    public static ZoneCatalogEntry valid(String ownerFolder, String zone, String filename, String nameserver) {
        return new ZoneCatalogEntry(ownerFolder, zone, filename, true, null, nameserver);
    }

    public static ZoneCatalogEntry invalid(String ownerFolder, String zone, String filename, String error) {
        return new ZoneCatalogEntry(ownerFolder, zone, filename, false, error, null);
    }

    public CatalogEntryIdentity identity() {
        return CatalogEntryIdentity.of(ownerFolder, zone);
    }

    public String catalogRef() {
        return ownerFolder + "/" + zone;
    }
}
