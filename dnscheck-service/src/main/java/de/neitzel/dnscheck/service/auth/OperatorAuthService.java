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

@ApplicationScoped
public class OperatorAuthService {

    private final OperatorMfaService operatorMfaService;
    private final MfaCodeService mfaCodeService;

    @Inject
    public OperatorAuthService(OperatorMfaService operatorMfaService, MfaCodeService mfaCodeService) {
        this.operatorMfaService = operatorMfaService;
        this.mfaCodeService = mfaCodeService;
    }

    public Optional<Operator> authenticate(String username, String password) {
        return authenticate(username, password, null);
    }

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

    private static WebApplicationException unauthorizedMfa(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return new WebApplicationException(
                Response.status(Response.Status.UNAUTHORIZED)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(body)
                        .build());
    }

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

    @Transactional
    public Operator createOperator(String username, String password) {
        Operator operator = new Operator();
        operator.username = username;
        operator.passwordHash = BcryptUtil.bcryptHash(password);
        operator.persist();
        return operator;
    }

    @Transactional
    public void grantGlobalRight(Operator operator, GlobalRightKind kind) {
        GlobalGrant grant = new GlobalGrant();
        grant.operator = operator;
        grant.kind = kind;
        grant.persist();
    }

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
