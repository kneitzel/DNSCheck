package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;

/**
 * One zone definition file discovered during catalog introspection.
 */
public record ZoneCatalogEntry(
        String ownerFolder,
        String zone,
        String filename,
        boolean valid,
        String error,
        String nameserver,
        String keyFile) {

    public static ZoneCatalogEntry valid(
            String ownerFolder, String zone, String filename, String nameserver, String keyFile) {
        return new ZoneCatalogEntry(ownerFolder, zone, filename, true, null, nameserver, keyFile);
    }

    public static ZoneCatalogEntry invalid(String ownerFolder, String zone, String filename, String error) {
        return new ZoneCatalogEntry(ownerFolder, zone, filename, false, error, null, null);
    }

    public CatalogEntryIdentity identity() {
        return CatalogEntryIdentity.of(ownerFolder, zone);
    }

    public String catalogRef() {
        return ownerFolder + "/" + zone;
    }
}
