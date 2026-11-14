package de.neitzel.dnscheck.core.ownership;

/**
 * Validates a two-segment relative path under an owner folder.
 *
 * @param ownerFolder the owner folder named by the first segment.
 * @param basename the file name named by the second segment. It must not contain a separator or {@code ..}.
 */
public record TwoSegmentRelativePath(OwnerFolder ownerFolder, String basename) {

    /**
     * Requires an owner folder and a single-segment basename.
     *
     * @throws IllegalArgumentException when the owner folder is missing, the basename is blank,
     *                                  or the basename contains a separator or {@code ..}.
     */
    public TwoSegmentRelativePath {
        if (ownerFolder == null) {
            throw new IllegalArgumentException("owner folder is required");
        }
        if (basename == null || basename.isBlank()) {
            throw new IllegalArgumentException("basename is required");
        }
        if (basename.contains("/") || basename.contains("\\") || basename.contains("..")) {
            throw new IllegalArgumentException("Invalid basename: " + basename);
        }
    }

    /**
     * Splits a relative path on its single slash into an owner folder and a basename.
     *
     * @param relativePath the two-segment path, such as {@code admin.u/example.com.json}.
     * @return the parsed path.
     * @throws IllegalArgumentException when the path is blank, contains a backslash, is not exactly two segments,
     *                                  or the owner folder name is invalid.
     */
    public static TwoSegmentRelativePath parse(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relative path is required");
        }
        if (relativePath.contains("\\")) {
            throw new IllegalArgumentException("Invalid relative path: " + relativePath);
        }
        int slash = relativePath.indexOf('/');
        if (slash <= 0 || slash == relativePath.length() - 1) {
            throw new IllegalArgumentException("Invalid relative path: " + relativePath);
        }
        if (relativePath.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException("Invalid relative path: " + relativePath);
        }
        String ownerSegment = relativePath.substring(0, slash);
        String basename = relativePath.substring(slash + 1);
        return new TwoSegmentRelativePath(OwnerFolder.parse(ownerSegment), basename);
    }

    /**
     * Returns the relative path with a single slash between the owner folder and the basename.
     *
     * @return the two-segment relative path.
     */
    public String value() {
        return ownerFolder.folderName() + "/" + basename;
    }
}
