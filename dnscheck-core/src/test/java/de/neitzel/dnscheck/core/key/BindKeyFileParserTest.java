package de.neitzel.dnscheck.core.key;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BindKeyFileParserTest {

    @Test
    void parsesBindNativeKeyFile(@TempDir Path tempDir) throws Exception {
        Path keyFile = tempDir.resolve("example.com.key");
        Files.writeString(
                keyFile,
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        TsigKey key = new BindKeyFileParser().parse(keyFile);

        assertEquals("dnscheck-key", key.name());
        assertEquals("hmac-sha256", key.algorithm());
        assertEquals("c2VjcmV0", key.secret());
    }
}
