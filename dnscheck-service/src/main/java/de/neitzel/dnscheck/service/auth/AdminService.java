package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.core.ownership.OwnerFolder;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Administration operations for operators, groups, global rights, and supplemental zone grants.
 * Callers must hold the admin global right, except when reading a group the operator belongs to.
 */
@ApplicationScoped
public class AdminService {

    /**
     * Resolves the signed-in operator and enforces the admin global right.
     */
    private final OperatorRightsService operatorRightsService;

    /**
     * Clears multi-factor authentication state when an admin performs an MFA reset.
     */
    private final OperatorMfaService operatorMfaService;

    /**
     * Creates the administration service.
     *
     * @param operatorRightsService Resolves the signed-in operator and enforces the admin global right.
     * @param operatorMfaService Clears multi-factor authentication state for an MFA reset.
     */
    @Inject
    public AdminService(OperatorRightsService operatorRightsService, OperatorMfaService operatorMfaService) {
        this.operatorRightsService = operatorRightsService;
        this.operatorMfaService = operatorMfaService;
    }

    /**
     * Returns every operator, ordered by username.
     *
     * @return The persisted operators.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     */
    public List<Operator> listOperators() {
        requireAdmin();
        return Operator.find("order by username").<Operator>list();
    }

    /**
     * Returns one operator by persisted identifier.
     *
     * @param operatorId Persisted identifier of the operator.
     * @return The matching operator.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no operator has {@code operatorId}.
     */
    public Operator getOperator(Long operatorId) {
        requireAdmin();
        return findOperator(operatorId);
    }

    /**
     * Creates an operator and stores a bcrypt hash of the password.
     *
     * @param username Unique username of the new operator.
     * @param password Password to hash. The plaintext is not stored.
     * @return The persisted operator.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws BadRequestException When {@code username} or {@code password} is missing or blank.
     * @throws WebApplicationException When an operator with {@code username} already exists.
     */
    public Operator createOperator(String username, String password) {
        requireAdmin();
        if (username == null || username.isBlank()) {
            throw new BadRequestException("username is required");
        }
        if (password == null || password.isBlank()) {
            throw new BadRequestException("password is required");
        }
        if (Operator.findByUsername(username) != null) {
            throw conflict("Operator already exists: " + username);
        }
        Operator operator = new Operator();
        operator.username = username;
        operator.passwordHash = BcryptUtil.bcryptHash(password);
        operator.persist();
        return operator;
    }

    /**
     * Updates whether an operator may sign in and, when a new password is supplied, replaces the password hash.
     *
     * @param operatorId Persisted identifier of the operator.
     * @param enabled When non-null, whether the operator may sign in. A null value leaves the flag unchanged.
     * @param password When non-blank, the replacement password. It is stored only as a bcrypt hash.
     *     A null or blank value leaves the current hash unchanged.
     * @return The updated operator.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no operator has {@code operatorId}.
     */
    public Operator updateOperator(Long operatorId, Boolean enabled, String password) {
        requireAdmin();
        Operator operator = findOperator(operatorId);
        if (enabled != null) {
            operator.enabled = enabled;
        }
        if (password != null && !password.isBlank()) {
            operator.passwordHash = BcryptUtil.bcryptHash(password);
        }
        return operator;
    }

    /**
     * Performs an MFA reset for an operator, clearing MFA enrollment so the operator can sign in with a password
     * only and enroll again.
     *
     * @param operatorId Persisted identifier of the operator.
     * @return The operator after the MFA reset.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no operator has {@code operatorId}.
     */
    public Operator resetOperatorMfa(Long operatorId) {
        requireAdmin();
        Operator operator = findOperator(operatorId);
        operatorMfaService.resetEnrollment(operator);
        return operator;
    }

    /**
     * Returns every group, ordered by name.
     *
     * @return The persisted groups.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     */
    public List<Group> listGroups() {
        requireAdmin();
        return Group.find("order by name").<Group>list();
    }

