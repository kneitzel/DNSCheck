package de.neitzel.dnscheck.core.zone;

import de.neitzel.dnscheck.core.ownership.CatalogEntryIdentity;

/**
 * One zone definition file discovered during catalog introspection.
 *
 * @param ownerFolder the owner folder that contains the file.
 * @param zone the DNS zone name, or a fallback derived from the file name when the file could not be loaded.
 * @param filename the path of the file relative to the zone catalog.
 * @param valid {@code true} when the file loaded as a zone definition.
 * @param error the validation or I/O error when the file is invalid, otherwise {@code null}.
 * @param nameserver the nameserver from a valid zone definition, otherwise {@code null}.
 * @param keyFile the scoped key path from a valid zone definition, otherwise {@code null}.
 */
public record ZoneCatalogEntry(
        String ownerFolder,
        String zone,
        String filename,
        boolean valid,
        String error,
        String nameserver,
        String keyFile) {

    /**
     * Builds an entry for a zone definition that loaded.
     *
     * @param ownerFolder the owner folder that contains the file.
     * @param zone the DNS zone name from the file.
     * @param filename the path of the file relative to the zone catalog.
     * @param nameserver the nameserver the zone definition contacts.
     * @param keyFile the scoped path of the TSIG key file.
     * @return a valid catalog entry with no error.
     */
    public static ZoneCatalogEntry valid(
            String ownerFolder, String zone, String filename, String nameserver, String keyFile) {
        return new ZoneCatalogEntry(ownerFolder, zone, filename, true, null, nameserver, keyFile);
    }

    /**
     * Builds an entry for a file that failed to load.
     * Nameserver and key file are left unset.
     *
     * @param ownerFolder the owner folder that contains the file.
     * @param zone the zone name from the file, or a fallback derived from the file name.
     * @param filename the path of the file relative to the zone catalog.
     * @param error the validation or I/O error.
     * @return an invalid catalog entry.
     */
    public static ZoneCatalogEntry invalid(String ownerFolder, String zone, String filename, String error) {
        return new ZoneCatalogEntry(ownerFolder, zone, filename, false, error, null, null);
    }

    /**
     * Returns the catalog identity for this entry.
     * The owner folder and zone name must still be valid when the file failed to load.
     *
     * @return the catalog identity.
     * @throws IllegalArgumentException when the stored owner folder or zone name is not a valid identity.
     */
    public CatalogEntryIdentity identity() {
        return CatalogEntryIdentity.of(ownerFolder, zone);
    }

    /**
     * Returns the catalog reference for this entry.
     *
     * @return {@code {owner}/{zone}}.
     */
    public String catalogRef() {
        return ownerFolder + "/" + zone;
    }
}
