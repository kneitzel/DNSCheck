package de.neitzel.dnscheck.service.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how the first operator is bootstrapped from configuration.
 */
class OperatorBootstrapTest {

    /**
     * A configured username and password are used, and the password is not marked as generated.
     */
    @Test
    void usesConfiguredCredentialsWhenBothPresent() {
        Optional<OperatorBootstrap.BootstrapCredentials> credentials =
                OperatorBootstrap.resolveCredentials(Optional.of("ops"), Optional.of("secret"));

        assertTrue(credentials.isPresent());
        assertEquals("ops", credentials.get().username());
        assertEquals("secret", credentials.get().password());
        assertFalse(credentials.get().generated());
    }

    /**
     * Missing credentials produce a generated password for the admin operator.
     */
    @Test
    void generatesAdminCredentialsWhenBothAbsent() {
        Optional<OperatorBootstrap.BootstrapCredentials> credentials =
                OperatorBootstrap.resolveCredentials(Optional.empty(), Optional.empty());

        assertTrue(credentials.isPresent());
        assertEquals("admin", credentials.get().username());
        assertFalse(credentials.get().password().isBlank());
        assertTrue(credentials.get().generated());
    }

    /**
     * Two generated bootstrap passwords differ.
     */
    @Test
    void generatesUniquePasswords() {
        Optional<OperatorBootstrap.BootstrapCredentials> first =
                OperatorBootstrap.resolveCredentials(Optional.empty(), Optional.empty());
        Optional<OperatorBootstrap.BootstrapCredentials> second =
                OperatorBootstrap.resolveCredentials(Optional.empty(), Optional.empty());

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertNotEquals(first.get().password(), second.get().password());
    }

    /**
     * A username without a password, or a password without a username, does not produce bootstrap credentials.
     */
    @Test
    void rejectsPartiallyConfiguredCredentials() {
        assertTrue(OperatorBootstrap.resolveCredentials(Optional.of("ops"), Optional.empty()).isEmpty());
        assertTrue(OperatorBootstrap.resolveCredentials(Optional.empty(), Optional.of("secret")).isEmpty());
    }
}
