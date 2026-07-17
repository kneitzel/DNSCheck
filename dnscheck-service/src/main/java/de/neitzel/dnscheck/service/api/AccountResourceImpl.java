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

@ApplicationScoped
public class AccountResourceImpl implements AccountResource {

    @Inject
    OperatorRightsService operatorRightsService;

    @Inject
    PersonalKeyService personalKeyService;

    @Inject
    OperatorMfaService operatorMfaService;

    @Override
    public AccountSecurityResponse getAccountSecurity() {
        return operatorMfaService.getCurrentSecurity();
    }

    @Override
    public AccountSecurityResponse startAccountMfaEnrollment() {
        return operatorMfaService.startEnrollment();
    }

    @Override
    public AccountSecurityResponse confirmAccountMfaEnrollment(ConfirmAccountMfaEnrollmentRequest request) {
        return operatorMfaService.confirmEnrollment(request);
    }

    @Override
    public AccountSecurityResponse cancelAccountMfaEnrollment() {
        return operatorMfaService.cancelEnrollment();
    }

    @Override
    public AccountKeyListResponse listAccountKeys() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        AccountKeyListResponse response = new AccountKeyListResponse();
        response.setKeys(personalKeyService.listKeys(operator).stream()
                .map(AccountKeysApiMapper::toSummary)
                .toList());
        return response;
    }

    @Override
    public RestResponse<AccountKeySummary> uploadAccountKey(UploadAccountKeyRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        AccountKeySummary summary = AccountKeysApiMapper.toSummary(personalKeyService.createKey(
                operator, request.getKeyName(), request.getContent()));
        return RestResponse.status(Response.Status.CREATED, summary);
    }

    @Override
    public AccountKeyDetail getAccountKey(String keyName) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return AccountKeysApiMapper.toDetail(personalKeyService.getKey(operator, keyName));
    }

    @Override
    public AccountKeySummary updateAccountKey(String keyName, UpdateAccountKeyRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return AccountKeysApiMapper.toSummary(
                personalKeyService.updateKey(operator, keyName, request.getContent()));
    }

    @Override
    public AccountKeySummary replaceAccountKey(String keyName, ReplaceAccountKeyRequest request) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return AccountKeysApiMapper.toSummary(
                personalKeyService.replaceKey(operator, keyName, request.getContent()));
    }

    @Override
    public String downloadAccountKey(String keyName) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        return personalKeyService.downloadKey(operator, keyName);
    }

    @Override
    public Response deleteAccountKey(String keyName) {
        Operator operator = operatorRightsService.requireCurrentOperator();
        personalKeyService.deleteKey(operator, keyName);
        return Response.noContent().build();
    }
}
