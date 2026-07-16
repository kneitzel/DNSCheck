package de.neitzel.dnscheck.core.ownership;

/**
 * Validates a two-segment relative path under an owner folder.
 */
public record TwoSegmentRelativePath(OwnerFolder ownerFolder, String basename) {

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

    public String value() {
        return ownerFolder.folderName() + "/" + basename;
    }
}
