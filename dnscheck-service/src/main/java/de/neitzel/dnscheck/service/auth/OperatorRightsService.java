package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.core.ownership.OwnerFolderKind;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

@ApplicationScoped
public class OperatorRightsService {

    @Inject
    SecurityIdentity securityIdentity;

    @Transactional
    public boolean isAdmin() {
        Operator operator = currentOperator();
        if (operator == null) {
            return false;
        }
        return hasGlobalRight(operator, GlobalRightKind.ADMIN);
    }

    @Transactional
    public boolean canCreateZone() {
        Operator operator = currentOperator();
        if (operator == null) {
            return false;
        }
        return hasGlobalRight(operator, GlobalRightKind.ADMIN)
                || hasGlobalRight(operator, GlobalRightKind.ZONE_CREATE);
    }

    @Transactional
    public boolean canWriteZone(String ownerFolder, String zoneName) {
        Operator operator = currentOperator();
        if (hasDerivedAccess(operator, ownerFolder, zoneName)) {
            return true;
        }
        return hasSupplementalZoneRight(operator, ownerFolder, zoneName, ZoneRightKind.WRITE);
    }

    @Transactional
    public boolean canReadZone(String ownerFolder, String zoneName) {
        Operator operator = currentOperator();
        if (hasDerivedAccess(operator, ownerFolder, zoneName)) {
            return true;
        }
        return hasSupplementalZoneRight(operator, ownerFolder, zoneName, ZoneRightKind.READ)
                || hasSupplementalZoneRight(operator, ownerFolder, zoneName, ZoneRightKind.WRITE);
    }

    @Transactional
    public boolean canCreateInOwnerFolder(String ownerFolder) {
        Operator operator = currentOperator();
        if (operator == null || !canCreateZone()) {
            return false;
        }
        return ownsOwnerFolder(operator, OwnerFolder.parse(ownerFolder));
    }

    @Transactional
    public boolean canTransferZone(String ownerFolder) {
        Operator operator = currentOperator();
        if (operator == null) {
            return false;
        }
        if (hasGlobalRight(operator, GlobalRightKind.ADMIN)) {
            return true;
        }
        OwnerFolder folder = OwnerFolder.parse(ownerFolder);
        return folder.kind() == OwnerFolderKind.USER && folder.identifier().equals(operator.username);
    }

    public void requireTransfer(String ownerFolder) {
        if (!canTransferZone(ownerFolder)) {
            throw new ForbiddenException();
        }
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException();
        }
    }

    public void requireZoneCreate() {
        if (!canCreateZone()) {
            throw new ForbiddenException();
        }
    }

    public void requireCreateInOwnerFolder(String ownerFolder) {
        if (!canCreateInOwnerFolder(ownerFolder)) {
            throw new ForbiddenException();
        }
    }

    public void requireWrite(String ownerFolder, String zoneName) {
        if (!canWriteZone(ownerFolder, zoneName)) {
            throw new ForbiddenException();
        }
    }

    public void requireRead(String ownerFolder, String zoneName) {
        if (!canReadZone(ownerFolder, zoneName)) {
            throw new ForbiddenException();
        }
    }

    @Transactional
    public Operator requireCurrentOperator() {
        Operator operator = currentOperator();
        if (operator == null) {
            throw new NotAuthorizedException("Not authenticated");
        }
        return operator;
    }

    private Operator currentOperator() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return null;
        }
        return Operator.findByUsername(securityIdentity.getPrincipal().getName());
    }

    private static boolean hasDerivedAccess(Operator operator, String ownerFolder, String zoneName) {
        if (operator == null) {
            return false;
        }
        if (zoneName == null || zoneName.isBlank()) {
            return false;
        }
        if (hasGlobalRight(operator, GlobalRightKind.ADMIN)) {
            return true;
        }
        return ownsOwnerFolder(operator, OwnerFolder.parse(ownerFolder));
    }

    private static boolean ownsOwnerFolder(Operator operator, OwnerFolder ownerFolder) {
        if (ownerFolder.kind() == OwnerFolderKind.USER) {
            return ownerFolder.identifier().equals(operator.username);
        }
        return operator.groups.stream().anyMatch(group -> group.name.equals(ownerFolder.identifier()));
    }

    private static boolean hasSupplementalZoneRight(
            Operator operator, String ownerFolder, String zoneName, ZoneRightKind kind) {
        if (operator == null || ownerFolder == null || ownerFolder.isBlank() || zoneName == null || zoneName.isBlank()) {
            return false;
        }
        if (ZoneGrant.count(
                        "operator = ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                        operator,
                        ownerFolder,
                        zoneName,
                        kind)
                > 0) {
            return true;
        }
        return ZoneGrant.count(
                        "group in ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                        operator.groups,
                        ownerFolder,
                        zoneName,
                        kind)
                > 0;
    }

    private static boolean hasGlobalRight(Operator operator, GlobalRightKind kind) {
        if (GlobalGrant.count("operator = ?1 and kind = ?2", operator, kind) > 0) {
            return true;
        }
        return GlobalGrant.count("group in ?1 and kind = ?2", operator.groups, kind) > 0;
    }
}
