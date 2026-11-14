package de.neitzel.dnscheck.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the published version of the DNSCheck core library.
 */
class DnsCheckCoreTest {

    /**
     * The core library exposes its snapshot version string.
     */
    @Test
    void exposesVersion() {
        assertEquals("0.1.0-SNAPSHOT", DnsCheckCore.VERSION);
    }
}
