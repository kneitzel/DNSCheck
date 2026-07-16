package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.api.generated.model.AccountMfaStatus;
import de.neitzel.dnscheck.service.api.generated.model.AccountSecurityResponse;
import de.neitzel.dnscheck.service.api.generated.model.ConfirmAccountMfaEnrollmentRequest;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
@Transactional
public class OperatorMfaService {

    private static final int RECOVERY_CODE_COUNT = 8;

    @Inject
    OperatorRightsService operatorRightsService;

    private final MfaCodeService mfaCodeService = new MfaCodeService();

    public AccountSecurityResponse getCurrentSecurity() {
        return toResponse(operatorRightsService.requireCurrentOperator(), false, null);
    }

    public AccountSecurityResponse startEnrollment() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        if (operator.mfaEnabled || operator.mfaPendingSecret != null) {
            throw new WebApplicationException("MFA is already pending or active", Response.Status.CONFLICT);
        }

        String secret = mfaCodeService.generateSecret();
        List<String> recoveryCodes = mfaCodeService.generateRecoveryCodes(RECOVERY_CODE_COUNT);
        operator.mfaPendingSecret = secret;
        operator.mfaSecret = null;
        operator.mfaEnabled = false;
        operator.mfaRecoveryCodeHashes = String.join("\n", recoveryCodes.stream()
                .map(mfaCodeService::hashRecoveryCode)
                .toList());
        return toResponse(operator, true, recoveryCodes);
    }

    public AccountSecurityResponse confirmEnrollment(ConfirmAccountMfaEnrollmentRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new BadRequestException("code is required");
        }

        Operator operator = operatorRightsService.requireCurrentOperator();
        if (operator.mfaPendingSecret == null) {
            throw new BadRequestException("MFA enrollment is not pending");
        }
        if (!mfaCodeService.isValidTotpCode(operator.mfaPendingSecret, request.getCode())) {
            throw new NotAuthorizedException("Invalid MFA code");
        }

        operator.mfaSecret = operator.mfaPendingSecret;
        operator.mfaPendingSecret = null;
        operator.mfaEnabled = true;
        return toResponse(operator, false, null);
    }

    private AccountSecurityResponse toResponse(Operator operator, boolean includeRecoveryCodes, List<String> recoveryCodes) {
        AccountSecurityResponse response = new AccountSecurityResponse();
        response.setMfaStatus(resolveStatus(operator));
        if (operator.mfaPendingSecret != null) {
            response.setSetupKey(operator.mfaPendingSecret);
            response.setOtpauthUri(mfaCodeService.buildOtpAuthUri(operator.username, operator.mfaPendingSecret));
        }
        if (includeRecoveryCodes && recoveryCodes != null) {
            response.setRecoveryCodes(recoveryCodes);
        }
        return response;
    }

    private static AccountMfaStatus resolveStatus(Operator operator) {
        if (operator.mfaPendingSecret != null) {
            return AccountMfaStatus.PENDING;
        }
        if (operator.mfaEnabled && operator.mfaSecret != null) {
            return AccountMfaStatus.ACTIVE;
        }
        return AccountMfaStatus.DISABLED;
    }
}
