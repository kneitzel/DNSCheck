package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeletionMarkerApiTest {

    private static final String WRITER_OWNER = "marker-writer.u";

    @InjectMock
    LiveZoneLoader liveZoneLoader;

    @Inject
    OperatorAuthService operatorAuthService;

    private static String writerCookie;
    private static String readerCookie;

    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenAnswer(invocation -> {
                    ZoneDefinition definition = invocation.getArgument(0);
                    return new LiveZone(
                            definition.zone(),
                            3600,
                            List.of(
                                    new DnsRecord("extra.example.com.", "A", "192.0.2.99", 300),
                                    new DnsRecord("bad.example.com.", "TXT", "please-remove-me", 300)));
                });
    }

    @Test
    @Order(0)
    @Transactional
    void seedCatalogAndOperators() throws Exception {
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
                  "keyFile": "marker-writer.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                keysDir.resolve("marker-writer.u/example.com.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        ensureOperator("marker-writer", "marker-writer");
        ensureOperator("marker-reader", "marker-reader");
    }

    @Test
    @Order(1)
    void writerCanAddDeletionMarkerAndViewMarkedRecord() throws Exception {
        writerCookie = login("marker-writer", "marker-writer");
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "bad", "type", "TXT", "rdataPattern", "*remove*"))
                .when()
                .post("/api/v1/zones/%s/example.com/deletion-markers".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("records.status", hasItem("marked_for_deletion"));

        String json = Files.readString(Path.of("target/test-zones/marker-writer.u/example.com.json"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("*remove*"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"delete\""));
    }

    @Test
    @Order(2)
    void readerCannotAddDeletionMarker() {
        readerCookie = login("marker-reader", "marker-reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "extra", "type", "A"))
                .when()
                .post("/api/v1/zones/%s/example.com/deletion-markers".formatted(WRITER_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(3)
    void writerCanRemoveDeletionMarker() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "bad", "type", "TXT", "rdataPattern", "*remove*"))
                .when()
                .delete("/api/v1/zones/%s/example.com/deletion-markers".formatted(WRITER_OWNER))
                .then()
                .statusCode(200);

        String json = Files.readString(Path.of("target/test-zones/marker-writer.u/example.com.json"));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("*remove*"));
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
