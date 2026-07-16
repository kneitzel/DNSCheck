package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasKey;

@QuarkusTest
class ZoneKeyPickerApiTest {

    private static final String VALID_KEY = """
            key "picker.example" {
              algorithm hmac-sha256;
              secret "picker-secret";
            };
            """;

    @Inject
    OperatorAuthService operatorAuthService;

    private long groupId;

    @BeforeEach
    void cleanDirs() throws Exception {
        cleanDir(Path.of("target/test-keys"));
        cleanDir(Path.of("target/test-zones"));
        Files.createDirectories(Path.of("target/test-keys"));
        Files.createDirectories(Path.of("target/test-zones"));
    }

    @BeforeEach
    @Transactional
    void seedOperators() {
        Operator owner = ensureOperator("picker-owner", "picker-owner");
        if (GlobalGrant.count("operator = ?1 and kind = ?2", owner, GlobalRightKind.ZONE_CREATE) == 0) {
            operatorAuthService.grantGlobalRight(owner, GlobalRightKind.ZONE_CREATE);
        }
        Operator member = ensureOperator("picker-member", "picker-member");
        if (GlobalGrant.count("operator = ?1 and kind = ?2", member, GlobalRightKind.ZONE_CREATE) == 0) {
            operatorAuthService.grantGlobalRight(member, GlobalRightKind.ZONE_CREATE);
        }
        Group group = Group.findByName("picker-team");
        if (group == null) {
            group = new Group();
            group.name = "picker-team";
            group.persist();
        }
        if (!group.members.contains(member)) {
            group.members.add(member);
            member.groups.add(group);
        }
        groupId = group.id;
        ensureOperator("picker-outsider", "picker-outsider");
    }

    @Test
    void selectableKeysListOwnAndMemberGroupKeysWithoutDownloadUrls() {
        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "shared.picker.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/admin/groups/%d/keys".formatted(groupId))
                .then()
                .statusCode(201);

        String memberCookie = login("picker-member", "picker-member");
        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "personal.picker.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .when()
                .get("/api/v1/keys/selectable")
                .then()
                .statusCode(200)
                .body("keys.displayLabel", hasItem("personal.picker.key"))
                .body("keys.displayLabel", hasItem("picker-team/shared.picker.key"))
                .body("keys.scopedPath", hasItem("picker-member.u/personal.picker.key"))
                .body("keys.scopedPath", hasItem("picker-team.g/shared.picker.key"))
                .body("$", not(hasKey("downloadUrl")))
                .body("keys[0]", not(hasKey("downloadUrl")));

        String outsiderCookie = login("picker-outsider", "picker-outsider");
        given()
                .cookie(SessionService.COOKIE_NAME, outsiderCookie)
                .when()
                .get("/api/v1/keys/selectable")
                .then()
                .statusCode(200)
                .body("keys.displayLabel", not(hasItem("picker-team/shared.picker.key")));
    }

    @Test
    void missingKeySummaryIsScopedToReadableZones() throws Exception {
        String ownerCookie = login("picker-owner", "picker-owner");
        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", "picker-owner.u",
                        "zone", "missing.owner",
                        "nameserver", "ns1.missing.owner",
                        "keyFile", "picker-owner.u/absent.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        String memberCookie = login("picker-member", "picker-member");
        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", "picker-team.g",
                        "zone", "missing.group",
                        "nameserver", "ns1.missing.group",
                        "keyFile", "picker-team.g/absent.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .when()
                .get("/api/v1/keys/missing")
                .then()
                .statusCode(200)
                .body("missing.scopedKeyPath", hasItem("picker-owner.u/absent.key"))
                .body("missing.scopedKeyPath", not(hasItem("picker-team.g/absent.key")))
                .body("missing.find { it.scopedKeyPath == 'picker-owner.u/absent.key' }.affectedEntryCount", is(1));

        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/keys/missing")
                .then()
                .statusCode(200)
                .body("missing.scopedKeyPath", hasItem("picker-owner.u/absent.key"))
                .body("missing.scopedKeyPath", hasItem("picker-team.g/absent.key"));
    }

    @Test
    void deleteKeyBlockedWhenReferencedAndZoneCreateStoresScopedKeyFile() throws Exception {
        String ownerCookie = login("picker-owner", "picker-owner");
        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "inuse.picker.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", "picker-owner.u",
                        "zone", "inuse.zone",
                        "nameserver", "ns1.inuse.zone",
                        "keyFile", "picker-owner.u/inuse.picker.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201)
                .body("ownerFolder", is("picker-owner.u"))
                .body("zone", is("inuse.zone"));

        String zoneJson = Files.readString(Path.of("target/test-zones/picker-owner.u/inuse.zone.json"));
        org.junit.jupiter.api.Assertions.assertTrue(zoneJson.contains("picker-owner.u/inuse.picker.key"));

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .when()
                .delete("/api/v1/account/keys/inuse.picker.key")
                .then()
                .statusCode(409)
                .body("referencedByCount", is(1))
                .body("message", org.hamcrest.Matchers.containsString("referenced"));
    }

    @Test
    void zoneViewAndReconcileHardErrorWhenKeyMissing() {
        String ownerCookie = login("picker-owner", "picker-owner");
        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", "picker-owner.u",
                        "zone", "broken.zone",
                        "nameserver", "ns1.broken.zone",
                        "keyFile", "picker-owner.u/gone.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .when()
                .get("/api/v1/zones/picker-owner.u/broken.zone/view")
                .then()
                .statusCode(409)
                .body("scopedKeyPath", is("picker-owner.u/gone.key"))
                .body("message", org.hamcrest.Matchers.containsString("missing"));

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .when()
                .post("/api/v1/zones/picker-owner.u/broken.zone/reconcile")
                .then()
                .statusCode(409)
                .body("scopedKeyPath", is("picker-owner.u/gone.key"));
    }

    private static void cleanDir(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private Operator ensureOperator(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            return operatorAuthService.createOperator(username, password);
        }
        return operator;
    }

    private String login(String username, String password) {
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
