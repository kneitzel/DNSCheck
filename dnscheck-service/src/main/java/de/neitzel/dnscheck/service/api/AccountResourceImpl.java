package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.service.api.generated.model.AccountKeyDetail;
import de.neitzel.dnscheck.service.api.generated.model.AccountKeyListResponse;
import de.neitzel.dnscheck.service.api.generated.model.AccountKeySummary;
import de.neitzel.dnscheck.service.api.generated.model.AccountSecurityResponse;
import de.neitzel.dnscheck.service.api.generated.model.ConfirmAccountMfaEnrollmentRequest;
import de.neitzel.dnscheck.service.api.generated.model.ReplaceAccountKeyRequest;
import de.neitzel.dnscheck.service.api.generated.model.UpdateAccountKeyRequest;
import de.neitzel.dnscheck.service.api.generated.model.UploadAccountKeyRequest;
import de.neitzel.dnscheck.service.api.generated.resources.AccountResource;
import de.neitzel.dnscheck.service.auth.OperatorRightsService;
import de.neitzel.dnscheck.service.auth.OperatorMfaService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.keys.PersonalKeyService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * HTTP API for the current operator's account security and personal TSIG key files.
 */
@ApplicationScoped
public class AccountResourceImpl implements AccountResource {

    /**
     * Resolves the authenticated operator before personal key operations.
     */
    @Inject
    OperatorRightsService operatorRightsService;

    /**
     * Reads and writes TSIG key files in the current operator's owner folder.
     */
    @Inject
    PersonalKeyService personalKeyService;

    /**
     * Starts, confirms, and cancels MFA enrollment, and reports account security for the current
     * operator.
     */
    @Inject
    OperatorMfaService operatorMfaService;

    /**
     * Handles GET /api/v1/account/security by returning the current operator's MFA status. Requires
     * an authenticated operator.
     *
     * @return Account security status for the current operator.
     */
    @Override
    public AccountSecurityResponse getAccountSecurity() {
        return operatorMfaService.getCurrentSecurity();
    }

    /**
     * Handles POST /api/v1/account/security by starting MFA enrollment for the current operator.
     * Requires an authenticated operator. The response includes the setup key and the recovery codes
     * issued for that enrollment.
     *
     * @return Account security status for the pending MFA enrollment.
     */
    @Override
    public AccountSecurityResponse startAccountMfaEnrollment() {
        return operatorMfaService.startEnrollment();
    }

    /**
     * Handles POST /api/v1/account/security/mfa/confirm by confirming MFA enrollment with a
     * verification code. Requires an authenticated operator. Enrollment becomes active only after the
     * code is accepted.
     *
     * @param request Supplies the verification code for the pending MFA enrollment.
     * @return Account security status after MFA enrollment becomes active.
     */
    @Override
    public AccountSecurityResponse confirmAccountMfaEnrollment(ConfirmAccountMfaEnrollmentRequest request) {
        return operatorMfaService.confirmEnrollment(request);
    }

    /**
     * Handles DELETE /api/v1/account/security/mfa by cancelling a pending MFA enrollment. Requires an
     * authenticated operator. The pending second factor and the recovery codes issued during that
     * enrollment are cleared.
     *
     * @return Account security status after MFA status returns to disabled.
     */
    @Override
    public AccountSecurityResponse cancelAccountMfaEnrollment() {
        return operatorMfaService.cancelEnrollment();
    }

    /**
     * Handles GET /api/v1/account/keys by listing personal TSIG key files in the current operator's
     * owner folder. Requires an authenticated operator.
     *
     * @return Personal TSIG key summaries for the current operator.
     */
    @Override
    public AccountKeyListResponse listAccountKeys() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        AccountKeyListResponse response = new AccountKeyListResponse();
        response.setKeys(personalKeyService.listKeys(operator).stream()
                .map(AccountKeysApiMapper::toSummary)
                .toList());
        return response;
    }

    /**
     * Handles POST /api/v1/account/keys by uploading a new personal TSIG key file into the current
     * operator's owner folder. Requires an authenticated operator.
     *
     * @param request Supplies the key name and the key file text.
     * @return HTTP 201 response containing the created personal key summary.
     */
    @Override
    public RestResponse<AccountKeySummary> uploadAccountKey(UploadAccountKeyRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        AccountKeySummary summary = AccountKeysApiMapper.toSummary(personalKeyService.createKey(
                operator, request.getKeyName(), request.getContent()));
        return RestResponse.status(Response.Status.CREATED, summary);
    }

    /**
     * Handles GET /api/v1/account/keys/{keyName} by returning one personal TSIG key, including its
     * file content. Requires an authenticated operator.
     *
     * @param keyName Names the personal TSIG key file in the operator's owner folder.
     * @return Personal key detail, including the key file content.
     */
    @Override
    public AccountKeyDetail getAccountKey(String keyName) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return AccountKeysApiMapper.toDetail(personalKeyService.getKey(operator, keyName));
    }

    /**
     * Handles PUT /api/v1/account/keys/{keyName} by replacing the content of a personal TSIG key
     * file. Requires an authenticated operator.
     *
     * @param keyName Names the personal TSIG key file to update.
     * @param request Supplies the replacement key file text.
     * @return Summary of the updated personal key.
     */
    @Override
    public AccountKeySummary updateAccountKey(String keyName, UpdateAccountKeyRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return AccountKeysApiMapper.toSummary(
                personalKeyService.updateKey(operator, keyName, request.getContent()));
    }

    /**
     * Handles POST /api/v1/account/keys/{keyName}/replace by replacing personal TSIG key content from
     * uploaded file text. Requires an authenticated operator.
     *
     * @param keyName Names the personal TSIG key file to replace.
     * @param request Supplies the uploaded replacement key file text.
     * @return Summary of the replaced personal key.
     */
    @Override
    public AccountKeySummary replaceAccountKey(String keyName, ReplaceAccountKeyRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return AccountKeysApiMapper.toSummary(
                personalKeyService.replaceKey(operator, keyName, request.getContent()));
    }

    /**
     * Handles GET /api/v1/account/keys/{keyName}/download by returning the text of a personal TSIG
     * key file. Requires an authenticated operator.
     *
     * @param keyName Names the personal TSIG key file to download.
     * @return Key file text for the named personal key.
     */
    @Override
    public String downloadAccountKey(String keyName) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return personalKeyService.downloadKey(operator, keyName);
    }

    /**
     * Handles DELETE /api/v1/account/keys/{keyName} by deleting a personal TSIG key file. Requires an
     * authenticated operator. Deletion is refused when a zone catalog entry still references the key.
     *
     * @param keyName Names the personal TSIG key file to delete.
     * @return HTTP 204 response after the key file is deleted.
     */
    @Override
    public Response deleteAccountKey(String keyName) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        personalKeyService.deleteKey(operator, keyName);
        return Response.noContent().build();
    }
}
