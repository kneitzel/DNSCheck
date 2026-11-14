package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.support.OpenApiContractSupport;
import com.atlassian.oai.validator.model.Request;
import io.quarkus.test.InjectMock;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * Tests the zone view of managed records and the live zone.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneViewApiTest {

    /**
     * Owner folder used for the admin's zone definitions.
     */
    private static final String ADMIN_OWNER = "admin.u";
    /**
     * Owner folder used for the reader's zone definition.
     */
    private static final String READER_OWNER = "reader.u";

    /**
     * Mock live zone loader that supplies an enforced record and an ad-hoc record.
     */
    @InjectMock
    LiveZoneLoader liveZoneLoader;

    /**
     * Service used to create the reader operator.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the admin operator.
     */
    private static String adminCookie;
    /**
     * Session cookie for the operator who owns the reader folder.
     */
    private static String readerCookie;

    /**
     * Stubs the live zone with one record that matches a managed record and one ad-hoc record.
     */
    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenReturn(new LiveZone(
                        "example.com",
                        3600,
                        List.of(
                                new DnsRecord("mail.example.com.", "A", "192.0.2.10", 300),
                                new DnsRecord("extra.example.com.", "A", "192.0.2.99", 300))));
    }

    /**
     * Writes zone definitions for the admin and reader owner folders and creates the reader.
     */
    @Test
    @Order(0)
    @Transactional
    void seedCatalogAndReader() throws Exception {
        Path zonesDir = Path.of("target/test-zones");
        Path keysDir = Path.of("target/test-keys");
        Path adminDir = zonesDir.resolve(ADMIN_OWNER);
        Path readerDir = zonesDir.resolve(READER_OWNER);
        Files.createDirectories(adminDir);
        Files.createDirectories(readerDir);
        Files.createDirectories(keysDir);
        Files.writeString(
                adminDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": [
                    {
                      "name": "mail",
                      "type": "A",
                      "rdata": "192.0.2.10"
                    }
                  ]
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
        Files.writeString(
                readerDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.reader.example.com",
                  "keyFile": "reader.u/example.com.key",
                  "records": [
                    {
                      "name": "mail",
                      "type": "A",
                      "rdata": "192.0.2.10"
                    }
                  ]
                }
                """);
        Files.createDirectories(keysDir.resolve("admin.u"));
        Files.writeString(
                keysDir.resolve("admin.u/example.com.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        ensureOperator("reader", "reader");
    }

    /**
     * Reading a zone view without a session is rejected.
     */
    @Test
    @Order(1)
    void unauthenticatedZoneViewIsRejected() {
        given()
                .when()
                .get("/api/v1/zones/%s/example.com/view".formatted(ADMIN_OWNER))
                .then()
                .statusCode(401);
    }

    /**
     * An admin receives a zone view that includes an enforced managed record, an ad-hoc record, and the zone default TTL.
     */
    @Test
    @Order(2)
    void adminReceivesMergedZoneView() {
        adminCookie = login("admin", "admin");
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones/%s/example.com/view".formatted(ADMIN_OWNER))
                .then()
                .body("zone", is("example.com"))
                .body("defaultTtl", is(3600))
                .body("records.status", hasItem("enforced"))
                .body("records.status", hasItem("ad_hoc"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "getZoneView",
                Request.Method.GET,
                "/api/v1/zones/%s/example.com/view".formatted(ADMIN_OWNER),
                response);
    }

    /**
     * A reader can read the zone view for a zone in that operator's owner folder.
     */
    @Test
    @Order(3)
    void readerCanViewOwnedZone() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/zones/%s/example.com/view".formatted(READER_OWNER))
                .then()
                .statusCode(200)
                .body("zone", is("example.com"));
    }

    /**
     * A reader cannot read the zone view for a zone owned by the admin.
     */
    @Test
    @Order(4)
    void readerIsDeniedUnownedZone() {
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/zones/%s/other.test/view".formatted(ADMIN_OWNER))
                .then()
                .statusCode(403);
    }

    /**
     * Reading a zone view for a zone that is not in the owner folder returns not found.
     */
    @Test
    @Order(5)
    void missingZoneReturnsNotFound() {
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones/%s/missing.test/view".formatted(ADMIN_OWNER))
                .then()
                .statusCode(404);
    }

    /**
     * A failure while loading the live zone returns a bad gateway and the loader's message.
     */
    @Test
    @Order(6)
    void liveZoneLoadFailureReturnsBadGatewayWithMessage() throws Exception {
        doThrow(new IOException("AXFR failed for zone other.test"))
                .when(liveZoneLoader)
                .load(any(ZoneDefinition.class), any(Path.class));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones/%s/other.test/view".formatted(ADMIN_OWNER))
                .then()
                .statusCode(502)
                .body("message", is("AXFR failed for zone other.test"));
    }

    /**
     * Returns the named operator, creating it when it is missing.
     *
     * @param username operator username.
     * @param password operator password.
     * @return the existing or newly created operator.
     */
    private Operator ensureOperator(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            operator = operatorAuthService.createOperator(username, password);
        }
        return operator;
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
}
