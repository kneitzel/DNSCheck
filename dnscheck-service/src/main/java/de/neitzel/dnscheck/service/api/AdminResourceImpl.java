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

/**
 * HTTP API for operators, groups, global rights, supplemental zone grants, and group TSIG key
 * files. Admin changes are applied in a single transaction.
 */
@ApplicationScoped
@Transactional
public class AdminResourceImpl implements AdminResource {

    /**
     * Creates and updates operators, groups, global rights, and supplemental zone grants.
     */
    private final AdminService adminService;
    /**
     * Builds the effective global rights and zone rights for an operator or a group.
     */
    private final RightsSummaryService rightsSummaryService;
    /**
     * Reads and writes TSIG key files stored in a group's owner folder.
     */
    private final GroupKeyService groupKeyService;
    /**
     * Resolves the authenticated operator for group key operations.
     */
    private final OperatorRightsService operatorRightsService;

    /**
     * Creates the admin API adapter.
     *
     * @param adminService Persists operators, groups, global rights, and supplemental zone grants.
     * @param rightsSummaryService Builds effective global rights and zone rights.
     * @param groupKeyService Reads and writes TSIG key files in a group's owner folder.
     * @param operatorRightsService Resolves the current operator for group key operations.
     */
    @Inject
    public AdminResourceImpl(
            AdminService adminService,
            RightsSummaryService rightsSummaryService,
            GroupKeyService groupKeyService,
            OperatorRightsService operatorRightsService) {
        this.adminService = adminService;
        this.rightsSummaryService = rightsSummaryService;
        this.groupKeyService = groupKeyService;
        this.operatorRightsService = operatorRightsService;
    }

    /**
     * Handles GET /api/v1/admin/operators/{operatorId} by returning one operator. Requires the admin
     * global right.
     *
     * @param operatorId Identifies the operator to read.
     * @return Admin summary of that operator.
     */
    @Override
    public OperatorSummary getOperator(Long operatorId) {
        return AdminApiMapper.toOperatorSummary(adminService.getOperator(operatorId));
    }

    /**
     * Handles GET /api/v1/admin/operators by listing operators. Requires the admin global right.
     *
     * @return Operators known to DNSCheck.
     */
    @Override
    public OperatorListResponse listOperators() {
        return AdminApiMapper.toOperatorListResponse(adminService.listOperators());
    }

    /**
     * Handles POST /api/v1/admin/operators by creating an operator account. Requires the admin global
     * right.
     *
     * @param request Supplies the username and password for the new operator.
     * @return Admin summary of the created operator.
     */
    @Override
    public OperatorSummary createOperator(CreateOperatorRequest request) {
        return AdminApiMapper.toOperatorSummary(
                adminService.createOperator(request.getUsername(), request.getPassword()));
    }

    /**
     * Handles PATCH /api/v1/admin/operators/{operatorId} by updating an operator's enabled state or
     * password. Requires the admin global right.
     *
     * @param operatorId Identifies the operator to update.
     * @param request Supplies an optional enabled flag and an optional replacement password.
     * @return Admin summary of the updated operator.
     */
    @Override
    public OperatorSummary updateOperator(Long operatorId, UpdateOperatorRequest request) {
        return AdminApiMapper.toOperatorSummary(
                adminService.updateOperator(operatorId, request.getEnabled(), request.getPassword()));
    }

    /**
     * Handles POST /api/v1/admin/operators/{operatorId}/mfa/reset by performing an MFA reset.
     * Requires the admin global right. The operator can then sign in with a password and start MFA
     * enrollment again.
     *
     * @param operatorId Identifies the operator whose MFA enrollment and recovery material are
     *     cleared.
     * @return Admin summary of the operator after the MFA reset.
     */
    @Override
    public OperatorSummary resetOperatorMfa(Long operatorId) {
        return AdminApiMapper.toOperatorSummary(adminService.resetOperatorMfa(operatorId));
    }

    /**
     * Handles GET /api/v1/admin/operators/{operatorId}/rights by returning one operator's effective
     * global rights and zone rights. Requires the admin global right.
     *
     * @param operatorId Identifies the operator whose rights are summarized.
     * @return Effective global rights and zone rights for that operator.
     */
    @Override
    public RightsSummaryResponse getOperatorRights(Long operatorId) {
        return RightsApiMapper.toRightsSummaryResponse(rightsSummaryService.getOperatorRights(operatorId));
    }

    /**
     * Handles GET /api/v1/admin/groups/{groupId} by returning one operator group. An operator with
     * the admin global right, or a member of the group, may read it.
     *
     * @param groupId Identifies the group to read.
     * @return Admin summary of that group.
     */
    @Override
    public GroupSummary getGroup(Long groupId) {
        return AdminApiMapper.toGroupSummary(adminService.getGroup(groupId));
    }

