package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedKeyFileStoreTest {

    private static final String VALID_KEY = """
            key "example.com" {
              algorithm hmac-sha256;
              secret "abc123";
            };
            """;

    @Test
    void createsOwnerFolderOnFirstKey(@TempDir Path keysRoot) throws Exception {
        ScopedKeyFileStore store = new ScopedKeyFileStore(keysRoot);
        OwnerFolder owner = OwnerFolder.parse("creator.u");

        store.create(owner, "example.com.key", VALID_KEY);

        assertTrue(Files.isDirectory(keysRoot.resolve("creator.u")));
        assertEquals(VALID_KEY.strip(), store.readContent(owner, "example.com.key").strip());
    }

    @Test
    void rejectsDuplicateCreate(@TempDir Path keysRoot) throws Exception {
        ScopedKeyFileStore store = new ScopedKeyFileStore(keysRoot);
        OwnerFolder owner = OwnerFolder.parse("creator.u");
        store.create(owner, "example.com.key", VALID_KEY);

        assertThrows(KeyAlreadyExistsException.class, () -> store.create(owner, "example.com.key", VALID_KEY));
    }

    @Test
    void replaceUpdatesExistingKey(@TempDir Path keysRoot) throws Exception {
        ScopedKeyFileStore store = new ScopedKeyFileStore(keysRoot);
        OwnerFolder owner = OwnerFolder.parse("creator.u");
        store.create(owner, "example.com.key", VALID_KEY);

        String updated = VALID_KEY.replace("abc123", "def456");
        store.replace(owner, "example.com.key", updated);

        assertTrue(store.readContent(owner, "example.com.key").contains("def456"));
    }
}
