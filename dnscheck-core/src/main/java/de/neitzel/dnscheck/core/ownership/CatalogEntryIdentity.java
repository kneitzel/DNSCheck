package de.neitzel.dnscheck.core.ownership;

/**
 * Identifies one zone definition in the catalog by owner folder and DNS zone name.
 */
public record CatalogEntryIdentity(OwnerFolder ownerFolder, String zoneName) {

    public CatalogEntryIdentity {
        if (ownerFolder == null) {
            throw new IllegalArgumentException("owner folder is required");
        }
        zoneName = SafeNames.requireZoneName(zoneName);
    }

    public static CatalogEntryIdentity of(String ownerFolder, String zoneName) {
        return new CatalogEntryIdentity(OwnerFolder.parse(ownerFolder), zoneName);
    }

    public String relativePath() {
        return ownerFolder.folderName() + "/" + zoneName + ".json";
    }

    public String catalogRef() {
        return ownerFolder.folderName() + "/" + zoneName;
    }

    public static CatalogEntryIdentity parseCatalogRef(String catalogRef) {
        TwoSegmentRelativePath segments = TwoSegmentRelativePath.parse(catalogRef);
        String basename = segments.basename();
        if (basename.endsWith(".json")) {
            basename = basename.substring(0, basename.length() - 5);
        }
        return new CatalogEntryIdentity(segments.ownerFolder(), basename);
    }

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
