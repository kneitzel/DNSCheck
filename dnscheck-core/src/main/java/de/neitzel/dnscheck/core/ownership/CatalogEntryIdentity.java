package de.neitzel.dnscheck.core.ownership;

/**
 * Identifies one zone definition in the catalog by owner folder and DNS zone name.
 *
 * @param ownerFolder the owner folder that contains the zone definition.
 * @param zoneName the DNS zone name. It must be a safe zone name.
 */
public record CatalogEntryIdentity(OwnerFolder ownerFolder, String zoneName) {

    /**
     * Requires an owner folder and a zone name that contains only letters, digits, dots, and hyphens.
     *
     * @throws IllegalArgumentException when the owner folder is missing or the zone name is not safe.
     */
    public CatalogEntryIdentity {
        if (ownerFolder == null) {
            throw new IllegalArgumentException("owner folder is required");
        }
        zoneName = SafeNames.requireZoneName(zoneName);
    }

    /**
     * Builds an identity by parsing the owner folder name and checking the zone name.
     *
     * @param ownerFolder the owner folder name, such as {@code admin.u}.
     * @param zoneName the DNS zone name.
     * @return the catalog identity.
     * @throws IllegalArgumentException when the owner folder name or zone name is not valid.
     */
    public static CatalogEntryIdentity of(String ownerFolder, String zoneName) {
        return new CatalogEntryIdentity(OwnerFolder.parse(ownerFolder), zoneName);
    }

    /**
     * Returns the zone definition path relative to the zone catalog, including the {@code .json} suffix.
     *
     * @return {@code {owner}/{zone}.json}.
     */
    public String relativePath() {
        return ownerFolder.folderName() + "/" + zoneName + ".json";
    }

    /**
     * Returns the catalog reference used in zone outcomes, without the file suffix.
     *
     * @return {@code {owner}/{zone}}.
     */
    public String catalogRef() {
        return ownerFolder.folderName() + "/" + zoneName;
    }

    /**
     * Parses a catalog reference {@code {owner}/{zone}}.
     * A trailing {@code .json} on the zone segment is removed so a file path can be passed as well.
     *
     * @param catalogRef the catalog reference or relative zone definition path.
     * @return the catalog identity.
     * @throws IllegalArgumentException when the value is not two segments or names an invalid owner folder or zone.
     */
    public static CatalogEntryIdentity parseCatalogRef(String catalogRef) {
        TwoSegmentRelativePath segments = TwoSegmentRelativePath.parse(catalogRef);
        String basename = segments.basename();
        if (basename.endsWith(".json")) {
            basename = basename.substring(0, basename.length() - 5);
        }
        return new CatalogEntryIdentity(segments.ownerFolder(), basename);
    }

    /**
     * Parses a zone definition path {@code {owner}/{zone}.json}.
     * The path must end with {@code .json}.
     *
     * @param relativePath the path relative to the zone catalog.
     * @return the catalog identity.
     * @throws IllegalArgumentException when the path is not two segments, does not end with {@code .json},
     *                                  or names an invalid owner folder or zone.
     */
    public static CatalogEntryIdentity parseRelativePath(String relativePath) {
        TwoSegmentRelativePath segments = TwoSegmentRelativePath.parse(relativePath);
        String filename = segments.basename();
        if (!filename.endsWith(".json")) {
            throw new IllegalArgumentException("Zone catalog path must end with .json: " + relativePath);
        }
        String zoneName = filename.substring(0, filename.length() - 5);
        return new CatalogEntryIdentity(segments.ownerFolder(), zoneName);
    }
}
