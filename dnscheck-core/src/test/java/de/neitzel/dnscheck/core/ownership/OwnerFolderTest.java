package de.neitzel.dnscheck.core.ownership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnerFolderTest {

    @Test
    void parsesUserOwnerFolder() {
        OwnerFolder folder = OwnerFolder.parse("admin.u");

        assertEquals(OwnerFolderKind.USER, folder.kind());
        assertEquals("admin", folder.identifier());
        assertEquals("admin.u", folder.folderName());
    }

    @Test
    void parsesGroupOwnerFolder() {
        OwnerFolder folder = OwnerFolder.parse("dns-team.g");

        assertEquals(OwnerFolderKind.GROUP, folder.kind());
        assertEquals("dns-team", folder.identifier());
        assertEquals("dns-team.g", folder.folderName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "admin.x", "../admin.u", "admin.u.extra", "admin.G", ""})
    void rejectsInvalidOwnerFolder(String value) {
        assertThrows(IllegalArgumentException.class, () -> OwnerFolder.parse(value));
    }
}
