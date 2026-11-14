package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.api.generated.model.AccountMfaStatus;
import de.neitzel.dnscheck.service.api.generated.model.AccountSecurityResponse;
import de.neitzel.dnscheck.service.api.generated.model.ConfirmAccountMfaEnrollmentRequest;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Manages MFA enrollment, confirmation, cancel MFA enrollment, recovery codes, and MFA reset for an operator.
 */
@ApplicationScoped
@Transactional
public class OperatorMfaService {

    /**
     * Number of recovery codes issued when MFA enrollment starts.
     */
    private static final int RECOVERY_CODE_COUNT = 8;

    /**
     * Resolves the signed-in operator whose multi-factor authentication state is changed.
     */
    private final OperatorRightsService operatorRightsService;

    /**
     * Creates MFA secret material, checks time-based one-time passwords, and hashes recovery codes.
     */
    private final MfaCodeService mfaCodeService;

    /**
     * Creates the multi-factor authentication service.
     *
     * @param operatorRightsService Resolves the signed-in operator.
     * @param mfaCodeService Creates secret material and recovery-code hashes, and checks one-time passwords.
     */
    @Inject
    public OperatorMfaService(OperatorRightsService operatorRightsService, MfaCodeService mfaCodeService) {
        this.operatorRightsService = operatorRightsService;
        this.mfaCodeService = mfaCodeService;
    }

    /**
     * Returns the signed-in operator's multi-factor authentication status.
     * Recovery codes and active secret material are omitted.
     *
     * @return The security view for the current operator.
     * @throws jakarta.ws.rs.NotAuthorizedException When no operator session is present.
     */
    public AccountSecurityResponse getCurrentSecurity() {
        return toResponse(operatorRightsService.requireCurrentOperator(), false, null);
    }

    /**
     * Starts MFA enrollment by storing pending secret material and bcrypt hashes of new recovery codes.
     * The response includes the pending secret and the plaintext recovery codes for this call only.
     *
     * @return The security view for the pending MFA enrollment, including recovery codes.
     * @throws jakarta.ws.rs.NotAuthorizedException When no operator session is present.
     * @throws WebApplicationException When multi-factor authentication is already pending or active.
     */
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

    /**
     * Confirms MFA enrollment when the time-based one-time password matches the pending secret material.
     * On success, multi-factor authentication becomes active and the pending secret is cleared.
     *
     * @param request Confirmation payload containing the one-time password.
     * @return The security view showing active multi-factor authentication.
     * @throws BadRequestException When {@code request} has no code, or MFA enrollment is not pending.
     * @throws jakarta.ws.rs.NotAuthorizedException When no operator session is present.
     * @throws WebApplicationException When the one-time password does not match the pending secret material.
     */
    public AccountSecurityResponse confirmEnrollment(ConfirmAccountMfaEnrollmentRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new BadRequestException("code is required");
        }

        Operator operator = operatorRightsService.requireCurrentOperator();
        if (operator.mfaPendingSecret == null) {
            throw new BadRequestException("MFA enrollment is not pending");
        }
        if (!mfaCodeService.isValidTotpCode(operator.mfaPendingSecret, request.getCode())) {
            throw new WebApplicationException("Invalid MFA code", Response.Status.CONFLICT);
        }

        operator.mfaSecret = operator.mfaPendingSecret;
        operator.mfaPendingSecret = null;
        operator.mfaEnabled = true;
        return toResponse(operator, false, null);
    }

    /**
     * Cancels MFA enrollment, clearing the pending second factor and the recovery codes issued for that enrollment.
     * Multi-factor authentication returns to disabled.
     *
     * @return The security view showing multi-factor authentication disabled.
     * @throws jakarta.ws.rs.NotAuthorizedException When no operator session is present.
     * @throws WebApplicationException When MFA enrollment is not pending.
     */
    public AccountSecurityResponse cancelEnrollment() {
        Operator operator = operatorRightsService.requireCurrentOperator();
        if (operator.mfaPendingSecret == null) {
            throw new WebApplicationException("MFA enrollment is not pending", Response.Status.CONFLICT);
        }
        resetEnrollment(operator);
        return toResponse(operator, false, null);
    }

    /**
     * Consumes one unused recovery code by removing its stored hash.
     *
     * @param operator Operator whose recovery-code hashes are checked.
     * @param candidate Recovery code presented by the operator.
     * @return {@code true} when a stored hash matched and was removed.
     */
    public boolean consumeRecoveryCode(Operator operator, String candidate) {
        if (operator == null || candidate == null || candidate.isBlank() || operator.mfaRecoveryCodeHashes == null) {
            return false;
        }

        List<String> hashes = Arrays.stream(operator.mfaRecoveryCodeHashes.split("\\R"))
            .map(hash -> hash.trim())
                .filter(hash -> !hash.isBlank())
                .toList();
        for (String hash : hashes) {
            if (mfaCodeService.matchesRecoveryCode(candidate, hash)) {
                List<String> remainingHashes = hashes.stream()
                        .filter(existingHash -> !Objects.equals(existingHash, hash))
                        .toList();
                operator.mfaRecoveryCodeHashes = remainingHashes.isEmpty() ? null : String.join("\n", remainingHashes);
                return true;
            }
        }
        return false;
    }

    /**
     * Performs an MFA reset by clearing pending secret material, active secret material, the enabled flag,
     * and recovery-code hashes. The operator can then sign in with a password only and enroll again.
     *
     * @param operator Operator whose multi-factor authentication state is cleared.
     */
    public void resetEnrollment(Operator operator) {
        operator.mfaPendingSecret = null;
        operator.mfaSecret = null;
        operator.mfaEnabled = false;
        operator.mfaRecoveryCodeHashes = null;
    }

    /**
     * Builds the security view for an operator.
     * Pending MFA enrollment includes the setup secret and otpauth URI. Active secret material is omitted.
     * Plaintext recovery codes are included only for the call that started enrollment.
     *
     * @param operator Operator whose multi-factor authentication state is reported.
     * @param includeRecoveryCodes Whether {@code recoveryCodes} may be copied into the response.
     * @param recoveryCodes Plaintext recovery codes issued for a new MFA enrollment, or null.
     * @return The security view.
     */
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

    /**
     * Maps stored multi-factor authentication fields to a status.
     * A pending secret means MFA enrollment is in progress. An enabled flag together with active secret material
     * means multi-factor authentication is active. Otherwise it is disabled.
     *
     * @param operator Operator whose fields are read.
     * @return The multi-factor authentication status.
     */
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
