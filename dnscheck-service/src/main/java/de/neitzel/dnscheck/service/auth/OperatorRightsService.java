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

/**
 * Decides whether the signed-in operator has a global right or a zone right.
 * Zone access comes from the admin global right, from being the zone owner, or from a supplemental zone grant.
 */
@ApplicationScoped
public class OperatorRightsService {

    /**
     * Security identity of the current request. The principal name is the signed-in operator's username.
     */
    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Returns whether the signed-in operator has the admin global right, directly or through a group.
     *
     * @return {@code true} when the admin global right is granted.
     */
    @Transactional
    public boolean isAdmin() {
        Operator operator = currentOperator();
        if (operator == null) {
            return false;
        }
        return hasGlobalRight(operator, GlobalRightKind.ADMIN);
    }

    /**
     * Returns whether the signed-in operator may create zone definitions.
     * The admin global right and the zone-create global right both allow creation.
     *
     * @return {@code true} when either global right is granted.
     */
    @Transactional
    public boolean canCreateZone() {
        Operator operator = currentOperator();
        if (operator == null) {
            return false;
        }
        return hasGlobalRight(operator, GlobalRightKind.ADMIN)
                || hasGlobalRight(operator, GlobalRightKind.ZONE_CREATE);
    }

    /**
     * Returns whether the signed-in operator may change one zone catalog entry.
     * Write access is derived from the zone owner, from the admin global right, or from a supplemental zone grant
     * of the write zone right.
     *
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @return {@code true} when write access is allowed.
     * @throws IllegalArgumentException When derived access is evaluated and {@code ownerFolder} is not a valid
     *     owner folder.
     */
    @Transactional
    public boolean canWriteZone(String ownerFolder, String zoneName) {
        Operator operator = currentOperator();
        if (hasDerivedAccess(operator, ownerFolder, zoneName)) {
            return true;
        }
        return hasSupplementalZoneRight(operator, ownerFolder, zoneName, ZoneRightKind.WRITE);
    }

    /**
     * Returns whether the signed-in operator may view one zone catalog entry.
     * Read access is derived from the zone owner or the admin global right, or granted by a supplemental zone grant
     * of the read or write zone right.
     *
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @return {@code true} when read access is allowed.
     * @throws IllegalArgumentException When derived access is evaluated and {@code ownerFolder} is not a valid
     *     owner folder.
     */
    @Transactional
    public boolean canReadZone(String ownerFolder, String zoneName) {
        Operator operator = currentOperator();
        if (hasDerivedAccess(operator, ownerFolder, zoneName)) {
            return true;
        }
        return hasSupplementalZoneRight(operator, ownerFolder, zoneName, ZoneRightKind.READ)
                || hasSupplementalZoneRight(operator, ownerFolder, zoneName, ZoneRightKind.WRITE);
    }

    /**
     * Returns whether the signed-in operator may create a zone definition in an owner folder.
     * Creation requires the admin or zone-create global right, and the operator must be the zone owner for that
     * folder: the operator username for an operator owner folder, or membership in the named group.
     *
     * @param ownerFolder Owner folder in which a zone definition would be created.
     * @return {@code true} when creation in that folder is allowed.
     * @throws IllegalArgumentException When the operator may create zones and {@code ownerFolder} is not a valid
     *     owner folder.
     */
    @Transactional
    public boolean canCreateInOwnerFolder(String ownerFolder) {
        Operator operator = currentOperator();
        if (operator == null || !canCreateZone()) {
            return false;
        }
        return ownsOwnerFolder(operator, OwnerFolder.parse(ownerFolder));
    }

    /**
     * Returns whether the signed-in operator may transfer zone definitions out of an owner folder.
     * The admin global right allows any folder. Any other operator may transfer only the operator owner folder
     * whose identifier equals that operator's username.
     *
     * @param ownerFolder Owner folder that would be the source of the transfer.
     * @return {@code true} when transfer from that folder is allowed.
     * @throws IllegalArgumentException When the operator lacks the admin global right and {@code ownerFolder} is
     *     not a valid owner folder.
     */
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

    /**
     * Rejects the call unless the signed-in operator may transfer zone definitions out of an owner folder.
     *
     * @param ownerFolder Owner folder that would be the source of the transfer.
     * @throws ForbiddenException When transfer from {@code ownerFolder} is not allowed.
     * @throws IllegalArgumentException When the owner folder must be parsed and is not valid.
     */
    public void requireTransfer(String ownerFolder) {
        if (!canTransferZone(ownerFolder)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Rejects the call unless the signed-in operator has the admin global right.
     *
     * @throws ForbiddenException When the admin global right is absent.
     */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenException();
        }
    }

