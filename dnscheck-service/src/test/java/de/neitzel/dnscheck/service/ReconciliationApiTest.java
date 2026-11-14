package de.neitzel.dnscheck.service;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.support.OpenApiContractSupport;
import com.atlassian.oai.validator.model.Request;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * Tests reconciliation status and the global reconciliation endpoint.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReconciliationApiTest {

    /**
     * Session cookie for the admin operator.
     */
    private static String adminCookie;
    /**
     * Session cookie for an operator who is not an admin.
     */
    private static String writerCookie;
    /**
     * Session cookie for the operator who owns one zone catalog entry.
     */
    private static String readerCookie;

    /**
     * Service used to create the fixture operators.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Seeds zone definitions in two owner folders and logs in as the admin operator.
     */
    @Test
    @Order(0)
    @Transactional
    void loginAsAdminAndSeedFixtures() throws Exception {
        seedReconciliationFixtures();
        adminCookie = login("admin", "admin");
        ensureOperator("reconcile-writer");
        ensureOperator("reconcile-reader");
    }

    /**
     * The health endpoint reports that the service is up.
     */
    @Test
    @Order(1)
    void healthEndpointReturnsUp() {
        Response response = given()
                .when()
                .get("/api/v1/health")
                .then()
                .body("status", is("UP"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms("getHealth", Request.Method.GET, "/api/v1/health", response);
    }

    /**
     * Reconciliation status is never run before a reconciliation run and includes the zone catalog location.
     */
    @Test
    @Order(2)
    void statusEndpointReturnsNeverRunInitially() {
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .body("status", is("never_run"))
                .body("zonesDir", notNullValue())
                .body("zoneOutcomes", notNullValue())
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "getReconciliationStatus", Request.Method.GET, "/api/v1/status", response);
    }

    /**
     * An explicit dry-run reconciliation run reports dry-run and returns zone outcomes.
     */
    @Test
    @Order(3)
    void reconcileEndpointRunsDryRun() {
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .queryParam("dryRun", true)
                .when()
                .post("/api/v1/reconcile")
                .then()
                .body("dryRun", is(true))
                .body("status", notNullValue())
                .body("zoneOutcomes", notNullValue())
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "triggerReconciliation", Request.Method.POST, "/api/v1/reconcile", response);
    }

    /**
     * A reconciliation run that omits the dry-run flag uses the configured dry-run default.
     */
    @Test
    @Order(4)
    void omittedDryRunUsesConfiguredDefault() {
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .post("/api/v1/reconcile")
                .then()
                .statusCode(200)
                .body("dryRun", is(true));
    }

    /**
     * An operator who is not an admin cannot trigger a global reconciliation run.
     */
    @Test
    @Order(5)
    void nonAdminCannotTriggerGlobalReconcile() {
        writerCookie = login("reconcile-writer", "reconcile-writer");
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .when()
                .post("/api/v1/reconcile")
                .then()
                .statusCode(403);
    }

    /**
     * Reconciliation status for a reader includes only the zone outcome for that operator's owner folder.
     */
    @Test
    @Order(6)
    void statusFiltersZoneOutcomesForReader() {
        readerCookie = login("reconcile-reader", "reconcile-reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(200)
                .body("zoneOutcomes", hasSize(1))
                .body("zoneOutcomes[0].zone", is("reconcile-reader.u/example.com"));
    }

    /**
     * A reader does not see a zone outcome for a zone catalog entry the operator does not own.
     */
    @Test
    @Order(7)
    void readerDoesNotSeeUngrantedZoneOutcomes() {
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/status")
                .then()
                .statusCode(200)
                .body("zoneOutcomes.zone", not(hasItem("admin.u/other.test")));
    }

    /**
     * The OpenAPI document is published with the DNSCheck API title and the health path.
     */
    @Test
    @Order(8)
    void openapiDocumentIsServed() {
        given()
                .when()
                .get("/api/v1/openapi.json")
                .then()
                .statusCode(200)
                .body("openapi", is("3.1.0"))
                .body("info.title", is("DNSCheck API"))
                .body("paths.'/api/v1/health'", notNullValue());
    }

    /**
     * Creates an operator whose password matches the username when that operator is missing.
     *
     * @param username operator username to ensure.
     */
    private void ensureOperator(String username) {
        if (Operator.findByUsername(username) == null) {
            operatorAuthService.createOperator(username, username);
        }
    }

    /**
     * Logs in and returns the session cookie.
     *
     * @param username operator username.
     * @param password operator password.
     * @return the session cookie value.
     */
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

    /**
     * Writes zone definitions and TSIG key files for the reader owner folder and the admin owner folder.
     */
    private static void seedReconciliationFixtures() throws IOException {
        Path zonesDir = Path.of("target/test-zones");
        Path keysDir = Path.of("target/test-keys");
        Files.createDirectories(zonesDir);
        Files.createDirectories(keysDir);
        try (Stream<Path> existingZones = Files.list(zonesDir)) {
            existingZones.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        try (Stream<Path> nested = Files.list(path)) {
                            nested.forEach(file -> {
                                try {
                                    Files.deleteIfExists(file);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        }
                        Files.deleteIfExists(path);
                    } else {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Path readerDir = zonesDir.resolve("reconcile-reader.u");
        Path adminDir = zonesDir.resolve("admin.u");
        Files.createDirectories(readerDir);
        Files.createDirectories(adminDir);
        Files.writeString(
                readerDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "reconcile-reader.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                adminDir.resolve("other.test.json"),
                """
                {
                  "zone": "other.test",
                  "nameserver": "ns1.other.test",
                  "keyFile": "admin.u/other.test.key",
                  "records": []
                }
                """);
        Files.createDirectories(keysDir.resolve("reconcile-reader.u"));
        Files.createDirectories(keysDir.resolve("admin.u"));
        Files.writeString(
                keysDir.resolve("reconcile-reader.u/example.com.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);
        Files.writeString(
                keysDir.resolve("admin.u/other.test.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);
    }
}
