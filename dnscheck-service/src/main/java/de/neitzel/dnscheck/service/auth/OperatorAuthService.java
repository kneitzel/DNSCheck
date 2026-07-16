package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class OperatorAuthService {

    public Optional<Operator> authenticate(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null || !operator.enabled) {
            return Optional.empty();
        }
        if (!BcryptUtil.matches(password, operator.passwordHash)) {
            return Optional.empty();
        }
        return Optional.of(operator);
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
