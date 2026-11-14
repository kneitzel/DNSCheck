package de.neitzel.dnscheck.core.ownership;

import java.nio.file.Path;

/**
 * Resolves scoped paths under configured storage roots without path traversal.
 */
public final class ScopedStoragePaths {

    /**
     * Prevents instantiation.
     */
    private ScopedStoragePaths() {}

    /**
     * Resolves {@code {owner}/{zone}.json} under the zone catalog root.
     *
     * @param root the zone catalog directory.
     * @param ownerFolder the owner folder name.
     * @param zoneName the DNS zone name.
     * @return the path of the zone definition file.
     * @throws IllegalArgumentException when the owner folder, zone name, or resolved path is not safe.
     */
    public static Path resolveZoneDefinition(Path root, String ownerFolder, String zoneName) {
        CatalogEntryIdentity identity = CatalogEntryIdentity.of(ownerFolder, zoneName);
        return resolveUnderRoot(root, identity.relativePath());
    }

    /**
     * Resolves a scoped key path under the keys directory.
     *
     * @param root the keys directory.
     * @param scopedKeyPath the scoped path {@code {owner}/{basename}.key}.
     * @return the path of the key file.
     * @throws IllegalArgumentException when the scoped path is not a safe key path or escapes the root.
     */
    public static Path resolveKeyFile(Path root, String scopedKeyPath) {
        ScopedRelativePath scoped = ScopedRelativePath.parse(scopedKeyPath);
        return resolveUnderRoot(root, scoped.value());
    }

    /**
     * Resolves a relative path and rejects absolute paths, backslashes, {@code ..}, and results that leave {@code root}.
     *
     * @param root the storage root.
     * @param relativePath the relative path to resolve.
     * @return the normalized path under {@code root}.
     * @throws IllegalArgumentException when the root or relative path is missing or the path would escape the root.
     */
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
