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

/**
 * Tests deletion markers on a zone catalog entry.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeletionMarkerApiTest {

    /**
     * Owner folder of the operator who may edit deletion markers.
     */
    private static final String WRITER_OWNER = "marker-writer.u";

    /**
     * Mock live zone loader that returns an ad-hoc record and a record the deletion marker can match.
     */
    @InjectMock
    LiveZoneLoader liveZoneLoader;

    /**
     * Service used to create the fixture operators.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the operator who owns the zone.
     */
    private static String writerCookie;
    /**
     * Session cookie for an operator who does not own the zone.
     */
    private static String readerCookie;

    /**
     * Stubs the live zone with an ad-hoc address and a TXT record that a deletion marker can match.
     */
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

    /**
     * Writes the zone definition and TSIG key, and creates the writer and reader operators.
     */
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

    /**
     * The zone owner can add a deletion marker, the zone view marks the matching live record, and the marker is stored in the zone definition.
     */
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

    /**
     * An operator without the write zone right cannot add a deletion marker.
     */
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

    /**
     * The zone owner can remove a deletion marker from the zone definition.
     */
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
