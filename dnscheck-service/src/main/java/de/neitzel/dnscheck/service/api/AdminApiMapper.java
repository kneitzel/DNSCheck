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

public final class AdminApiMapper {

    private AdminApiMapper() {}

    public static OperatorListResponse toOperatorListResponse(List<Operator> operators) {
        OperatorListResponse response = new OperatorListResponse();
        response.setOperators(operators.stream().map(AdminApiMapper::toOperatorSummary).toList());
        return response;
    }

    public static OperatorSummary toOperatorSummary(Operator operator) {
        OperatorSummary summary = new OperatorSummary();
        summary.setId(operator.id);
        summary.setUsername(operator.username);
        summary.setEnabled(operator.enabled);
        summary.setGroupNames(operator.groups.stream().map(group -> group.name).sorted().toList());
        return summary;
    }

    public static GroupListResponse toGroupListResponse(List<Group> groups) {
        GroupListResponse response = new GroupListResponse();
        response.setGroups(groups.stream().map(AdminApiMapper::toGroupSummary).toList());
        return response;
    }

    public static GroupSummary toGroupSummary(Group group) {
        GroupSummary summary = new GroupSummary();
        summary.setId(group.id);
        summary.setName(group.name);
        summary.setMemberUsernames(
                group.members.stream().map(operator -> operator.username).sorted().toList());
        return summary;
    }

    public static GlobalGrantListResponse toGlobalGrantListResponse(List<GlobalGrant> grants) {
        GlobalGrantListResponse response = new GlobalGrantListResponse();
        response.setGrants(grants.stream().map(AdminApiMapper::toGlobalGrantSummary).toList());
        return response;
    }

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

    public static ZoneGrantListResponse toZoneGrantListResponse(List<ZoneGrant> grants) {
        ZoneGrantListResponse response = new ZoneGrantListResponse();
        response.setGrants(grants.stream().map(AdminApiMapper::toZoneGrantSummary).toList());
        return response;
    }

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

    public static GlobalRightKind toEntityGlobalRight(AdminGlobalRight right) {
        return switch (right) {
            case ADMIN -> GlobalRightKind.ADMIN;
            case ZONE_CREATE -> GlobalRightKind.ZONE_CREATE;
        };
    }

    public static ZoneRightKind toEntityZoneRight(AdminZoneRight right) {
        return switch (right) {
            case READ -> ZoneRightKind.READ;
            case WRITE -> ZoneRightKind.WRITE;
        };
    }

    private static AdminGlobalRight toApiGlobalRight(GlobalRightKind kind) {
        return switch (kind) {
            case ADMIN -> AdminGlobalRight.ADMIN;
            case ZONE_CREATE -> AdminGlobalRight.ZONE_CREATE;
        };
    }

    private static AdminZoneRight toApiZoneRight(ZoneRightKind kind) {
        return switch (kind) {
            case READ -> AdminZoneRight.READ;
            case WRITE -> AdminZoneRight.WRITE;
        };
    }
}
