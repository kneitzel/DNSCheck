package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authenticates an operator and persists operators, global rights, and supplemental zone grants.
 * When multi-factor authentication is active, sign-in requires a time-based one-time password or a recovery code.
 */
@ApplicationScoped
public class OperatorAuthService {

    /**
     * Consumes a recovery code when the time-based one-time password does not match.
     */
    private final OperatorMfaService operatorMfaService;

    /**
     * Checks a time-based one-time password against the operator's MFA secret material.
     */
    private final MfaCodeService mfaCodeService;

    /**
     * Creates the authentication service.
     *
     * @param operatorMfaService Consumes a recovery code during sign-in.
     * @param mfaCodeService Checks time-based one-time passwords.
     */
    @Inject
    public OperatorAuthService(OperatorMfaService operatorMfaService, MfaCodeService mfaCodeService) {
        this.operatorMfaService = operatorMfaService;
        this.mfaCodeService = mfaCodeService;
    }

    /**
     * Authenticates an operator with a password and no second factor.
     * This succeeds only when the password hash matches and multi-factor authentication is not active.
     *
     * @param username Operator username.
     * @param password Password to check against the stored bcrypt hash.
     * @return The operator when authentication succeeds, or empty when the operator is missing, disabled,
     *     or the password does not match.
     * @throws WebApplicationException When multi-factor authentication is active, because no second factor was supplied.
     */
    public Optional<Operator> authenticate(String username, String password) {
        return authenticate(username, password, null);
    }

    /**
     * Authenticates an operator with a password and, when multi-factor authentication is active, a second factor.
     * A matching recovery code is consumed so it cannot be used again.
     *
     * @param username Operator username.
     * @param password Password to check against the stored bcrypt hash.
     * @param mfaCode Time-based one-time password or recovery code. Required when multi-factor authentication is active.
     * @return The operator when authentication succeeds, or empty when the operator is missing, disabled,
     *     or the password does not match.
     * @throws WebApplicationException When multi-factor authentication is active and {@code mfaCode} is missing or invalid.
     */
    @Transactional
    public Optional<Operator> authenticate(String username, String password, String mfaCode) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null || !operator.enabled) {
            return Optional.empty();
        }
        if (!BcryptUtil.matches(password, operator.passwordHash)) {
            return Optional.empty();
        }
        if (operator.mfaEnabled) {
            requireValidMfaCode(operator, mfaCode);
        }
        return Optional.of(operator);
    }

    /**
     * Requires a valid time-based one-time password or an unused recovery code before a session is created.
     * A matching recovery code is removed from the operator's stored hashes.
     *
     * @param operator Operator whose multi-factor authentication is active.
     * @param mfaCode Time-based one-time password or recovery code presented at sign-in.
     * @throws WebApplicationException When {@code mfaCode} is missing or matches neither the secret nor an unused
     *     recovery code.
     */
    private void requireValidMfaCode(Operator operator, String mfaCode) {
        if (mfaCode == null || mfaCode.isBlank()) {
            throw unauthorizedMfa("MFA code required");
        }
        if (mfaCodeService.isValidTotpCode(operator.mfaSecret, mfaCode)) {
            return;
        }
        if (operatorMfaService.consumeRecoveryCode(operator, mfaCode)) {
            return;
        }
        throw unauthorizedMfa("Invalid MFA code");
    }

    /**
     * Builds an unauthorized failure that reports a multi-factor authentication problem.
     *
     * @param message Explanation placed in the JSON body.
     * @return An unauthorized exception whose entity is a JSON object containing {@code message}.
     */
    private static WebApplicationException unauthorizedMfa(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return new WebApplicationException(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(body)
                        .build());
    }

    /**
     * Replaces an operator's password hash after the current password matches.
     *
     * @param operator Operator whose password hash is updated.
     * @param currentPassword Password that must match the stored bcrypt hash.
     * @param newPassword Replacement password. It is stored only as a bcrypt hash.
     * @throws IllegalArgumentException When {@code currentPassword} or {@code newPassword} is missing or blank.
     * @throws NotAuthorizedException When {@code currentPassword} does not match the stored hash.
     */
    public void changePassword(Operator operator, String currentPassword, String newPassword) {
        if (currentPassword == null
                || currentPassword.isBlank()
                || newPassword == null
                || newPassword.isBlank()) {
            throw new IllegalArgumentException("currentPassword and newPassword are required");
        }
        if (!BcryptUtil.matches(currentPassword, operator.passwordHash)) {
            throw new NotAuthorizedException("Invalid current password");
        }
        operator.passwordHash = BcryptUtil.bcryptHash(newPassword);
    }

    /**
     * Persists a new operator with a bcrypt hash of the password.
     *
     * @param username Username stored for the operator.
     * @param password Password to hash. The plaintext is not stored.
     * @return The persisted operator.
     */
    @Transactional
    public Operator createOperator(String username, String password) {
        Operator operator = new Operator();
        operator.username = username;
        operator.passwordHash = BcryptUtil.bcryptHash(password);
        operator.persist();
        return operator;
    }

    /**
     * Persists a global right (admin or zone-create) for an operator.
     *
     * @param operator Operator that receives the global right.
     * @param kind Global right to assign.
     */
    @Transactional
    public void grantGlobalRight(Operator operator, GlobalRightKind kind) {
        GlobalGrant grant = new GlobalGrant();
        grant.operator = operator;
        grant.kind = kind;
        grant.persist();
    }

    /**
     * Persists a supplemental zone grant for an operator on one zone catalog entry.
     *
     * @param operator Operator that receives the zone right.
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param kind Zone right (read or write) to grant.
     */
    @Transactional
    public void grantZoneRight(Operator operator, String ownerFolder, String zoneName, ZoneRightKind kind) {
        ZoneGrant grant = new ZoneGrant();
        grant.operator = operator;
        grant.ownerFolder = ownerFolder;
        grant.zoneName = zoneName;
        grant.kind = kind;
        grant.persist();
    }
}
