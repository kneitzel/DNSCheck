package de.neitzel.dnscheck.core.ownership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests parsing of owner folder names into owner kind and identifier.
 */
class OwnerFolderTest {

    /**
     * An operator owner folder parses as a user owner with the name before the suffix.
     */
    @Test
    void parsesUserOwnerFolder() {
        OwnerFolder folder = OwnerFolder.parse("admin.u");

        assertEquals(OwnerFolderKind.USER, folder.kind());
        assertEquals("admin", folder.identifier());
        assertEquals("admin.u", folder.folderName());
    }

    /**
     * A group owner folder parses as a group owner with the name before the suffix.
     */
    @Test
    void parsesGroupOwnerFolder() {
        OwnerFolder folder = OwnerFolder.parse("dns-team.g");

        assertEquals(OwnerFolderKind.GROUP, folder.kind());
        assertEquals("dns-team", folder.identifier());
        assertEquals("dns-team.g", folder.folderName());
    }

    /**
     * A name that is not a user or group owner folder is rejected.
     */
    @ParameterizedTest
    @ValueSource(strings = {"admin", "admin.x", "../admin.u", "admin.u.extra", "admin.G", ""})
    void rejectsInvalidOwnerFolder(String value) {
        assertThrows(IllegalArgumentException.class, () -> OwnerFolder.parse(value));
    }
}
