package de.neitzel.dnscheck.core.ownership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests resolution of zone definition and key file paths under an owner folder.
 */
class ScopedStoragePathsTest {

    /**
     * A zone name resolves to the JSON zone definition inside the owner folder.
     */
    @Test
    void resolvesZoneDefinitionPath(@TempDir Path root) {
        Path resolved = ScopedStoragePaths.resolveZoneDefinition(root, "admin.u", "example.com");

        assertEquals(root.resolve("admin.u").resolve("example.com.json").normalize(), resolved.normalize());
    }

    /**
     * A scoped key path resolves to the key file inside the owner folder.
     */
    @Test
    void resolvesScopedKeyPath(@TempDir Path root) {
        Path resolved = ScopedStoragePaths.resolveKeyFile(root, "admin.u/example.com.key");

        assertEquals(root.resolve("admin.u").resolve("example.com.key").normalize(), resolved.normalize());
    }

    /**
     * A key path that omits the owner folder is rejected.
     */
    @Test
    void rejectsKeyPathWithoutOwnerFolder(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class, () -> ScopedStoragePaths.resolveKeyFile(root, "example.com.key"));
    }

    /**
     * A scoped key path that climbs out of the keys directory is rejected.
     */
    @Test
    void rejectsTraversalInScopedKeyPath(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class, () -> ScopedStoragePaths.resolveKeyFile(root, "../admin.u/example.com.key"));
    }

    /**
     * A zone name that climbs out of the owner folder is rejected.
     */
    @Test
    void rejectsTraversalViaZoneName(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class, () -> ScopedStoragePaths.resolveZoneDefinition(root, "admin.u", "../other.com"));
    }
}
