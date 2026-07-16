package de.neitzel.dnscheck.core.ownership;

import java.nio.file.Path;

/**
 * Resolves scoped paths under configured storage roots without path traversal.
 */
public final class ScopedStoragePaths {

    private ScopedStoragePaths() {}

    public static Path resolveZoneDefinition(Path root, String ownerFolder, String zoneName) {
        CatalogEntryIdentity identity = CatalogEntryIdentity.of(ownerFolder, zoneName);
        return resolveUnderRoot(root, identity.relativePath());
    }

    public static Path resolveKeyFile(Path root, String scopedKeyPath) {
        ScopedRelativePath scoped = ScopedRelativePath.parse(scopedKeyPath);
        return resolveUnderRoot(root, scoped.value());
    }

    public static Path resolveUnderRoot(Path root, String relativePath) {
        if (root == null) {
            throw new IllegalArgumentException("storage root is required");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relative path is required");
        }
        if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.contains("\\")) {
            throw new IllegalArgumentException("Invalid relative path: " + relativePath);
        }
        Path resolved = root.resolve(relativePath).normalize();
        Path normalizedRoot = root.normalize().toAbsolutePath();
        if (!resolved.toAbsolutePath().startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Path escapes storage root: " + relativePath);
        }
        return resolved;
    }
}
