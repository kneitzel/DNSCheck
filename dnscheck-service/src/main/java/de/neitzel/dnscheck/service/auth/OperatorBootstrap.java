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

@ApplicationScoped
public class OperatorBootstrap {

    private static final Logger LOG = Logger.getLogger(OperatorBootstrap.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OperatorAuthService operatorAuthService;
    private final DnsCheckConfig config;

    public OperatorBootstrap(OperatorAuthService operatorAuthService, DnsCheckConfig config) {
        this.operatorAuthService = operatorAuthService;
        this.config = config;
    }

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

    static String generatePassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    record BootstrapCredentials(String username, String password, boolean generated) {}
}
