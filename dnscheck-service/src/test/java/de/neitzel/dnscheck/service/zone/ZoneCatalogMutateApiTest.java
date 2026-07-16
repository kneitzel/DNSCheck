package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneCatalogMutateApiTest {

    private static final String CREATOR_OWNER = "creator.u";

    @Inject
    OperatorAuthService operatorAuthService;

    private static String creatorCookie;
    private static String readerCookie;

    @Test
    @Order(0)
    @Transactional
    void seedOperators() throws Exception {
        Files.createDirectories(Path.of("target/test-zones"));

        Operator creator = ensureOperator("creator", "creator");
        if (GlobalGrant.count("operator = ?1 and kind = ?2", creator, GlobalRightKind.ZONE_CREATE) == 0) {
            operatorAuthService.grantGlobalRight(creator, GlobalRightKind.ZONE_CREATE);
        }
        ensureOperator("reader", "reader");
    }

    @Test
    @Order(1)
    void createReturns201() {
        creatorCookie = login("creator", "creator");
        given()
                .cookie(SessionService.COOKIE_NAME, creatorCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", CREATOR_OWNER,
                        "zone", "mutate.test",
                        "nameserver", "ns1.mutate.test",
                        "keyFile", "creator.u/mutate.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201)
                .body("zone", is("mutate.test"))
                .body("ownerFolder", is(CREATOR_OWNER))
                .body("nameserver", is("ns1.mutate.test"));
    }

    @Test
    @Order(2)
    void creatorCanPatchNameserver() {
        given()
                .cookie(SessionService.COOKIE_NAME, creatorCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("nameserver", "ns2.mutate.test"))
                .when()
                .patch("/api/v1/zones/%s/mutate.test".formatted(CREATOR_OWNER))
                .then()
                .statusCode(200)
                .body("zone", is("mutate.test"))
                .body("ownerFolder", is(CREATOR_OWNER))
                .body("nameserver", is("ns2.mutate.test"));
    }

    @Test
    @Order(3)
    void readerCannotPatchZoneDefinition() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("nameserver", "ns3.mutate.test"))
                .when()
                .patch("/api/v1/zones/%s/mutate.test".formatted(CREATOR_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(4)
    void patchMissingZoneReturns404() {
        given()
                .cookie(SessionService.COOKIE_NAME, creatorCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("nameserver", "ns1.missing.test"))
                .when()
                .patch("/api/v1/zones/%s/missing.test".formatted(CREATOR_OWNER))
                .then()
                .statusCode(404);
    }

    @Test
    @Order(5)
    void readerCannotDeleteZoneDefinition() {
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .delete("/api/v1/zones/%s/mutate.test".formatted(CREATOR_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(6)
    void creatorCanDeleteZoneDefinition() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, creatorCookie)
                .when()
                .delete("/api/v1/zones/%s/mutate.test".formatted(CREATOR_OWNER))
                .then()
                .statusCode(204);

        assertFalse(Files.isRegularFile(Path.of("target/test-zones/%s/mutate.test.json".formatted(CREATOR_OWNER))));

        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.zone", not(hasItem("mutate.test")));
    }

    private Operator ensureOperator(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            operator = operatorAuthService.createOperator(username, password);
        }
        return operator;
    }

    private static String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .detailedCookie(SessionService.COOKIE_NAME)
                .getValue();
    }
}