    /**
     * Handles GET /api/v1/admin/groups by listing operator groups. Requires the admin global right.
     *
     * @return Operator groups known to DNSCheck.
     */
    @Override
    public GroupListResponse listGroups() {
        return AdminApiMapper.toGroupListResponse(adminService.listGroups());
    }

    /**
     * Handles POST /api/v1/admin/groups by creating an operator group. Requires the admin global
     * right.
     *
     * @param request Supplies the group name.
     * @return Admin summary of the created group.
     */
    @Override
    public GroupSummary createGroup(CreateGroupRequest request) {
        return AdminApiMapper.toGroupSummary(adminService.createGroup(request.getName()));
    }

    /**
     * Handles GET /api/v1/admin/groups/{groupId}/rights by returning global rights and zone rights
     * granted directly to a group. Requires the admin global right.
     *
     * @param groupId Identifies the group whose rights are summarized.
     * @return Global rights and zone rights held by that group.
     */
    @Override
    public RightsSummaryResponse getGroupRights(Long groupId) {
        return RightsApiMapper.toRightsSummaryResponse(rightsSummaryService.getGroupRights(groupId));
    }

    /**
     * Handles DELETE /api/v1/admin/groups/{groupId} by deleting an operator group. Requires the admin
     * global right.
     *
     * @param groupId Identifies the group to delete.
     * @return HTTP 204 response after the group is deleted.
     */
    @Override
    public Response deleteGroup(Long groupId) {
        adminService.deleteGroup(groupId);
        return Response.noContent().build();
    }

    /**
     * Handles PUT /api/v1/admin/groups/{groupId}/members/{operatorId} by adding an operator to a
     * group. Requires the admin global right.
     *
     * @param groupId Identifies the group that gains the member.
     * @param operatorId Identifies the operator to add.
     * @return Admin summary of the group after the member is added.
     */
    @Override
    public GroupSummary addGroupMember(Long groupId, Long operatorId) {
        return AdminApiMapper.toGroupSummary(adminService.addGroupMember(groupId, operatorId));
    }

    /**
     * Handles DELETE /api/v1/admin/groups/{groupId}/members/{operatorId} by removing an operator from
     * a group. Requires the admin global right.
     *
     * @param groupId Identifies the group that loses the member.
     * @param operatorId Identifies the operator to remove.
     * @return Admin summary of the group after the member is removed.
     */
    @Override
    public GroupSummary removeGroupMember(Long groupId, Long operatorId) {
        return AdminApiMapper.toGroupSummary(adminService.removeGroupMember(groupId, operatorId));
    }

    /**
     * Handles GET /api/v1/admin/global-grants by listing global right grants. Requires the admin
     * global right.
     *
     * @return Global right grants assigned to operators and groups.
     */
    @Override
    public GlobalGrantListResponse listGlobalGrants() {
        return AdminApiMapper.toGlobalGrantListResponse(adminService.listGlobalGrants());
    }

    /**
     * Handles POST /api/v1/admin/global-grants by granting a global right to an operator or a group.
     * Requires the admin global right.
     *
     * @param request Supplies the global right and exactly one of an operator or a group.
     * @return Summary of the created global right grant.
     */
    @Override
    public GlobalGrantSummary createGlobalGrant(CreateGlobalGrantRequest request) {
        return AdminApiMapper.toGlobalGrantSummary(adminService.createGlobalGrant(
                AdminApiMapper.toEntityGlobalRight(request.getKind()),
                request.getOperatorId(),
                request.getGroupId()));
    }

    /**
     * Handles DELETE /api/v1/admin/global-grants/{grantId} by revoking a global right grant.
     * Requires the admin global right.
     *
     * @param grantId Identifies the global right grant to revoke.
     * @return HTTP 204 response after the grant is revoked.
     */
    @Override
    public Response deleteGlobalGrant(Long grantId) {
        adminService.deleteGlobalGrant(grantId);
        return Response.noContent().build();
    }

    /**
     * Handles GET /api/v1/admin/zone-grants by listing supplemental zone grants. Requires the admin
     * global right.
     *
     * @return Supplemental zone grants assigned to operators and groups.
     */
    @Override
    public ZoneGrantListResponse listZoneGrants() {
        return AdminApiMapper.toZoneGrantListResponse(adminService.listZoneGrants());
    }

    /**
     * Handles POST /api/v1/admin/zone-grants by creating a supplemental zone grant for one zone
     * catalog entry. Requires the admin global right.
     *
     * @param request Supplies the owner folder, zone name, zone right, and exactly one of an operator
     *     or a group.
     * @return Summary of the created supplemental zone grant.
     */
    @Override
    public ZoneGrantSummary createZoneGrant(CreateZoneGrantRequest request) {
        return AdminApiMapper.toZoneGrantSummary(adminService.createZoneGrant(
                request.getOwnerFolder(),
                request.getZoneName(),
                AdminApiMapper.toEntityZoneRight(request.getKind()),
                request.getOperatorId(),
                request.getGroupId()));
    }

