package de.neitzel.dnscheck.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnsCheckCoreTest {

    @Test
    void exposesVersion() {
        assertEquals("0.1.0-SNAPSHOT", DnsCheckCore.VERSION);
    }
}
