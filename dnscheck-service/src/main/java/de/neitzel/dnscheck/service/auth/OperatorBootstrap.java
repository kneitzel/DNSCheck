package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.config.DnsCheckConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Creates the first operator and grants the admin global right when the database contains no operators.
 */
@ApplicationScoped
public class OperatorBootstrap {

    /**
     * Records whether the first admin operator was created or bootstrap settings were incomplete.
     */
    private static final Logger LOG = Logger.getLogger(OperatorBootstrap.class);

    /**
     * Source of random bytes for a generated bootstrap password.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Persists the first operator and the admin global right.
     */
    private final OperatorAuthService operatorAuthService;

    /**
     * Supplies the optional bootstrap username and password.
     */
    private final DnsCheckConfig config;

    /**
     * Creates the bootstrap bean.
     *
     * @param operatorAuthService Persists the first operator and the admin global right.
     * @param config Supplies the optional bootstrap username and password.
     */
    public OperatorBootstrap(OperatorAuthService operatorAuthService, DnsCheckConfig config) {
        this.operatorAuthService = operatorAuthService;
        this.config = config;
    }

    /**
     * On startup, creates the first operator with the admin global right when none exist.
     * Creation runs when both bootstrap settings are present, or when both are absent and a password is generated.
     * An existing operator leaves the database unchanged.
     *
     * @param event Startup notification. The event payload is not read.
     */
    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (Operator.count() > 0) {
            return;
        }
        Optional<BootstrapCredentials> credentials = resolveCredentials(
                config.bootstrap().adminUsername(), config.bootstrap().adminPassword());
        if (credentials.isEmpty()) {
            LOG.warn(
                    "No operators in database and bootstrap credentials are incomplete — set both"
                            + " dnscheck.bootstrap.admin-username and admin-password, or neither for"
                            + " auto-generation");
            return;
        }
        BootstrapCredentials resolved = credentials.get();
        Operator admin = operatorAuthService.createOperator(resolved.username(), resolved.password());
        operatorAuthService.grantGlobalRight(admin, GlobalRightKind.ADMIN);
        if (resolved.generated()) {
            LOG.infof(
                    "Bootstrapped admin operator '%s' with generated password: %s — change after first login",
                    admin.username,
                    resolved.password());
        } else {
            LOG.infof("Bootstrapped admin operator '%s'", admin.username);
        }
    }

    /**
     * Chooses the username and password for the first operator.
     * When both values are present, they are used as configured. When both are absent, the username is
     * {@code admin} and the password is generated. When exactly one value is present, no credentials are returned.
     *
     * @param username Configured bootstrap username, or empty when unset.
     * @param password Configured bootstrap password, or empty when unset. This value is secret material.
     * @return Credentials for the first operator, or empty when the configuration is only partially set.
     */
    static Optional<BootstrapCredentials> resolveCredentials(
            Optional<String> username, Optional<String> password) {
        if (username.isPresent() && password.isPresent()) {
            return Optional.of(new BootstrapCredentials(username.get(), password.get(), false));
        }
        if (username.isEmpty() && password.isEmpty()) {
            return Optional.of(new BootstrapCredentials("admin", generatePassword(), true));
        }
        return Optional.empty();
    }

    /**
     * Creates a bootstrap password from random bytes when configuration does not supply one.
     *
     * @return The generated password. Callers treat it as secret material.
     */
    static String generatePassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Username and password used to persist the first operator.
     */
    record BootstrapCredentials(
            /**
             * Username of the bootstrapped operator.
             */
            String username,
            /**
             * Initial password. It is hashed before it is stored.
             */
            String password,
            /**
             * Whether {@code password} was generated because configuration supplied neither value.
             */
            boolean generated) {}
}
