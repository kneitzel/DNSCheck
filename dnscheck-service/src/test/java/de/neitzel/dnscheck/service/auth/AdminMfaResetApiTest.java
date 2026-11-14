package de.neitzel.dnscheck.service.auth;

import com.atlassian.oai.validator.model.Request;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.support.OpenApiContractSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Tests an admin MFA reset of another operator's MFA enrollment.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminMfaResetApiTest {

    /**
     * Username of the operator whose MFA enrollment is reset.
     */
    private static final String TARGET_USERNAME = "reset-target";
    /**
     * Password of the operator whose MFA enrollment is reset.
     */
    private static final String TARGET_PASSWORD = "reset-target";

    /**
     * Generator used to produce the confirmation code for MFA enrollment.
     */
    private static final MfaCodeService MFA_CODE_SERVICE = new MfaCodeService();

    /**
     * Service used to create the target operator when it is missing.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the operator whose MFA enrollment is reset.
     */
    private static String targetCookie;
    /**
     * Session cookie for the admin who performs the MFA reset.
     */
    private static String adminCookie;
    /**
     * Pending MFA enrollment secret returned to the target operator.
     */
    private static String setupKey;
    /**
     * Database id of the operator whose MFA enrollment is reset.
     */
    private static Long targetOperatorId;

    /**
     * Creates the target operator when it does not already exist.
     */
    @BeforeEach
    @Transactional
    void ensureTargetOperatorExists() {
        if (Operator.findByUsername(TARGET_USERNAME) == null) {
            operatorAuthService.createOperator(TARGET_USERNAME, TARGET_PASSWORD);
        }
        targetOperatorId = Operator.findByUsername(TARGET_USERNAME).id;
    }

    /**
     * The target operator logs in and starts MFA enrollment, which stays pending and returns recovery codes.
     */
    @Test
    @Order(1)
    void targetLoginAndEnrollMfa() {
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", TARGET_USERNAME, "password", TARGET_PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is(TARGET_USERNAME))
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .response();
        targetCookie = loginResponse.detailedCookie(SessionService.COOKIE_NAME).getValue();

        Response enrollmentResponse = given()
                .cookie(SessionService.COOKIE_NAME, targetCookie)
                .when()
                .post("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("pending"))
                .body("recoveryCodes.size()", is(8))
                .extract()
                .response();
        setupKey = enrollmentResponse.jsonPath().getString("setupKey");
    }

    /**
     * Confirming MFA enrollment with a current code makes the enrollment active.
     */
    @Test
    @Order(2)
    void targetConfirmsMfaEnrollment() {
        String code = MFA_CODE_SERVICE.generateTotp(setupKey, Instant.now().getEpochSecond() / 30);

        given()
                .cookie(SessionService.COOKIE_NAME, targetCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("code", code))
                .when()
                .post("/api/v1/account/security/mfa/confirm")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("active"));
    }

    /**
     * Password-only login is rejected after MFA enrollment is active.
     */
    @Test
    @Order(3)
    void targetLoginRequiresMfaBeforeReset() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", TARGET_USERNAME, "password", TARGET_PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    /**
     * An admin MFA reset clears the target operator's MFA enrollment, pending secret, and recovery codes.
     */
    @Test
    @Order(4)
    void adminResetsTargetMfa() {
        adminCookie = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .detailedCookie(SessionService.COOKIE_NAME)
                .getValue();

        Response response = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .post("/api/v1/admin/operators/%d/mfa/reset".formatted(targetOperatorId))
                .then()
                .statusCode(200)
                .body("username", is(TARGET_USERNAME))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "resetOperatorMfa", Request.Method.POST, "/api/v1/admin/operators/%d/mfa/reset".formatted(targetOperatorId), response);

        Operator operator = Operator.findByUsername(TARGET_USERNAME);
        org.junit.jupiter.api.Assertions.assertFalse(operator.mfaEnabled);
        org.junit.jupiter.api.Assertions.assertNull(operator.mfaSecret);
        org.junit.jupiter.api.Assertions.assertNull(operator.mfaPendingSecret);
        org.junit.jupiter.api.Assertions.assertNull(operator.mfaRecoveryCodeHashes);
    }

    /**
     * After the MFA reset, the target operator can sign in with a password and MFA is disabled.
     */
    @Test
    @Order(5)
    void targetCanLoginWithoutMfaAfterReset() {
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", TARGET_USERNAME, "password", TARGET_PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is(TARGET_USERNAME))
                .extract()
                .response();
        targetCookie = loginResponse.detailedCookie(SessionService.COOKIE_NAME).getValue();

        given()
                .cookie(SessionService.COOKIE_NAME, targetCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("disabled"));
    }
}
