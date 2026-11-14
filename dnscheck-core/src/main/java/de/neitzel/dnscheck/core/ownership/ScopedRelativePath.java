package de.neitzel.dnscheck.core.ownership;

/**
 * A two-segment scoped path for key files, e.g. {@code admin.u/example.com.key}.
 *
 * @param ownerFolder the owner folder that contains the key file.
 * @param basename the key file basename, which must end in {@code .key}.
 */
public record ScopedRelativePath(OwnerFolder ownerFolder, String basename) {

    /**
     * Requires an owner folder and a key file basename that is safe to resolve under the keys directory.
     *
     * @throws IllegalArgumentException when the owner folder is missing or the basename is not a safe
     *                                  {@code .key} name.
     */
    public ScopedRelativePath {
        if (ownerFolder == null) {
            throw new IllegalArgumentException("owner folder is required");
        }
        basename = SafeNames.requireKeyBasename(basename);
    }

    /**
     * Parses a scoped key path of the form {@code {owner}/{basename}.key}.
     *
     * @param scopedPath the scoped path relative to the keys directory.
     * @return the parsed path.
     * @throws IllegalArgumentException when the path is not two segments or the basename is not a safe key file name.
     */
    public static ScopedRelativePath parse(String scopedPath) {
        TwoSegmentRelativePath segments = TwoSegmentRelativePath.parse(scopedPath);
        SafeNames.requireKeyBasename(segments.basename());
        return new ScopedRelativePath(segments.ownerFolder(), segments.basename());
    }

    /**
     * Returns the scoped path with a single slash between the owner folder and the basename.
     *
     * @return the scoped key path.
     */
    public String value() {
        return ownerFolder.folderName() + "/" + basename;
    }
}
