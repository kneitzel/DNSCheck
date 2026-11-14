package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.PlannedChange;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests creation of ad-hoc records and enforced managed records.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneRecordApiTest {

    /**
     * Owner folder of the operator who may create records.
     */
    private static final String WRITER_OWNER = "writer.u";

    /**
     * Mock live zone loader that merges the mutable live records with managed records.
     */
    @InjectMock
    LiveZoneLoader liveZoneLoader;

    /**
     * Mock updater that appends added records to the live zone fixture.
     */
    @InjectMock
    DnsUpdater dnsUpdater;

    /**
     * Service used to create the writer and reader operators.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Mutable live zone records shared by the loader and the updater stub.
     */
    private static final CopyOnWriteArrayList<DnsRecord> LIVE_RECORDS = new CopyOnWriteArrayList<>();

    /**
     * Session cookie for the operator who owns the zone.
     */
    private static String writerCookie;
    /**
     * Session cookie for an operator without the write zone right.
     */
    private static String readerCookie;

    /**
     * Stubs live zone loading and applies add changes to the live record fixture.
     */
    @BeforeEach
    void stubLiveZoneAndDnsUpdater() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenAnswer(invocation -> {
                    ZoneDefinition definition = invocation.getArgument(0);
                    List<DnsRecord> records = new ArrayList<>(LIVE_RECORDS);
                    for (var spec : definition.managedRecords()) {
                        DnsRecord managed = new DnsRecord(spec.nameFqdn(), spec.type(), spec.rdata(), 300);
                        if (records.stream().noneMatch(r -> r.key().equals(managed.key()))) {
                            records.add(managed);
                        }
                    }
                    return new LiveZone(definition.zone(), 3600, records);
                });

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PlannedChange> changes = invocation.getArgument(2);
            for (PlannedChange change : changes) {
                if (change.action() == ChangeAction.ADD) {
                    LIVE_RECORDS.add(new DnsRecord(
                            change.nameFqdn(), change.type(), change.rdata(), change.ttl()));
                }
            }
            return null;
        }).when(dnsUpdater).apply(any(ZoneDefinition.class), any(Path.class), any());
    }

    /**
     * Seeds the live zone, writes the zone definition and TSIG key, and creates the writer and reader.
     */
    @Test
    @Order(0)
    @Transactional
    void seedCatalogAndOperators() throws Exception {
        LIVE_RECORDS.clear();
        LIVE_RECORDS.addAll(List.of(
                new DnsRecord("mail.example.com.", "A", "192.0.2.10", 300),
                new DnsRecord("extra.example.com.", "A", "192.0.2.99", 300)));

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
                  "keyFile": "writer.u/example.com.key",
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
                keysDir.resolve("writer.u/example.com.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        ensureOperator("writer", "writer");
        ensureOperator("reader", "reader");
    }

    /**
     * Creating a record without a session is rejected.
     */
    @Test
    @Order(1)
    void unauthenticatedCreateIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "new",
                        "type", "TXT",
                        "rdata", "\"hello\"",
                        "enforce", false))
                .when()
                .post("/api/v1/zones/%s/example.com/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(401);
    }

    /**
     * An operator without the write zone right cannot create a record.
     */
    @Test
    @Order(2)
    void readerCannotCreateRecords() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "new",
                        "type", "TXT",
                        "rdata", "\"hello\"",
                        "enforce", false))
                .when()
                .post("/api/v1/zones/%s/example.com/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(403);
    }

    /**
     * Creating a record without enforcement adds an ad-hoc record to the live zone and leaves the zone definition unchanged.
     */
    @Test
    @Order(3)
    void adHocCreateUpdatesLiveZoneOnly() throws Exception {
        writerCookie = login("writer", "writer");
        String zoneJsonBefore = Files.readString(Path.of("target/test-zones/writer.u/example.com.json"));

        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "new",
                        "type", "TXT",
                        "rdata", "\"hello\"",
                        "enforce", false))
                .when()
                .post("/api/v1/zones/%s/example.com/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("records.find { it.name == 'new.example.com.' && it.type == 'TXT' }.status", is("ad_hoc"));

        assert Files.readString(Path.of("target/test-zones/writer.u/example.com.json")).equals(zoneJsonBefore);
        verify(dnsUpdater).apply(any(ZoneDefinition.class), any(Path.class), any());
    }

    /**
     * Creating an enforced record writes the managed record into the zone definition and shows it as enforced.
     */
    @Test
    @Order(4)
    void enforcedCreateWritesDefinitionAndUpdatesLiveZone() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "wiki",
                        "type", "A",
                        "rdata", "192.0.2.50",
                        "enforce", true))
                .when()
                .post("/api/v1/zones/%s/example.com/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("records.status", hasItem("enforced"))
                .body("records.find { it.name == 'wiki.example.com.' && it.type == 'A' }.status", is("enforced"));

        String zoneJson = Files.readString(Path.of("target/test-zones/writer.u/example.com.json"));
        assert zoneJson.contains("wiki.example.com.");
        assert zoneJson.contains("192.0.2.50");
    }

    /**
     * Creating a record in a zone that is not in the owner folder returns not found.
     */
    @Test
    @Order(5)
    void missingZoneReturnsNotFound() {
        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "ghost",
                        "type", "A",
                        "rdata", "192.0.2.1",
                        "enforce", false))
                .when()
                .post("/api/v1/zones/%s/missing.test/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(404);
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
