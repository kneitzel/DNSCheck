package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.service.support.OpenApiContractSupport;
import com.atlassian.oai.validator.model.Request;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

/**
 * Tests session login, logout, and access to protected endpoints.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthApiTest {

    /**
     * Session cookie captured after a successful login.
     */
    private static String sessionCookie;

    /**
     * A request without a session is rejected when it calls a protected endpoint.
     */
    @Test
    @Order(1)
    void protectedEndpointRejectsUnauthenticatedRequests() {
        given()
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(401);
    }

    /**
     * Login with a wrong password is rejected.
     */
    @Test
    @Order(2)
    void loginRejectsInvalidCredentials() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "wrong"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    /**
     * Login with the bootstrap operator password establishes a session cookie.
     */
    @Test
    @Order(3)
    void loginEstablishesSession() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .body("username", is("admin"))
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms("login", Request.Method.POST, "/api/v1/auth/login", response);
        sessionCookie = response.detailedCookie(SessionService.COOKIE_NAME).getValue();
    }

    /**
     * A request that sends the session cookie can read reconciliation status.
     */
    @Test
    @Order(4)
    void authenticatedRequestsAccessProtectedEndpoints() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(200)
                .body("zonesDir", notNullValue())
                .body("zoneOutcomes", notNullValue());
    }

    /**
     * Logout completes, and the same session cookie is then rejected by a protected endpoint.
     */
    @Test
    @Order(5)
    void logoutClearsSession() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .post("/api/v1/auth/logout")
                .then()
                .statusCode(204);

        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(401);
    }

    /**
     * The health endpoint stays available without a session.
     */
    @Test
    @Order(6)
    void healthRemainsPublic() {
        given()
                .when()
                .get("/api/v1/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    /**
     * The OpenAPI document names the session cookie and requires it on status but not on login.
     */
    @Test
    @Order(7)
    void openapiDescribesSessionSecurity() {
        given()
                .when()
                .get("/api/v1/openapi.json")
                .then()
                .statusCode(200)
                .body("components.securitySchemes.session.name", is(SessionService.COOKIE_NAME))
                .body("paths.'/api/v1/status'.get.security[0].session", notNullValue())
                .body("paths.'/api/v1/auth/login'.post.security", nullValue());
    }
}
