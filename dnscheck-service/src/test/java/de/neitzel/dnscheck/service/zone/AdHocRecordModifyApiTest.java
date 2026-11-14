package de.neitzel.dnscheck.service.zone;

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
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Tests modification of an ad-hoc record without changing enforcement.
 */
@QuarkusTest
class AdHocRecordModifyApiTest {

    /**
     * Owner folder that holds the zone under test.
     */
    private static final String OWNER = "adhoc-edit.u";
    /**
     * Zone name of the catalog entry under test.
     */
    private static final String ZONE = "adhoc.example";

    /**
     * Mock live zone loader that merges the mutable live records with managed records.
     */
    @InjectMock
    LiveZoneLoader liveZoneLoader;

    /**
     * Mock updater that applies add, delete, and update changes to the live record fixture.
     */
    @InjectMock
    DnsUpdater dnsUpdater;

    /**
     * Service used to create the writer and reader operators.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Mutable live zone records for the ad-hoc record and the managed record.
     */
    private final CopyOnWriteArrayList<DnsRecord> liveRecords = new CopyOnWriteArrayList<>();

    /**
     * Stubs live zone loading and applies planned changes to the live record fixture.
     */
    @BeforeEach
    void stubLiveAndDns() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenAnswer(invocation -> {
                    ZoneDefinition definition = invocation.getArgument(0);
                    List<DnsRecord> records = new ArrayList<>(liveRecords);
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
                switch (change.action()) {
                    case ADD -> liveRecords.add(new DnsRecord(
                            change.nameFqdn(), change.type(), change.rdata(),
                            change.ttl() != null ? change.ttl() : 300L));
                    case DELETE -> liveRecords.removeIf(record -> matches(change, record));
                    case UPDATE -> {
                        liveRecords.removeIf(record -> matches(change, record));
                        liveRecords.add(new DnsRecord(
                                change.nameFqdn(),
                                change.type(),
                                change.rdata(),
                                change.ttl() != null ? change.ttl() : 300L));
                    }
                }
            }
            return null;
        }).when(dnsUpdater).apply(any(ZoneDefinition.class), any(Path.class), any());
    }

    /**
     * Seeds an ad-hoc record and a managed record, writes the zone definition and TSIG key, and creates the operators.
     */
    @BeforeEach
    @Transactional
    void seed() throws Exception {
        liveRecords.clear();
        liveRecords.add(new DnsRecord("wiki.adhoc.example.", "A", "192.0.2.50", 300));
        liveRecords.add(new DnsRecord("mail.adhoc.example.", "A", "192.0.2.10", 300));

        Path zonesDir = Path.of("target/test-zones");
        Path keysDir = Path.of("target/test-keys");
        Path ownerDir = zonesDir.resolve(OWNER);
        Files.createDirectories(ownerDir);
        Files.createDirectories(keysDir.resolve(OWNER));
        Files.writeString(
                ownerDir.resolve(ZONE + ".json"),
                """
                {
                  "zone": "%s",
                  "nameserver": "ns1.%s",
                  "keyFile": "%s/%s.key",
                  "records": [
                    {
                      "name": "mail",
                      "type": "A",
                      "rdata": "192.0.2.10"
                    }
                  ]
                }
                """.formatted(ZONE, ZONE, OWNER, ZONE));
        Files.writeString(
                keysDir.resolve(OWNER + "/" + ZONE + ".key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        ensureOperator("adhoc-edit", "adhoc-edit");
        ensureOperator("adhoc-reader", "adhoc-reader");
    }

    /**
     * The zone owner can change an ad-hoc record's rdata, and the zone definition stays free of that record.
     */
    @Test
    void writerCanModifyAdHocRecordWithoutUpdatingDefinition() throws Exception {
        String cookie = login("adhoc-edit", "adhoc-edit");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "wiki",
                        "type", "A",
                        "rdata", "192.0.2.50",
                        "newRdata", "192.0.2.51"))
                .when()
                .patch("/api/v1/zones/%s/%s/records".formatted(OWNER, ZONE))
                .then()
                .statusCode(200)
                .body(
                        "records.find { it.name == 'wiki.adhoc.example.' && it.type == 'A' }.status",
                        is("ad_hoc"))
                .body(
                        "records.find { it.name == 'wiki.adhoc.example.' && it.type == 'A' }.rdata",
                        is("192.0.2.51"));

        String json = Files.readString(Path.of("target/test-zones/%s/%s.json".formatted(OWNER, ZONE)));
        assertFalse(json.contains("wiki"));
        assertFalse(json.contains("192.0.2.51"));
    }

    /**
     * The zone owner can change only the TTL of an ad-hoc record, and the record stays ad-hoc.
     */
    @Test
    void writerCanChangeAdHocTtlOnly() {
        String cookie = login("adhoc-edit", "adhoc-edit");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "wiki",
                        "type", "A",
                        "rdata", "192.0.2.50",
                        "newTtl", 600))
                .when()
                .patch("/api/v1/zones/%s/%s/records".formatted(OWNER, ZONE))
                .then()
                .statusCode(200)
                .body(
                        "records.find { it.name == 'wiki.adhoc.example.' && it.type == 'A' }.status",
                        is("ad_hoc"))
                .body(
                        "records.find { it.name == 'wiki.adhoc.example.' && it.type == 'A' }.ttl",
                        is(600));
    }

    /**
     * Changing a managed record's rdata writes the new rdata into the zone definition.
     */
    @Test
    void managedModifyStillUpdatesDefinition() throws Exception {
        String cookie = login("adhoc-edit", "adhoc-edit");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "mail",
                        "type", "A",
                        "rdata", "192.0.2.10",
                        "newRdata", "192.0.2.11"))
                .when()
                .patch("/api/v1/zones/%s/%s/records".formatted(OWNER, ZONE))
                .then()
                .statusCode(200)
                .body(
                        "records.find { it.name == 'mail.adhoc.example.' && it.type == 'A' }.rdata",
                        is("192.0.2.11"));

        String json = Files.readString(Path.of("target/test-zones/%s/%s.json".formatted(OWNER, ZONE)));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("192.0.2.11"));
    }

    /**
     * An operator without the write zone right cannot modify an ad-hoc record.
     */
    @Test
    void readerCannotModifyAdHocRecord() {
        String cookie = login("adhoc-reader", "adhoc-reader");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "wiki",
                        "type", "A",
                        "rdata", "192.0.2.50",
                        "newRdata", "192.0.2.51"))
                .when()
                .patch("/api/v1/zones/%s/%s/records".formatted(OWNER, ZONE))
                .then()
                .statusCode(403);
    }

    /**
     * Returns whether a live record has the same name, type, and rdata as a planned change.
     *
     * @param change planned change to match.
     * @param record live record to compare.
     * @return true when the name, type, and rdata match.
     */
    private static boolean matches(PlannedChange change, DnsRecord record) {
        return record.nameFqdn().equalsIgnoreCase(change.nameFqdn())
                && record.type().equalsIgnoreCase(change.type())
                && Objects.equals(record.rdata(), change.rdata());
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
            return operatorAuthService.createOperator(username, password);
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
                .cookie(SessionService.COOKIE_NAME);
    }
}
