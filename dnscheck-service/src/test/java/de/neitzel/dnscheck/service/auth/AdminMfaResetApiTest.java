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
class AdminMfaResetApiTest {

    private static final String TARGET_USERNAME = "reset-target";
    private static final String TARGET_PASSWORD = "reset-target";

    private static final MfaCodeService MFA_CODE_SERVICE = new MfaCodeService();

    @Inject
    OperatorAuthService operatorAuthService;

    private static String targetCookie;
    private static String adminCookie;
    private static String setupKey;
    private static Long targetOperatorId;

    @BeforeEach
    @Transactional
    void ensureTargetOperatorExists() {
        if (Operator.findByUsername(TARGET_USERNAME) == null) {
            operatorAuthService.createOperator(TARGET_USERNAME, TARGET_PASSWORD);
        }
        targetOperatorId = Operator.findByUsername(TARGET_USERNAME).id;
    }

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
