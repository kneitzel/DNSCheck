package de.neitzel.dnscheck.service.auth;

import com.atlassian.oai.validator.model.Request;
import de.neitzel.dnscheck.service.support.OpenApiContractSupport;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
    private static String sessionCookie;
    private static String setupKey;

    @Test
    @Order(1)
    void loginEstablishesSession() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is("admin"))
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
}
