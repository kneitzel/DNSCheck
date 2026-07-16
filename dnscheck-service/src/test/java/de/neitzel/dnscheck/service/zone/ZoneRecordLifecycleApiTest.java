package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.ChangeAction;
import de.neitzel.dnscheck.core.model.DnsRecord;
import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.PlannedChange;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneRecordLifecycleApiTest {

    private static final String WRITER_OWNER = "writer.u";

    @InjectMock
    LiveZoneLoader liveZoneLoader;

    @InjectMock
    DnsUpdater dnsUpdater;

    @Inject
    OperatorAuthService operatorAuthService;

    private static final CopyOnWriteArrayList<DnsRecord> LIVE_RECORDS = new CopyOnWriteArrayList<>();

    private static String writerCookie;
    private static String readerCookie;

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
                switch (change.action()) {
                    case ADD -> LIVE_RECORDS.add(new DnsRecord(
                            change.nameFqdn(), change.type(), change.rdata(), change.ttl()));
                    case DELETE -> LIVE_RECORDS.removeIf(record -> matches(change, record));
                    case UPDATE -> {
                        LIVE_RECORDS.removeIf(record -> matches(change, record));
                        LIVE_RECORDS.add(new DnsRecord(
                                change.nameFqdn(), change.type(), change.rdata(), change.ttl()));
                    }
                }
            }
            return null;
        }).when(dnsUpdater).apply(any(ZoneDefinition.class), any(Path.class), any());
    }

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

    @Test
    @Order(1)
    void readerCannotPromoteRecords() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "extra",
                        "type", "A",
                        "rdata", "192.0.2.99"))
                .when()
                .post("/api/v1/zones/%s/example.com/records/promote".formatted(WRITER_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(2)
    void promoteAddsAdHocRecordToDefinition() throws Exception {
        writerCookie = login("writer", "writer");
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "extra",
                        "type", "A",
                        "rdata", "192.0.2.99"))
                .when()
                .post("/api/v1/zones/%s/example.com/records/promote".formatted(WRITER_OWNER))
                .then()
                .body("records.find { it.name == 'extra.example.com.' && it.type == 'A' }.status", is("enforced"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "promoteZoneRecord",
                Request.Method.POST,
                "/api/v1/zones/%s/example.com/records/promote".formatted(WRITER_OWNER),
                response);

        assert Files.readString(Path.of("target/test-zones/writer.u/example.com.json")).contains("extra.example.com.");
    }

    @Test
    @Order(3)
    void demoteRemovesManagedRecordButKeepsLiveRecord() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "mail",
                        "type", "A",
                        "rdata", "192.0.2.10"))
                .when()
                .post("/api/v1/zones/%s/example.com/records/demote".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("records.find { it.name == 'mail.example.com.' && it.type == 'A' }.status", is("ad_hoc"));

        assert !Files.readString(Path.of("target/test-zones/writer.u/example.com.json")).contains("\"rdata\": \"192.0.2.10\"");
    }

    @Test
    @Order(4)
    void modifyUpdatesManagedRecordInDefinitionAndLiveZone() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "extra",
                        "type", "A",
                        "rdata", "192.0.2.99",
                        "newRdata", "192.0.2.100"))
                .when()
                .patch("/api/v1/zones/%s/example.com/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body(
                        "records.find { it.name == 'extra.example.com.' && it.type == 'A' }.rdata",
                        is("192.0.2.100"));

        assert Files.readString(Path.of("target/test-zones/writer.u/example.com.json")).contains("192.0.2.100");
    }

    @Test
    @Order(5)
    void deleteRemovesRecordFromLiveZoneAndDefinition() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, writerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "extra",
                        "type", "A",
                        "rdata", "192.0.2.100"))
                .when()
                .delete("/api/v1/zones/%s/example.com/records".formatted(WRITER_OWNER))
                .then()
                .statusCode(200)
                .body("records.name", not(hasItem("extra.example.com.")));

        assert !Files.readString(Path.of("target/test-zones/writer.u/example.com.json")).contains("192.0.2.100");
        assert LIVE_RECORDS.stream().noneMatch(record -> record.nameFqdn().equals("extra.example.com."));
    }

    private static boolean matches(PlannedChange change, DnsRecord record) {
        return record.nameFqdn().equalsIgnoreCase(change.nameFqdn())
                && record.type().equalsIgnoreCase(change.type())
                && Objects.equals(record.rdata(), change.rdata());
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