    /**
     * Handles DELETE /api/v1/admin/zone-grants/{grantId} by revoking a supplemental zone grant.
     * Requires the admin global right.
     *
     * @param grantId Identifies the supplemental zone grant to revoke.
     * @return HTTP 204 response after the grant is revoked.
     */
    @Override
    public Response deleteZoneGrant(Long grantId) {
        adminService.deleteZoneGrant(grantId);
        return Response.noContent().build();
    }

    /**
     * Handles GET /api/v1/admin/groups/{groupId}/keys by listing TSIG key files in the group's owner
     * folder. Requires an authenticated operator who holds the admin global right or belongs to the
     * group.
     *
     * @param groupId Identifies the group whose owner folder is listed.
     * @return TSIG key summaries stored for that group.
     */
    @Override
    public AccountKeyListResponse listGroupKeys(Long groupId) {
        return toKeyListResponse(groupKeyService.listKeys(
                operatorRightsService.requireCurrentOperator(), groupId));
    }

    /**
     * Handles POST /api/v1/admin/groups/{groupId}/keys by uploading a TSIG key file into the group's
     * owner folder. Requires an authenticated operator who holds the admin global right.
     *
     * @param groupId Identifies the group that receives the key file.
     * @param request Supplies the key name and the key file text.
     * @return HTTP 201 response containing the created group key summary.
     */
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

    /**
     * Handles GET /api/v1/admin/groups/{groupId}/keys/{keyName} by returning one group TSIG key,
     * including its file content. Requires an authenticated operator who holds the admin global
     * right.
     *
     * @param groupId Identifies the group whose owner folder holds the key file.
     * @param keyName Names the group TSIG key file.
     * @return Group key detail, including the key file content.
     */
    @Override
    public AccountKeyDetail getGroupKey(Long groupId, String keyName) {
        return AccountKeysApiMapper.toDetail(groupKeyService.getKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName));
    }

    /**
     * Handles PUT /api/v1/admin/groups/{groupId}/keys/{keyName} by replacing the content of a group
     * TSIG key file. Requires an authenticated operator who holds the admin global right.
     *
     * @param groupId Identifies the group whose owner folder holds the key file.
     * @param keyName Names the group TSIG key file to update.
     * @param request Supplies the replacement key file text.
     * @return Summary of the updated group key.
     */
    @Override
    public AccountKeySummary updateGroupKey(
            Long groupId, String keyName, UpdateAccountKeyRequest request) {
        return AccountKeysApiMapper.toSummary(groupKeyService.updateKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName, request.getContent()));
    }

    /**
     * Handles POST /api/v1/admin/groups/{groupId}/keys/{keyName}/replace by replacing group TSIG key
     * content from uploaded file text. Requires an authenticated operator who holds the admin global
     * right.
     *
     * @param groupId Identifies the group whose owner folder holds the key file.
     * @param keyName Names the group TSIG key file to replace.
     * @param request Supplies the uploaded replacement key file text.
     * @return Summary of the replaced group key.
     */
    @Override
    public AccountKeySummary replaceGroupKey(
            Long groupId, String keyName, ReplaceAccountKeyRequest request) {
        return AccountKeysApiMapper.toSummary(groupKeyService.replaceKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName, request.getContent()));
    }

    /**
     * Handles GET /api/v1/admin/groups/{groupId}/keys/{keyName}/download by returning the text of a
     * group TSIG key file. Requires an authenticated operator who holds the admin global right.
     *
     * @param groupId Identifies the group whose owner folder holds the key file.
     * @param keyName Names the group TSIG key file to download.
     * @return Key file text for the named group key.
     */
    @Override
    public String downloadGroupKey(Long groupId, String keyName) {
        return groupKeyService.downloadKey(
                operatorRightsService.requireCurrentOperator(), groupId, keyName);
    }

    /**
     * Handles DELETE /api/v1/admin/groups/{groupId}/keys/{keyName} by deleting a group TSIG key file.
     * Requires an authenticated operator who holds the admin global right. Deletion is refused when
     * a zone catalog entry still references the key.
     *
     * @param groupId Identifies the group whose owner folder holds the key file.
     * @param keyName Names the group TSIG key file to delete.
     * @return HTTP 204 response after the key file is deleted.
     */
    @Override
    public Response deleteGroupKey(Long groupId, String keyName) {
        groupKeyService.deleteKey(operatorRightsService.requireCurrentOperator(), groupId, keyName);
        return Response.noContent().build();
    }

    /**
     * Maps group TSIG key summaries onto the account key list model reused by the group key
     * endpoint.
     *
     * @param keys Supplies the key names and scoped paths from the group owner folder.
     * @return API list of those key summaries.
     */
    private static AccountKeyListResponse toKeyListResponse(List<PersonalKeyService.PersonalKeySummary> keys) {
        AccountKeyListResponse response = new AccountKeyListResponse();
        response.setKeys(keys.stream().map(AccountKeysApiMapper::toSummary).toList());
        return response;
    }
}
