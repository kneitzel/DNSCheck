package de.neitzel.dnscheck.service.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorBootstrapTest {

    @Test
    void usesConfiguredCredentialsWhenBothPresent() {
        Optional<OperatorBootstrap.BootstrapCredentials> credentials =
                OperatorBootstrap.resolveCredentials(Optional.of("ops"), Optional.of("secret"));

        assertTrue(credentials.isPresent());
        assertEquals("ops", credentials.get().username());
        assertEquals("secret", credentials.get().password());
        assertFalse(credentials.get().generated());
    }

    @Test
    void generatesAdminCredentialsWhenBothAbsent() {
        Optional<OperatorBootstrap.BootstrapCredentials> credentials =
                OperatorBootstrap.resolveCredentials(Optional.empty(), Optional.empty());

        assertTrue(credentials.isPresent());
        assertEquals("admin", credentials.get().username());
        assertFalse(credentials.get().password().isBlank());
        assertTrue(credentials.get().generated());
    }

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

    @Test
    void rejectsPartiallyConfiguredCredentials() {
        assertTrue(OperatorBootstrap.resolveCredentials(Optional.of("ops"), Optional.empty()).isEmpty());
        assertTrue(OperatorBootstrap.resolveCredentials(Optional.empty(), Optional.of("secret")).isEmpty());
    }
}
