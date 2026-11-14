package de.neitzel.dnscheck.core.key;

import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests storage of TSIG key files under an owner folder.
 */
class ScopedKeyFileStoreTest {

    /**
     * BIND native key file body used as a valid TSIG key fixture.
     */
    private static final String VALID_KEY = """
            key "example.com" {
              algorithm hmac-sha256;
              secret "abc123";
            };
            """;

    /**
     * The first key written for an operator creates that owner folder and stores the key content.
     */
    @Test
    void createsOwnerFolderOnFirstKey(@TempDir Path keysRoot) throws Exception {
        ScopedKeyFileStore store = new ScopedKeyFileStore(keysRoot);
        OwnerFolder owner = OwnerFolder.parse("creator.u");

        store.create(owner, "example.com.key", VALID_KEY);

        assertTrue(Files.isDirectory(keysRoot.resolve("creator.u")));
        assertEquals(VALID_KEY.strip(), store.readContent(owner, "example.com.key").strip());
    }

    /**
     * Creating a key file that already exists in the owner folder is rejected.
     */
    @Test
    void rejectsDuplicateCreate(@TempDir Path keysRoot) throws Exception {
        ScopedKeyFileStore store = new ScopedKeyFileStore(keysRoot);
        OwnerFolder owner = OwnerFolder.parse("creator.u");
        store.create(owner, "example.com.key", VALID_KEY);

        assertThrows(KeyAlreadyExistsException.class, () -> store.create(owner, "example.com.key", VALID_KEY));
    }

    /**
     * Replacing a key file updates the stored TSIG secret.
     */
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
