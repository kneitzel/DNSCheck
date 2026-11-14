package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.AdminGlobalRight;
import de.neitzel.dnscheck.service.api.generated.model.AdminZoneRight;
import de.neitzel.dnscheck.service.api.generated.model.CreateGlobalGrantRequest;
import de.neitzel.dnscheck.service.api.generated.model.CreateGroupRequest;
import de.neitzel.dnscheck.service.api.generated.model.CreateOperatorRequest;
import de.neitzel.dnscheck.service.api.generated.model.CreateZoneGrantRequest;
import de.neitzel.dnscheck.service.api.generated.model.GlobalGrantListResponse;
import de.neitzel.dnscheck.service.api.generated.model.GlobalGrantSummary;
import de.neitzel.dnscheck.service.api.generated.model.GroupListResponse;
import de.neitzel.dnscheck.service.api.generated.model.GroupSummary;
import de.neitzel.dnscheck.service.api.generated.model.OperatorListResponse;
import de.neitzel.dnscheck.service.api.generated.model.OperatorSummary;
import de.neitzel.dnscheck.service.api.generated.model.UpdateOperatorRequest;
import de.neitzel.dnscheck.service.api.generated.model.ZoneGrantListResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneGrantSummary;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;

import java.util.List;

/**
 * Maps operators, groups, global rights, and supplemental zone grants onto admin API models.
 */
public final class AdminApiMapper {

    /**
     * Prevents instantiation of this mapper.
     */
    private AdminApiMapper() {}

    /**
     * Maps operators onto the admin list response.
     *
     * @param operators Supplies the operators to list.
     * @return API list of operator summaries.
     */
    public static OperatorListResponse toOperatorListResponse(List<Operator> operators) {
        OperatorListResponse response = new OperatorListResponse();
        response.setOperators(operators.stream().map(AdminApiMapper::toOperatorSummary).toList());
        return response;
    }

    /**
     * Maps one operator onto the admin summary, including group membership.
     *
     * @param operator Supplies the operator id, username, enabled flag, and groups.
     * @return API operator summary.
     */
    public static OperatorSummary toOperatorSummary(Operator operator) {
        OperatorSummary summary = new OperatorSummary();
        summary.setId(operator.id);
        summary.setUsername(operator.username);
        summary.setEnabled(operator.enabled);
        summary.setGroupNames(operator.groups.stream().map(group -> group.name).sorted().toList());
        return summary;
    }

    /**
     * Maps groups onto the admin list response.
     *
     * @param groups Supplies the groups to list.
     * @return API list of group summaries.
     */
    public static GroupListResponse toGroupListResponse(List<Group> groups) {
        GroupListResponse response = new GroupListResponse();
        response.setGroups(groups.stream().map(AdminApiMapper::toGroupSummary).toList());
        return response;
    }

    /**
     * Maps one group onto the admin summary, including its member operators.
     *
     * @param group Supplies the group id, name, and members.
     * @return API group summary.
     */
    public static GroupSummary toGroupSummary(Group group) {
        GroupSummary summary = new GroupSummary();
        summary.setId(group.id);
        summary.setName(group.name);
        summary.setMemberUsernames(
                group.members.stream().map(operator -> operator.username).sorted().toList());
        return summary;
    }

    /**
     * Maps global right grants onto the admin list response.
     *
     * @param grants Supplies the global right grants to list.
     * @return API list of global right grant summaries.
     */
    public static GlobalGrantListResponse toGlobalGrantListResponse(List<GlobalGrant> grants) {
        GlobalGrantListResponse response = new GlobalGrantListResponse();
        response.setGrants(grants.stream().map(AdminApiMapper::toGlobalGrantSummary).toList());
        return response;
    }

    /**
     * Maps one global right grant onto the admin summary, including the operator or group that holds
     * it.
     *
     * @param grant Supplies the grant id, global right, and the operator or group it is assigned to.
     * @return API summary of one global right grant.
     */
    public static GlobalGrantSummary toGlobalGrantSummary(GlobalGrant grant) {
        GlobalGrantSummary summary = new GlobalGrantSummary();
        summary.setId(grant.id);
        summary.setKind(toApiGlobalRight(grant.kind));
        if (grant.operator != null) {
            summary.setOperatorUsername(grant.operator.username);
        }
        if (grant.group != null) {
            summary.setGroupName(grant.group.name);
        }
        return summary;
    }

    /**
     * Maps supplemental zone grants onto the admin list response.
     *
     * @param grants Supplies the supplemental zone grants to list.
     * @return API list of zone grant summaries.
     */
    public static ZoneGrantListResponse toZoneGrantListResponse(List<ZoneGrant> grants) {
        ZoneGrantListResponse response = new ZoneGrantListResponse();
        response.setGrants(grants.stream().map(AdminApiMapper::toZoneGrantSummary).toList());
        return response;
    }

    /**
     * Maps one supplemental zone grant onto the admin summary, including the owner folder, zone name,
     * and zone right.
     *
     * @param grant Supplies the grant id, zone catalog entry, zone right, and the operator or group.
     * @return API summary of one supplemental zone grant.
     */
    public static ZoneGrantSummary toZoneGrantSummary(ZoneGrant grant) {
        ZoneGrantSummary summary = new ZoneGrantSummary();
        summary.setId(grant.id);
        summary.setOwnerFolder(grant.ownerFolder);
        summary.setZoneName(grant.zoneName);
        summary.setKind(toApiZoneRight(grant.kind));
        if (grant.operator != null) {
            summary.setOperatorUsername(grant.operator.username);
        }
        if (grant.group != null) {
            summary.setGroupName(grant.group.name);
        }
        return summary;
    }

    /**
     * Maps an API global right onto the persisted global right kind.
     *
     * @param right Supplies the admin global right or the zone-create global right from a grant
     *     request.
     * @return Persisted kind stored on the global right grant.
     */
    public static GlobalRightKind toEntityGlobalRight(AdminGlobalRight right) {
        return switch (right) {
            case ADMIN -> GlobalRightKind.ADMIN;
            case ZONE_CREATE -> GlobalRightKind.ZONE_CREATE;
        };
    }

    /**
     * Maps an API zone right onto the persisted zone right kind.
     *
     * @param right Supplies the read zone right or the write zone right from a supplemental zone
     *     grant request.
     * @return Persisted kind stored on the supplemental zone grant.
     */
    public static ZoneRightKind toEntityZoneRight(AdminZoneRight right) {
        return switch (right) {
            case READ -> ZoneRightKind.READ;
            case WRITE -> ZoneRightKind.WRITE;
        };
    }

    /**
     * Maps a persisted global right kind onto the admin API enum.
     *
     * @param kind Supplies the admin global right or the zone-create global right.
     * @return Matching API global right.
     */
    private static AdminGlobalRight toApiGlobalRight(GlobalRightKind kind) {
        return switch (kind) {
            case ADMIN -> AdminGlobalRight.ADMIN;
            case ZONE_CREATE -> AdminGlobalRight.ZONE_CREATE;
        };
    }

    /**
     * Maps a persisted zone right kind onto the admin API enum.
     *
     * @param kind Supplies the read zone right or the write zone right.
     * @return Matching API zone right.
     */
    private static AdminZoneRight toApiZoneRight(ZoneRightKind kind) {
        return switch (kind) {
            case READ -> AdminZoneRight.READ;
            case WRITE -> AdminZoneRight.WRITE;
        };
    }
}