    /**
     * Returns a group when the signed-in operator has the admin global right or is a member of that group.
     *
     * @param groupId Persisted identifier of the group.
     * @return The matching group.
     * @throws jakarta.ws.rs.NotAuthorizedException When no operator session is present.
     * @throws NotFoundException When no group has {@code groupId}.
     * @throws jakarta.ws.rs.ForbiddenException When the operator is neither an admin nor a member of the group.
     */
    public Group getGroup(Long groupId) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        Group group = findGroup(groupId);
        if (operatorRightsService.isAdmin() || group.members.contains(operator)) {
            return group;
        }
        throw new jakarta.ws.rs.ForbiddenException();
    }

    /**
     * Creates a group with a unique name.
     *
     * @param name Unique group name. The name is also the identifier of that group's owner folder.
     * @return The persisted group.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws BadRequestException When {@code name} is missing or blank.
     * @throws WebApplicationException When a group with {@code name} already exists.
     */
    public Group createGroup(String name) {
        requireAdmin();
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (Group.findByName(name) != null) {
            throw conflict("Group already exists: " + name);
        }
        Group group = new Group();
        group.name = name;
        group.persist();
        return group;
    }

    /**
     * Deletes a group, removes its memberships, and deletes global rights and supplemental zone grants assigned
     * to the group.
     *
     * @param groupId Persisted identifier of the group.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no group has {@code groupId}.
     */
    public void deleteGroup(Long groupId) {
        requireAdmin();
        Group group = findGroup(groupId);
        for (Operator member : List.copyOf(group.members)) {
            group.members.remove(member);
            member.groups.remove(group);
        }
        GlobalGrant.delete("group", group);
        ZoneGrant.delete("group", group);
        group.delete();
    }

    /**
     * Adds an operator to a group so the operator inherits that group's global rights and supplemental zone grants.
     *
     * @param groupId Persisted identifier of the group.
     * @param operatorId Persisted identifier of the operator to add.
     * @return The group after the membership change.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When the group or the operator does not exist.
     */
    public Group addGroupMember(Long groupId, Long operatorId) {
        requireAdmin();
        Group group = findGroup(groupId);
        Operator operator = findOperator(operatorId);
        group.members.add(operator);
        operator.groups.add(group);
        return group;
    }

    /**
     * Removes an operator from a group so the operator no longer inherits that group's rights.
     *
     * @param groupId Persisted identifier of the group.
     * @param operatorId Persisted identifier of the operator to remove.
     * @return The group after the membership change.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When the group or the operator does not exist.
     */
    public Group removeGroupMember(Long groupId, Long operatorId) {
        requireAdmin();
        Group group = findGroup(groupId);
        Operator operator = findOperator(operatorId);
        group.members.remove(operator);
        operator.groups.remove(group);
        return group;
    }

    /**
     * Returns every persisted global right assignment.
     *
     * @return The global grants, ordered by identifier.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     */
    public List<GlobalGrant> listGlobalGrants() {
        requireAdmin();
        return GlobalGrant.find("order by id").<GlobalGrant>list();
    }

    /**
     * Assigns a global right (admin or zone-create) to exactly one operator or one group.
     *
     * @param kind Global right to assign.
     * @param operatorId Persisted identifier of the operator grantee. Required when {@code groupId} is null.
     * @param groupId Persisted identifier of the group grantee. Required when {@code operatorId} is null.
     * @return The persisted global grant.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws BadRequestException When both or neither grantee identifiers are set.
     * @throws NotFoundException When the chosen operator or group does not exist.
     * @throws WebApplicationException When that grantee already has this global right.
     */
    public GlobalGrant createGlobalGrant(GlobalRightKind kind, Long operatorId, Long groupId) {
        requireAdmin();
        GrantTarget target = resolveGrantTarget(operatorId, groupId);
        if (target.operator() != null
                && GlobalGrant.count("operator = ?1 and kind = ?2", target.operator(), kind) > 0) {
            throw conflict("Global grant already exists");
        }
        if (target.group() != null
                && GlobalGrant.count("group = ?1 and kind = ?2", target.group(), kind) > 0) {
            throw conflict("Global grant already exists");
        }
        GlobalGrant grant = new GlobalGrant();
        grant.kind = kind;
        grant.operator = target.operator();
        grant.group = target.group();
        grant.persist();
        return grant;
    }

    /**
     * Deletes one persisted global right assignment.
     *
     * @param grantId Persisted identifier of the global grant.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no global grant has {@code grantId}.
     */
    public void deleteGlobalGrant(Long grantId) {
        requireAdmin();
        GlobalGrant grant = GlobalGrant.findById(grantId);
        if (grant == null) {
            throw new NotFoundException();
        }
        grant.delete();
    }

    /**
     * Returns every supplemental zone grant.
     *
     * @return The zone grants, ordered by identifier.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     */
    public List<ZoneGrant> listZoneGrants() {
        requireAdmin();
        return ZoneGrant.find("order by id").<ZoneGrant>list();
    }

    /**
     * Creates a supplemental zone grant of a zone right (read or write) on one zone catalog entry.
     * The grant is assigned to exactly one operator or one group, and is identified by owner folder and zone name.
     *
     * @param ownerFolder Owner folder of the zone owner that holds the zone definition.
     * @param zoneName Zone name of the zone catalog entry.
     * @param kind Zone right to grant.
     * @param operatorId Persisted identifier of the operator grantee. Required when {@code groupId} is null.
     * @param groupId Persisted identifier of the group grantee. Required when {@code operatorId} is null.
     * @return The persisted supplemental zone grant.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws BadRequestException When the owner folder or zone name is missing, the owner folder is invalid,
     *     or both or neither grantee identifiers are set.
     * @throws NotFoundException When the chosen operator or group does not exist.
     * @throws WebApplicationException When that grantee already has this zone right on the entry.
     */
    public ZoneGrant createZoneGrant(
            String ownerFolder, String zoneName, ZoneRightKind kind, Long operatorId, Long groupId) {
        requireAdmin();
        if (ownerFolder == null || ownerFolder.isBlank()) {
            throw new BadRequestException("ownerFolder is required");
        }
        if (zoneName == null || zoneName.isBlank()) {
            throw new BadRequestException("zoneName is required");
        }
        try {
            OwnerFolder.parse(ownerFolder);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        GrantTarget target = resolveGrantTarget(operatorId, groupId);
        if (target.operator() != null
                && ZoneGrant.count(
                                "operator = ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                                target.operator(),
                                ownerFolder,
                                zoneName,
                                kind)
                        > 0) {
            throw conflict("Zone grant already exists");
        }
        if (target.group() != null
                && ZoneGrant.count(
                                "group = ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                                target.group(),
                                ownerFolder,
                                zoneName,
                                kind)
                        > 0) {
            throw conflict("Zone grant already exists");
        }
        ZoneGrant grant = new ZoneGrant();
        grant.ownerFolder = ownerFolder;
        grant.zoneName = zoneName;
        grant.kind = kind;
        grant.operator = target.operator();
        grant.group = target.group();
        grant.persist();
        return grant;
    }

    /**
     * Deletes one supplemental zone grant.
     *
     * @param grantId Persisted identifier of the zone grant.
     * @throws jakarta.ws.rs.ForbiddenException When the signed-in operator lacks the admin global right.
     * @throws NotFoundException When no zone grant has {@code grantId}.
     */
    public void deleteZoneGrant(Long grantId) {
        requireAdmin();
        ZoneGrant grant = ZoneGrant.findById(grantId);
        if (grant == null) {
            throw new NotFoundException();
        }
        grant.delete();
    }

    /**
     * Rejects the call unless the signed-in operator has the admin global right.
     *
     * @throws jakarta.ws.rs.ForbiddenException When the operator lacks the admin global right.
     */
    private void requireAdmin() {
        operatorRightsService.requireAdmin();
    }

    /**
     * Loads an operator or rejects the call when the identifier is unknown.
     *
     * @param operatorId Persisted identifier of the operator.
     * @return The matching operator.
     * @throws NotFoundException When no operator has {@code operatorId}.
     */
    private static Operator findOperator(Long operatorId) {
        Operator operator = Operator.findById(operatorId);
        if (operator == null) {
            throw new NotFoundException();
        }
        return operator;
    }

    /**
     * Loads a group or rejects the call when the identifier is unknown.
     *
     * @param groupId Persisted identifier of the group.
     * @return The matching group.
     * @throws NotFoundException When no group has {@code groupId}.
     */
    private static Group findGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new NotFoundException();
        }
        return group;
    }

    /**
     * Resolves the single operator or group that receives a global right or supplemental zone grant.
     *
     * @param operatorId Persisted identifier of the operator grantee, or null when the grant targets a group.
     * @param groupId Persisted identifier of the group grantee, or null when the grant targets an operator.
     * @return The chosen grantee.
     * @throws BadRequestException When both or neither identifiers are set.
     * @throws NotFoundException When the chosen operator or group does not exist.
     */
    private static GrantTarget resolveGrantTarget(Long operatorId, Long groupId) {
        boolean hasOperator = operatorId != null;
        boolean hasGroup = groupId != null;
        if (hasOperator == hasGroup) {
            throw new BadRequestException("Exactly one of operatorId or groupId is required");
        }
        if (hasOperator) {
            return new GrantTarget(findOperator(operatorId), null);
        }
        return new GrantTarget(null, findGroup(groupId));
    }

    /**
     * Builds a conflict failure for a duplicate operator, group, or grant.
     *
     * @param message Explanation returned to the caller.
     * @return A conflict exception carrying {@code message}.
     */
    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(message, Response.Status.CONFLICT);
    }

    /**
     * The single operator or group that receives a global right or supplemental zone grant.
     * Exactly one of the two components is set.
     */
    private record GrantTarget(
            /**
             * Operator grantee, or null when the grant targets a group.
             */
            Operator operator,
            /**
             * Group grantee, or null when the grant targets an operator.
             */
            Group group) {}
}
