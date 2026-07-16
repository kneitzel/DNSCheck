package de.neitzel.dnscheck.core.ownership;

/**
 * A two-segment scoped path for key files, e.g. {@code admin.u/example.com.key}.
 */
public record ScopedRelativePath(OwnerFolder ownerFolder, String basename) {

    public ScopedRelativePath {
        if (ownerFolder == null) {
            throw new IllegalArgumentException("owner folder is required");
        }
        basename = SafeNames.requireKeyBasename(basename);
    }

    public static ScopedRelativePath parse(String scopedPath) {
        TwoSegmentRelativePath segments = TwoSegmentRelativePath.parse(scopedPath);
        SafeNames.requireKeyBasename(segments.basename());
        return new ScopedRelativePath(segments.ownerFolder(), segments.basename());
    }

    public String value() {
        return ownerFolder.folderName() + "/" + basename;
    }
}