    /**
     * Rejects the call unless the signed-in operator has the admin or zone-create global right.
     *
     * @throws ForbiddenException When neither global right is granted.
     */
    public void requireZoneCreate() {
        if (!canCreateZone()) {
            throw new ForbiddenException();
        }
    }

    /**
     * Rejects the call unless the signed-in operator may create a zone definition in an owner folder.
     *
     * @param ownerFolder Owner folder in which a zone definition would be created.
     * @throws ForbiddenException When creation in {@code ownerFolder} is not allowed.
     * @throws IllegalArgumentException When the owner folder must be parsed and is not valid.
     */
    public void requireCreateInOwnerFolder(String ownerFolder) {
        if (!canCreateInOwnerFolder(ownerFolder)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Rejects the call unless the signed-in operator has the write zone right on a zone catalog entry.
     *
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @throws ForbiddenException When write access is not allowed.
     * @throws IllegalArgumentException When the owner folder must be parsed and is not valid.
     */
    public void requireWrite(String ownerFolder, String zoneName) {
        if (!canWriteZone(ownerFolder, zoneName)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Rejects the call unless the signed-in operator has the read zone right on a zone catalog entry.
     *
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @throws ForbiddenException When read access is not allowed.
     * @throws IllegalArgumentException When the owner folder must be parsed and is not valid.
     */
    public void requireRead(String ownerFolder, String zoneName) {
        if (!canReadZone(ownerFolder, zoneName)) {
            throw new ForbiddenException();
        }
    }

    /**
     * Returns the signed-in operator.
     *
     * @return The persisted operator for the current session.
     * @throws NotAuthorizedException When the request is anonymous or the principal does not match an operator.
     */
    @Transactional
    public Operator requireCurrentOperator() {
        Operator operator = currentOperator();
        if (operator == null) {
            throw new NotAuthorizedException("Not authenticated");
        }
        return operator;
    }

    /**
     * Loads the operator named by the current security identity.
     *
     * @return The persisted operator, or null when the request is anonymous or the username is unknown.
     */
    private Operator currentOperator() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return null;
        }
        return Operator.findByUsername(securityIdentity.getPrincipal().getName());
    }

    /**
     * Returns whether zone access follows from the admin global right or from zone ownership.
     * A blank zone name does not grant derived access. Supplemental zone grants are not considered here.
     *
     * @param operator Signed-in operator, or null when the request is anonymous.
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @return {@code true} when the operator is an admin or the zone owner for {@code ownerFolder}.
     * @throws IllegalArgumentException When the operator is present, {@code zoneName} is not blank, and
     *     {@code ownerFolder} is not a valid owner folder.
     */
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

    /**
     * Returns whether the operator is the zone owner for an owner folder.
     * An operator owner folder matches the operator username. A group owner folder matches a group the operator
     * belongs to.
     *
     * @param operator Operator whose ownership is tested.
     * @param ownerFolder Parsed owner folder.
     * @return {@code true} when the operator owns that folder.
     */
    private static boolean ownsOwnerFolder(Operator operator, OwnerFolder ownerFolder) {
        if (ownerFolder.kind() == OwnerFolderKind.USER) {
            return ownerFolder.identifier().equals(operator.username);
        }
        return operator.groups.stream().anyMatch(group -> group.name.equals(ownerFolder.identifier()));
    }

    /**
     * Returns whether a supplemental zone grant of the given zone right exists for the operator or one of the
     * operator's groups on that zone catalog entry.
     *
     * @param operator Signed-in operator, or null when the request is anonymous.
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param kind Zone right (read or write) to look up.
     * @return {@code true} when such a supplemental zone grant is persisted.
     */
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

    /**
     * Returns whether a global right is granted to the operator or to one of the operator's groups.
     *
     * @param operator Operator whose global rights are checked.
     * @param kind Global right (admin or zone-create) to look up.
     * @return {@code true} when a matching global grant is persisted.
     */
    private static boolean hasGlobalRight(Operator operator, GlobalRightKind kind) {
        if (GlobalGrant.count("operator = ?1 and kind = ?2", operator, kind) > 0) {
            return true;
        }
        return GlobalGrant.count("group in ?1 and kind = ?2", operator.groups, kind) > 0;
    }
}
