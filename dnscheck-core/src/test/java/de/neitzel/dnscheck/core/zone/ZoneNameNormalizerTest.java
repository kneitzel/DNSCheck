package de.neitzel.dnscheck.core.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests normalization of managed record names to FQDN.
 */
class ZoneNameNormalizerTest {

    /**
     * Normalizer shared by the name scenarios.
     */
    private final ZoneNameNormalizer normalizer = new ZoneNameNormalizer();

    /**
     * The apex marker normalizes to the zone's FQDN.
     */
    @Test
    void normalizesApexAtSign() {
        assertEquals("example.com.", normalizer.normalize("@", "example.com"));
    }

    /**
     * A name relative to the zone normalizes to an FQDN under that zone.
     */
    @Test
    void normalizesRelativeName() {
        assertEquals("mail.example.com.", normalizer.normalize("mail", "example.com"));
    }

    /**
     * An FQDN is preserved when the name is already absolute.
     */
    @Test
    void normalizesFqdn() {
        assertEquals("mail.example.com.", normalizer.normalize("mail.example.com.", "example.com"));
    }
}
