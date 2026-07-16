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

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountMfaApiTest {

    private static final MfaCodeService MFA_CODE_SERVICE = new MfaCodeService();
        private static final String MFA_USERNAME = "mfa-user";
        private static final String MFA_PASSWORD = "mfa-user";

        @Inject
        OperatorAuthService operatorAuthService;

    private static String sessionCookie;
        private static String mfaSessionCookie;
        private static String recoveryCode;
    private static String setupKey;

        @BeforeEach
        @Transactional
        void ensureOperatorExists() {
                if (Operator.findByUsername(MFA_USERNAME) == null) {
                        operatorAuthService.createOperator(MFA_USERNAME, MFA_PASSWORD);
        }
        }

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
