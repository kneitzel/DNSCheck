package de.neitzel.dnscheck.core.ownership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopedStoragePathsTest {

    @Test
    void resolvesZoneDefinitionPath(@TempDir Path root) {
        Path resolved = ScopedStoragePaths.resolveZoneDefinition(root, "admin.u", "example.com");

        assertEquals(root.resolve("admin.u").resolve("example.com.json").normalize(), resolved.normalize());
    }

    @Test
    void resolvesScopedKeyPath(@TempDir Path root) {
        Path resolved = ScopedStoragePaths.resolveKeyFile(root, "admin.u/example.com.key");

        assertEquals(root.resolve("admin.u").resolve("example.com.key").normalize(), resolved.normalize());
    }

    @Test
    void rejectsKeyPathWithoutOwnerFolder(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class, () -> ScopedStoragePaths.resolveKeyFile(root, "example.com.key"));
    }

    @Test
    void rejectsTraversalInScopedKeyPath(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class, () -> ScopedStoragePaths.resolveKeyFile(root, "../admin.u/example.com.key"));
    }

    @Test
    void rejectsTraversalViaZoneName(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class, () -> ScopedStoragePaths.resolveZoneDefinition(root, "admin.u", "../other.com"));
    }
}
