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
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * Tests an operator cancelling a pending MFA enrollment.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CancelPendingMfaApiTest {

    /**
     * Username of the operator who cancels MFA enrollment.
     */
    private static final String USERNAME = "cancel-mfa-user";
    /**
     * Password of the operator who cancels MFA enrollment.
     */
    private static final String PASSWORD = "cancel-mfa-user";
    /**
     * Generator used to produce the confirmation code that activates MFA enrollment.
     */
    private static final MfaCodeService MFA_CODE_SERVICE = new MfaCodeService();

    /**
     * Service used to create the operator when it is missing.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the operator under test.
     */
    private static String sessionCookie;
    /**
     * Pending MFA enrollment secret captured when enrollment is started again.
     */
    private static String setupKey;

    /**
     * Creates the operator when it does not already exist.
     */
    @BeforeEach
    @Transactional
    void ensureOperatorExists() {
        if (Operator.findByUsername(USERNAME) == null) {
            operatorAuthService.createOperator(USERNAME, PASSWORD);
        }
    }

    /**
     * Login with the operator password establishes a session before MFA enrollment.
     */
    @Test
    @Order(1)
    void loginEstablishesSession() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", USERNAME, "password", PASSWORD))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .response();
        sessionCookie = response.detailedCookie(SessionService.COOKIE_NAME).getValue();
    }

    /**
     * Cancelling MFA enrollment while it is disabled is rejected, and the status stays disabled.
     */
    @Test
    @Order(2)
    void cancelWhileDisabledIsRejected() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .delete("/api/v1/account/security/mfa")
                .then()
                .statusCode(409);

        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("disabled"));
    }

    /**
     * Starting MFA enrollment and then cancelling it returns the operator to disabled and clears the pending setup.
     */
    @Test
    @Order(3)
    void startEnrollmentThenCancelReturnsDisabled() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .post("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("pending"))
                .body("setupKey", notNullValue())
                .body("recoveryCodes.size()", is(8));

        Response response = given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .delete("/api/v1/account/security/mfa")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("disabled"))
                .body("setupKey", nullValue())
                .body("otpauthUri", nullValue())
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "cancelAccountMfaEnrollment", Request.Method.DELETE, "/api/v1/account/security/mfa", response);

        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("disabled"))
                .body("setupKey", nullValue());
    }

    /**
     * After a cancelled MFA enrollment, the operator can start enrollment again and receive recovery codes.
     */
    @Test
    @Order(4)
    void afterCancelEnrollmentCanStartAgain() {
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .post("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("pending"))
                .body("setupKey", notNullValue())
                .body("recoveryCodes.size()", is(8))
                .extract()
                .response();
        setupKey = response.jsonPath().getString("setupKey");
    }

    /**
     * Cancelling MFA enrollment after it is active is rejected, and the status stays active.
     */
    @Test
    @Order(5)
    void cancelWhileActiveIsRejected() {
        String code = MFA_CODE_SERVICE.generateTotp(setupKey, Instant.now().getEpochSecond() / 30);
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("code", code))
                .when()
                .post("/api/v1/account/security/mfa/confirm")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("active"));

        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .delete("/api/v1/account/security/mfa")
                .then()
                .statusCode(409);

        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/account/security")
                .then()
                .statusCode(200)
                .body("mfaStatus", is("active"));
    }
}
