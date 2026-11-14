package de.neitzel.dnscheck.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests rdata pattern matching used by deletion markers.
 */
class GlobMatcherTest {

    /**
     * A glob rdata pattern matches rdata that contains the wildcard text and rejects rdata that does not.
     */
    @Test
    void matchesWildcardPattern() {
        assertTrue(GlobMatcher.matches("*stalwart*", "v=spf1 include:stalwart.mail ~all"));
        assertFalse(GlobMatcher.matches("*stalwart*", "v=spf1 include:other ~all"));
    }

    /**
     * A null or blank rdata pattern matches every rdata value.
     */
    @Test
    void blankPatternMatchesAll() {
        assertTrue(GlobMatcher.matches(null, "anything"));
        assertTrue(GlobMatcher.matches("", "anything"));
    }
}
