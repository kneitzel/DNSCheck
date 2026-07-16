package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.auth.entity.ZoneRightKind;
import de.neitzel.dnscheck.service.zone.LiveZoneLoader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class SupplementalZoneGrantsApiTest {

    @InjectMock
    LiveZoneLoader liveZoneLoader;

    @Inject
    OperatorAuthService operatorAuthService;

    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenReturn(new LiveZone("shared.example", 3600, List.of()));
    }

    @BeforeEach
    @Transactional
    void seedOperatorsAndZones() throws Exception {
        ensureOperator("grant-owner", "grant-owner");
        ensureOperator("grant-collab", "grant-collab");
        ensureOperator("grant-other", "grant-other");
        deleteGrantsFor("grant-collab");

        Path zonesDir = Path.of("target/test-zones");
        Path keysDir = Path.of("target/test-keys");
        Path ownerDir = zonesDir.resolve("grant-owner.u");
        Path otherDir = zonesDir.resolve("grant-other.u");
        Files.createDirectories(ownerDir);
        Files.createDirectories(otherDir);
        Files.createDirectories(keysDir.resolve("grant-owner.u"));
        Files.createDirectories(keysDir.resolve("grant-other.u"));
        writeZone(ownerDir.resolve("shared.example.json"), "shared.example", "grant-owner.u");
        writeZone(otherDir.resolve("shared.example.json"), "shared.example", "grant-other.u");
        writeKey(keysDir.resolve("grant-owner.u/shared.example.key"));
        writeKey(keysDir.resolve("grant-other.u/shared.example.key"));
    }

    private void deleteGrantsFor(String username) {
        Operator operator = Operator.findByUsername(username);
        if (operator != null) {
            ZoneGrant.delete("operator", operator);
        }
    }

    @Test
    void collaboratorWithSupplementalReadCanViewOwnersPersonalZone() {
        String adminCookie = login("admin", "admin");
        Operator collab = Operator.findByUsername("grant-collab");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "ownerFolder", "grant-owner.u",
                        "zoneName", "shared.example",
                        "kind", "read",
                        "operatorId", collab.id))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(200)
                .body("ownerFolder", is("grant-owner.u"))
                .body("zoneName", is("shared.example"));

        String collabCookie = login("grant-collab", "grant-collab");
        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .when()
                .get("/api/v1/zones/grant-owner.u/shared.example/view")
                .then()
                .statusCode(200)
                .body("zone", is("shared.example"));

        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.findAll { it.ownerFolder == 'grant-owner.u' && it.zone == 'shared.example' }.size()", is(1));
    }

    @Test
    void supplementalGrantDoesNotApplyToSameDnsNameUnderDifferentOwner() {
        String adminCookie = login("admin", "admin");
        Operator collab = Operator.findByUsername("grant-collab");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "ownerFolder", "grant-owner.u",
                        "zoneName", "shared.example",
                        "kind", "read",
                        "operatorId", collab.id))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(200);

        String collabCookie = login("grant-collab", "grant-collab");
        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .when()
                .get("/api/v1/zones/grant-other.u/shared.example/view")
                .then()
                .statusCode(403);

        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body(
                        "entries.findAll { it.ownerFolder == 'grant-other.u' && it.zone == 'shared.example' }",
                        empty());
    }

    @Test
    void collaboratorWithSupplementalWriteCanReconcile() {
        String adminCookie = login("admin", "admin");
        Operator collab = Operator.findByUsername("grant-collab");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "ownerFolder", "grant-owner.u",
                        "zoneName", "shared.example",
                        "kind", "write",
                        "operatorId", collab.id))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(200);

        String collabCookie = login("grant-collab", "grant-collab");
        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .queryParam("dryRun", true)
                .when()
                .post("/api/v1/zones/grant-owner.u/shared.example/reconcile")
                .then()
                .statusCode(200)
                .body("zoneOutcomes.zone", hasItem("grant-owner.u/shared.example"));
    }

    @Test
    void createZoneGrantRequiresOwnerFolder() {
        String adminCookie = login("admin", "admin");
        Operator collab = Operator.findByUsername("grant-collab");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("zoneName", "shared.example", "kind", "read", "operatorId", collab.id))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(400);
    }

    private Operator ensureOperator(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            operator = operatorAuthService.createOperator(username, password);
        }
        return operator;
    }

    private static void writeZone(Path path, String zone, String ownerFolder) throws Exception {
        Files.writeString(
                path,
                """
                {
                  "zone": "%s",
                  "nameserver": "ns1.%s",
                  "keyFile": "%s/%s.key",
                  "records": []
                }
                """.formatted(zone, zone, ownerFolder, zone));
    }

    private static void writeKey(Path path) throws Exception {
        Files.writeString(
                path,
                """
                key "shared.example" {
                  algorithm hmac-sha256;
                  secret "test-secret";
                };
                """);
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
