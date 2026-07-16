package de.neitzel.dnscheck.core.ownership;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogEntryIdentityTest {

    @Test
    void buildsFromOwnerFolderAndZoneName() {
        CatalogEntryIdentity identity = CatalogEntryIdentity.of("admin.u", "example.com");

        assertEquals("admin.u", identity.ownerFolder().folderName());
        assertEquals("example.com", identity.zoneName());
        assertEquals("admin.u/example.com.json", identity.relativePath());
    }

    @Test
    void parsesRelativeCatalogPath() {
        CatalogEntryIdentity identity = CatalogEntryIdentity.parseRelativePath("dns-team.g/staging.test.json");

        assertEquals("dns-team.g", identity.ownerFolder().folderName());
        assertEquals("staging.test", identity.zoneName());
    }

    @Test
    void parsesCatalogRefWithoutJsonSuffix() {
        CatalogEntryIdentity identity = CatalogEntryIdentity.parseCatalogRef("admin.u/example.com");

        assertEquals("admin.u", identity.ownerFolder().folderName());
        assertEquals("example.com", identity.zoneName());
        assertEquals("admin.u/example.com", identity.catalogRef());
    }

    @Test
    void rejectsMismatchedJsonSuffix() {
        assertThrows(IllegalArgumentException.class, () -> CatalogEntryIdentity.parseRelativePath("admin.u/example.com.key"));
    }

    @Test
    void rejectsPathWithExtraSegments() {
        assertThrows(IllegalArgumentException.class, () -> CatalogEntryIdentity.parseRelativePath("admin.u/nested/example.com.json"));
    }
}
