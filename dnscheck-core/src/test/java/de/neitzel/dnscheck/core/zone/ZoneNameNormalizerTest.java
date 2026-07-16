package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneNameNormalizerTest {

    private final ZoneNameNormalizer normalizer = new ZoneNameNormalizer();

    @Test
    void normalizesApexAtSign() {
        assertEquals("example.com.", normalizer.normalize("@", "example.com"));
    }

    @Test
    void normalizesRelativeName() {
        assertEquals("mail.example.com.", normalizer.normalize("mail", "example.com"));
    }

    @Test
    void normalizesFqdn() {
        assertEquals("mail.example.com.", normalizer.normalize("mail.example.com.", "example.com"));
    }
}
