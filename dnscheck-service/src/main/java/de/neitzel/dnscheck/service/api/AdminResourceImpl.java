package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.AccountKeyDetail;
import de.neitzel.dnscheck.service.api.generated.model.AccountKeyListResponse;
import de.neitzel.dnscheck.service.api.generated.model.AccountKeySummary;
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
import de.neitzel.dnscheck.service.api.generated.model.RightsSummaryResponse;
import de.neitzel.dnscheck.service.api.generated.model.UpdateOperatorRequest;
import de.neitzel.dnscheck.service.api.generated.model.ZoneGrantListResponse;
import de.neitzel.dnscheck.service.api.generated.model.ZoneGrantSummary;
import de.neitzel.dnscheck.service.api.generated.resources.AdminResource;
import de.neitzel.dnscheck.service.api.generated.model.ReplaceAccountKeyRequest;
import de.neitzel.dnscheck.service.api.generated.model.UpdateAccountKeyRequest;
import de.neitzel.dnscheck.service.api.generated.model.UploadAccountKeyRequest;
import de.neitzel.dnscheck.service.auth.AdminService;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.RightsSummaryService;
import de.neitzel.dnscheck.service.keys.GroupKeyService;
import de.neitzel.dnscheck.service.keys.PersonalKeyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

@ApplicationScoped
@Transactional
public class AdminResourceImpl implements AdminResource {

    @Inject
    AdminService adminService;

    @Inject
    RightsSummaryService rightsSummaryService;

    @Inject
    GroupKeyService groupKeyService;

    @Inject
    OperatorRightsService operatorRightsService;

    @Override
    public OperatorSummary getOperator(Long operatorId) {
        return AdminApiMapper.toOperatorSummary(adminService.getOperator(operatorId));
    }

    @Override
    public OperatorListResponse listOperators() {
        return AdminApiMapper.toOperatorListResponse(adminService.listOperators());
    }

    @Override
    public OperatorSummary createOperator(CreateOperatorRequest request) {
        return AdminApiMapper.toOperatorSummary(
                adminService.createOperator(request.getUsername(), request.getPassword()));
    }

    @Override
    public OperatorSummary updateOperator(Long operatorId, UpdateOperatorRequest request) {
        return AdminApiMapper.toOperatorSummary(
                adminService.updateOperator(operatorId, request.getEnabled(), request.getPassword()));
    }

    @Override
    public RightsSummaryResponse getOperatorRights(Long operatorId) {
        return RightsApiMapper.toRightsSummaryResponse(rightsSummaryService.getOperatorRights(operatorId));
    }

    @Override
    public GroupSummary getGroup(Long groupId) {
        return AdminApiMapper.toGroupSummary(adminService.getGroup(groupId));
    }

    @Override
    public GroupListResponse listGroups() {
        return AdminApiMapper.toGroupListResponse(adminService.listGroups());
    }

    @Override
    public GroupSummary createGroup(CreateGroupRequest request) {
        return AdminApiMapper.toGroupSummary(adminService.createGroup(request.getName()));
    }

    @Override
    public RightsSummaryResponse getGroupRights(Long groupId) {
        return RightsApiMapper.toRightsSummaryResponse(rightsSummaryService.getGroupRights(groupId));
    }

    @Override
    public Response deleteGroup(Long groupId) {
        adminService.deleteGroup(groupId);
        return Response.noContent().build();
    }

    @Override
    public GroupSummary addGroupMember(Long groupId, Long operatorId) {
        return AdminApiMapper.toGroupSummary(adminService.addGroupMember(groupId, operatorId));
    }

    @Override
    public GroupSummary removeGroupMember(Long groupId, Long operatorId) {
        return AdminApiMapper.toGroupSummary(adminService.removeGroupMember(groupId, operatorId));
    }

    @Override
    public GlobalGrantListResponse listGlobalGrants() {
        return AdminApiMapper.toGlobalGrantListResponse(adminService.listGlobalGrants());
    }

    @Override
    public GlobalGrantSummary createGlobalGrant(CreateGlobalGrantRequest request) {
        return AdminApiMapper.toGlobalGrantSummary(adminService.createGlobalGrant(
                AdminApiMapper.toEntityGlobalRight(request.getKind()),
                request.getOperatorId(),
                request.getGroupId()));
    }

    @Override
    public Response deleteGlobalGrant(Long grantId) {
        adminService.deleteGlobalGrant(grantId);
        return Response.noContent().build();
    }

    @Override
    public ZoneGrantListResponse listZoneGrants() {
        return AdminApiMapper.toZoneGrantListResponse(adminService.listZoneGrants());
    }

    @Override
    public ZoneGrantSummary createZoneGrant(CreateZoneGrantRequest request) {
        return AdminApiMapper.toZoneGrantSummary(adminService.createZoneGrant(
                request.getOwnerFolder(),
                request.getZoneName(),
                AdminApiMapper.toEntityZoneRight(request.getKind()),
                request.getOperatorId(),
                request.getGroupId()));
    }

    @Override
    public Response deleteZoneGrant(Long grantId) {
        adminService.deleteZoneGrant(grantId);
        return Response.noContent().build();
    }

    @Override
    public AccountKeyListResponse listGroupKeys(Long groupId) {
        return toKeyListResponse(groupKeyService.listKeys(
                operatorRightsService.requireCurrentOperator(), groupId));
    }

    @Override
    public RestResponse<AccountKeySummary> uploadGroupKey(Long groupId, UploadAccountKeyRequest request) {
        AccountKeySummary summary = AccountKeysApiMapper.toSummary(
                groupKeyService.createKey(
                        operatorRightsService.requireCurrentOperator(),
                        groupId,
                        request.getKeyName(),
                        request.getContent()));
        return RestResponse.status(Response.Status.CREATED, summary);
    }

    @Override
    public AccountKeyDetail getGroupKey(Long groupId, String keyName) {
        return AccountKeysApiMapper.toDetail(groupKeyService.getKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName));
    }

    @Override
    public AccountKeySummary updateGroupKey(
            Long groupId, String keyName, UpdateAccountKeyRequest request) {
        return AccountKeysApiMapper.toSummary(groupKeyService.updateKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName, request.getContent()));
    }

    @Override
    public AccountKeySummary replaceGroupKey(
            Long groupId, String keyName, ReplaceAccountKeyRequest request) {
        return AccountKeysApiMapper.toSummary(groupKeyService.replaceKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName, request.getContent()));
    }

    @Override
    public String downloadGroupKey(Long groupId, String keyName) {
        return groupKeyService.downloadKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName);
    }

    @Override
    public Response deleteGroupKey(Long groupId, String keyName) {
        groupKeyService.deleteKey(operatorRightsService.requireCurrentOperator(), groupId, keyName);
        return Response.noContent().build();
    }

    private static AccountKeyListResponse toKeyListResponse(List<PersonalKeyService.PersonalKeySummary> keys) {
        AccountKeyListResponse response = new AccountKeyListResponse();
        response.setKeys(keys.stream().map(AccountKeysApiMapper::toSummary).toList());
        return response;
    }
}
