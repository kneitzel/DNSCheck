package de.neitzel.dnscheck.service.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
@TestProfile(HsqlTestProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthHsqlIT {

    private static String sessionCookie;

    @Test
    @Order(1)
    void loginEstablishesSessionOnHsql() {
        sessionCookie = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("username", is("admin"))
                .cookie(SessionService.COOKIE_NAME, notNullValue())
                .extract()
                .detailedCookie(SessionService.COOKIE_NAME)
                .getValue();
    }

    @Test
    @Order(2)
    void authenticatedRequestsAccessProtectedEndpointsOnHsql() {
        given()
                .cookie(SessionService.COOKIE_NAME, sessionCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(200)
                .body("zonesDir", notNullValue())
                .body("zoneOutcomes", notNullValue());
    }
}
