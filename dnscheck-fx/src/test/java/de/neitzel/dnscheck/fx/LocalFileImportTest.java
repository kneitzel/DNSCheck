package de.neitzel.dnscheck.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileImportTest {
    private static final String KEY = """
            key "shared" { algorithm hmac-sha256; secret "dGVzdA=="; };
            """;
    @TempDir Path folder;
    private final LocalFileImport imports = new LocalFileImport();

    @Test void addsAKeyAndCreatesTheFirstZone() throws Exception {
        assertTrue(new ZoneFiles().load(folder).isEmpty());
        assertEquals("shared.key", imports.keyName(KEY));
        imports.pastedKey(folder, "shared.key", KEY).install(false);
        assertEquals(java.util.List.of("shared.key"), imports.keyFiles(folder));
        imports.createZone(folder, "example.com", "ns.example.com", "shared.key");
        assertEquals("example.com", new ZoneFiles().load(folder).getFirst().getZone());
        assertThrows(IOException.class, () -> imports.createZone(folder, "example.com", "ns2.example.com", "shared.key"));
        assertTrue(Files.readString(folder.resolve("example.com.domain")).contains("ns.example.com"));
    }

    @Test void importsAZoneBeforeItsKeyAndRequiresExplicitReplacement() throws Exception {
        Path source = Files.createDirectory(folder.resolve("source")).resolve("example.com.domain");
        Files.writeString(source, """
                {"zone":"example.com","nameserver":"ns1.example.com","keyFile":"shared.key","records":[]}
                """);
        var staged = imports.droppedFile(folder, source);
        staged.install(false);
        assertFalse(new ZoneFiles().load(folder).getFirst().isValid());
        imports.pastedKey(folder, "shared.key", KEY).install(false);
        assertTrue(new ZoneFiles().load(folder).getFirst().isValid());

        Files.writeString(source, Files.readString(source).replace("ns1", "ns2"));
        var replacement = imports.droppedFile(folder, source);
        assertTrue(replacement.exists());
        assertThrows(IOException.class, () -> replacement.install(false));
        replacement.install(true);
        assertEquals("ns2.example.com", new ZoneFiles().load(folder).getFirst().getNameserver());
    }

    @Test void rejectsMalformedKeysAndFilenamesOutsideTheFolder() {
        assertThrows(IOException.class, () -> imports.pastedKey(folder, "bad.key", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> imports.pastedKey(folder, "../shared.key", KEY));
    }
}
