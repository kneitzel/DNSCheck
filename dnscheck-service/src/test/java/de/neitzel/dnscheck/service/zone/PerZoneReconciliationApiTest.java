package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerZoneReconciliationApiTest {

    private static final String WRITER_OWNER = "zone-reconcile-writer.u";

    @Inject
    OperatorAuthService operatorAuthService;

    private static String writerCookie;
    private static String readerCookie;
    private static String adminCookie;

    @Test
    @Order(0)
    @Transactional
    void seedOperators() throws IOException {
        seedFixtures();
        adminCookie = login("admin", "admin");
        ensureOperator("zone-reconcile-writer", "zone-reconcile-writer");
        ensureOperator("zone-reconcile-reader", "zone-reconcile-reader");
    }

    @Test
    @Order(1)
    void writerCanReconcileZoneWithDryRun() {
        writerCookie = login("zone-reconcile-writer", "zone-reconcile-writer");
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .queryParam("dryRun", true)
                .when()
                .post("/api/v1/zones/%s/example.com/reconcile".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("dryRun", is(true))
                .body("zoneOutcomes", hasSize(1))
                .body("zoneOutcomes[0].zone", is("zone-reconcile-writer.u/example.com"));
    }

    @Test
    @Order(2)
    void readerIsDeniedPerZoneReconcile() {
        readerCookie = login("zone-reconcile-reader", "zone-reconcile-reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .queryParam("dryRun", true)
                .when()
                .post("/api/v1/zones/%s/example.com/reconcile".formatted(WRITER_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(3)
    void dryRunOverrideAppliesWhenExplicitlyFalse() {
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .queryParam("dryRun", false)
                .when()
                .post("/api/v1/zones/%s/example.com/reconcile".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("dryRun", is(false));
    }

    @Test
    @Order(4)
    void missingZoneReturnsNotFound() {
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .queryParam("dryRun", true)
                .when()
                .post("/api/v1/zones/%s/missing.test/reconcile".formatted(WRITER_OWNER))
                .then()
                .statusCode(404);
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

    private static void seedFixtures() throws IOException {
        Path zonesDir = Path.of("target/test-zones");
        Path keysDir = Path.of("target/test-keys");
        Path writerDir = zonesDir.resolve(WRITER_OWNER);
        Files.createDirectories(writerDir);
        Files.createDirectories(keysDir.resolve(WRITER_OWNER));
        Files.writeString(
                writerDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "zone-reconcile-writer.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                keysDir.resolve("zone-reconcile-writer.u/example.com.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);
    }
}
