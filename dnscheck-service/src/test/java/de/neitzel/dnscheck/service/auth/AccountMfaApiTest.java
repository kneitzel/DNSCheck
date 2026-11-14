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
 * Tests MFA enrollment, recovery codes, and sign-in after enrollment is active.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountMfaApiTest {

    /**
     * Generator used to produce time-based codes for MFA enrollment and login.
     */
    private static final MfaCodeService MFA_CODE_SERVICE = new MfaCodeService();
        /**
         * Username of the operator who enrolls in MFA.
         */
        private static final String MFA_USERNAME = "mfa-user";
        /**
         * Password of the operator who enrolls in MFA.
         */
        private static final String MFA_PASSWORD = "mfa-user";

        /**
         * Service used to create the operator when it is missing.
         */
        @Inject
        OperatorAuthService operatorAuthService;

    /**
     * Session cookie from password login before MFA enrollment is active.
     */
    private static String sessionCookie;
        /**
         * Session cookie from a login that included the second factor.
         */
        private static String mfaSessionCookie;
        /**
         * Recovery code issued during MFA enrollment.
         */
        private static String recoveryCode;
    /**
     * Pending MFA enrollment secret used to generate confirmation codes.
     */
    private static String setupKey;

        /**
         * Creates the MFA operator when it does not already exist.
         */
        @BeforeEach
        @Transactional
        void ensureOperatorExists() {
                if (Operator.findByUsername(MFA_USERNAME) == null) {
                        operatorAuthService.createOperator(MFA_USERNAME, MFA_PASSWORD);
        }
        }

    /**
     * Password login establishes a session before MFA enrollment.
     */
    @Test
    @Order(1)
    void loginEstablishesSession() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", MFA_USERNAME, "password", MFA_PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is(MFA_USERNAME))
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .response();
        sessionCookie = response.detailedCookie(SessionService.COOKIE_NAME).getValue();
    }

    /**
     * Account security reports MFA disabled before enrollment starts.
     */
    @Test
    @Order(2)
    void accountSecurityStartsDisabled() {
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("disabled"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "getAccountSecurity", Request.Method.GET, "/api/v1/account/security", response);
    }

    /**
     * Starting MFA enrollment returns a pending setup key and recovery codes.
     */
    @Test
    @Order(3)
    void startEnrollmentReturnsPendingSetup() {
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .post("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("pending"))
                .body("setupKey", notNullValue())
                .body("otpauthUri", notNullValue())
                .body("recoveryCodes.size()", is(8))
                .extract()
                .response();
        setupKey = response.jsonPath().getString("setupKey");
        recoveryCode = response.jsonPath().getList("recoveryCodes", String.class).get(0);
        OpenApiContractSupport.assertResponseConforms(
                "startAccountMfaEnrollment", Request.Method.POST, "/api/v1/account/security", response);
    }

    /**
     * Account security reports the pending MFA enrollment and its setup key.
     */
    @Test
    @Order(4)
    void accountSecurityReportsPendingEnrollment() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("pending"))
                .body("setupKey", is(setupKey));
    }

    /**
     * Confirming MFA enrollment with a current code makes the enrollment active.
     */
    @Test
    @Order(5)
    void confirmEnrollmentActivatesMfa() {
        String code = MFA_CODE_SERVICE.generateTotp(setupKey, Instant.now().getEpochSecond() / 30);

        Response response = given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("code", code))
                .when()
                .post("/api/v1/account/security/mfa/confirm")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("active"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "confirmAccountMfaEnrollment", Request.Method.POST, "/api/v1/account/security/mfa/confirm", response);
    }

    /**
     * Account security reports that MFA enrollment is active after confirmation.
     */
    @Test
    @Order(6)
    void accountSecurityReportsActiveEnrollment() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("active"));
    }

    /**
     * A recovery code, including surrounding spaces and lower case, establishes a session after MFA enrollment is active.
     */
    @Test
    @Order(7)
    void loginWithRecoveryCodeEstablishesSession() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username",
                        MFA_USERNAME,
                        "password",
                        MFA_PASSWORD,
                        "mfaCode",
                        "  " + recoveryCode.toLowerCase() + "  "))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is(MFA_USERNAME))
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .response();
        mfaSessionCookie = response.detailedCookie(SessionService.COOKIE_NAME).getValue();
        OpenApiContractSupport.assertResponseConforms("login", Request.Method.POST, "/api/v1/auth/login", response);
    }

    /**
     * A recovery code that has already been used is rejected.
     */
    @Test
    @Order(8)
    void usedRecoveryCodeIsRejectedOnReuse() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", MFA_USERNAME, "password", MFA_PASSWORD, "mfaCode", recoveryCode))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    /**
     * Password-only login is rejected once MFA enrollment is active.
     */
    @Test
    @Order(9)
    void loginWithoutMfaCodeIsRejectedAfterEnrollment() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", MFA_USERNAME, "password", MFA_PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("MFA code required"));
    }

    /**
     * Login with a wrong second factor is rejected once MFA enrollment is active.
     */
    @Test
    @Order(10)
    void loginWithWrongMfaCodeIsRejectedAfterEnrollment() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", MFA_USERNAME, "password", MFA_PASSWORD, "mfaCode", "000000"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid MFA code"));
    }

    /**
     * Login with a current second factor establishes a session after MFA enrollment is active.
     */
    @Test
    @Order(11)
    void loginWithMfaCodeEstablishesSession() {
        String code = MFA_CODE_SERVICE.generateTotp(setupKey, Instant.now().getEpochSecond() / 30);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", MFA_USERNAME, "password", MFA_PASSWORD, "mfaCode", code))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is(MFA_USERNAME))
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .response();
        mfaSessionCookie = response.detailedCookie(SessionService.COOKIE_NAME).getValue();
        OpenApiContractSupport.assertResponseConforms("login", Request.Method.POST, "/api/v1/auth/login", response);
    }

    /**
     * A session created with the second factor can read reconciliation status.
     */
    @Test
    @Order(12)
    void mfaSessionAccessesProtectedEndpoints() {
        given()
                .cookie(SessionService.COOKIE_NAME, mfaSessionCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(200)
                .body("zonesDir", notNullValue())
                .body("zoneOutcomes", notNullValue());
    }
}
