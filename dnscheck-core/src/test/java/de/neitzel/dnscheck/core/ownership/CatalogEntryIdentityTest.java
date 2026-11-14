package de.neitzel.dnscheck.core.ownership;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests identity of a zone catalog entry from its owner folder and zone name.
 */
class CatalogEntryIdentityTest {

    /**
     * An owner folder and zone name form a catalog entry whose relative path is the zone definition file.
     */
    @Test
    void buildsFromOwnerFolderAndZoneName() {
        CatalogEntryIdentity identity = CatalogEntryIdentity.of("admin.u", "example.com");

        assertEquals("admin.u", identity.ownerFolder().folderName());
        assertEquals("example.com", identity.zoneName());
        assertEquals("admin.u/example.com.json", identity.relativePath());
    }

    /**
     * A relative path of owner folder and zone definition file yields that catalog entry.
     */
    @Test
    void parsesRelativeCatalogPath() {
        CatalogEntryIdentity identity = CatalogEntryIdentity.parseRelativePath("dns-team.g/staging.test.json");

        assertEquals("dns-team.g", identity.ownerFolder().folderName());
        assertEquals("staging.test", identity.zoneName());
    }

    /**
     * A catalog reference without the JSON suffix still identifies the owner folder and zone name.
     */
    @Test
    void parsesCatalogRefWithoutJsonSuffix() {
        CatalogEntryIdentity identity = CatalogEntryIdentity.parseCatalogRef("admin.u/example.com");

        assertEquals("admin.u", identity.ownerFolder().folderName());
        assertEquals("example.com", identity.zoneName());
        assertEquals("admin.u/example.com", identity.catalogRef());
    }

    /**
     * A relative path whose file is not a zone definition is rejected.
     */
    @Test
    void rejectsMismatchedJsonSuffix() {
        assertThrows(IllegalArgumentException.class, () -> CatalogEntryIdentity.parseRelativePath("admin.u/example.com.key"));
    }

    /**
     * A relative path with more than the owner folder and the zone file is rejected.
     */
    @Test
    void rejectsPathWithExtraSegments() {
        assertThrows(IllegalArgumentException.class, () -> CatalogEntryIdentity.parseRelativePath("admin.u/nested/example.com.json"));
    }
}
