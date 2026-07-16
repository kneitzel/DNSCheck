package de.neitzel.dnscheck.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobMatcherTest {

    @Test
    void matchesWildcardPattern() {
        assertTrue(GlobMatcher.matches("*stalwart*", "v=spf1 include:stalwart.mail ~all"));
        assertFalse(GlobMatcher.matches("*stalwart*", "v=spf1 include:other ~all"));
    }

    @Test
    void blankPatternMatchesAll() {
        assertTrue(GlobMatcher.matches(null, "anything"));
        assertTrue(GlobMatcher.matches("", "anything"));
    }
}
