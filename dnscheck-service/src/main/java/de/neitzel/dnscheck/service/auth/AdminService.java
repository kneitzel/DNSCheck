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

@ApplicationScoped
public class AdminService {

    private final OperatorRightsService operatorRightsService;
    private final OperatorMfaService operatorMfaService;

    @Inject
    public AdminService(OperatorRightsService operatorRightsService, OperatorMfaService operatorMfaService) {
        this.operatorRightsService = operatorRightsService;
        this.operatorMfaService = operatorMfaService;
    }

    public List<Operator> listOperators() {
        requireAdmin();
        return Operator.find("order by username").<Operator>list();
    }

    public Operator getOperator(Long operatorId) {
        requireAdmin();
        return findOperator(operatorId);
    }

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

    public Operator resetOperatorMfa(Long operatorId) {
        requireAdmin();
        Operator operator = findOperator(operatorId);
        operatorMfaService.resetEnrollment(operator);
        return operator;
    }

    public List<Group> listGroups() {
        requireAdmin();
        return Group.find("order by name").<Group>list();
    }

    public Group getGroup(Long groupId) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        Group group = findGroup(groupId);
        if (operatorRightsService.isAdmin() || group.members.contains(operator)) {
            return group;
        }
        throw new jakarta.ws.rs.ForbiddenException();
    }

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

    public Group addGroupMember(Long groupId, Long operatorId) {
        requireAdmin();
        Group group = findGroup(groupId);
        Operator operator = findOperator(operatorId);
        group.members.add(operator);
        operator.groups.add(group);
        return group;
    }

    public Group removeGroupMember(Long groupId, Long operatorId) {
        requireAdmin();
        Group group = findGroup(groupId);
        Operator operator = findOperator(operatorId);
        group.members.remove(operator);
        operator.groups.remove(group);
        return group;
    }

    public List<GlobalGrant> listGlobalGrants() {
        requireAdmin();
        return GlobalGrant.find("order by id").<GlobalGrant>list();
    }

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

    public void deleteGlobalGrant(Long grantId) {
        requireAdmin();
        GlobalGrant grant = GlobalGrant.findById(grantId);
        if (grant == null) {
            throw new NotFoundException();
        }
        grant.delete();
    }

    public List<ZoneGrant> listZoneGrants() {
        requireAdmin();
        return ZoneGrant.find("order by id").<ZoneGrant>list();
    }

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

    public void deleteZoneGrant(Long grantId) {
        requireAdmin();
        ZoneGrant grant = ZoneGrant.findById(grantId);
        if (grant == null) {
            throw new NotFoundException();
        }
        grant.delete();
    }

    private void requireAdmin() {
        operatorRightsService.requireAdmin();
    }

    private static Operator findOperator(Long operatorId) {
        Operator operator = Operator.findById(operatorId);
        if (operator == null) {
            throw new NotFoundException();
        }
        return operator;
    }

    private static Group findGroup(Long groupId) {
        Group group = Group.findById(groupId);
        if (group == null) {
            throw new NotFoundException();
        }
        return group;
    }

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

    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(message, Response.Status.CONFLICT);
    }

    private record GrantTarget(Operator operator, Group group) {}
}
