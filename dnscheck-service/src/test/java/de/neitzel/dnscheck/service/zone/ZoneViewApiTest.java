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

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneViewApiTest {

    private static final String ADMIN_OWNER = "admin.u";
    private static final String READER_OWNER = "reader.u";

    @InjectMock
    LiveZoneLoader liveZoneLoader;

    @Inject
    OperatorAuthService operatorAuthService;

    private static String adminCookie;
    private static String readerCookie;

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

    @Test
    @Order(1)
    void unauthenticatedZoneViewIsRejected() {
        given()
                .when()
                .get("/api/v1/zones/%s/example.com/view".formatted(ADMIN_OWNER))
                .then()
                .statusCode(401);
    }

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
