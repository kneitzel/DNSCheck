package de.neitzel.dnscheck.service.zone;

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
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class ScopedZoneCatalogApiTest {

    private static final String PERSONAL_OWNER = "scoped-owner.u";
    private static final String GROUP_NAME = "scoped-zones-group";
    private static final String GROUP_OWNER = "scoped-zones-group.g";

    @Inject
    OperatorAuthService operatorAuthService;

    private long groupId;

    @BeforeEach
    void prepareZonesDir() throws Exception {
        Path zonesDir = Path.of("target/test-zones");
        Files.createDirectories(zonesDir);
        try (var paths = Files.list(zonesDir)) {
            paths.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        try (var nested = Files.list(path)) {
                            nested.forEach(file -> {
                                try {
                                    Files.deleteIfExists(file);
                                } catch (Exception ignored) {
                                }
                            });
                        }
                        Files.deleteIfExists(path);
                    } else {
                        Files.deleteIfExists(path);
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    @BeforeEach
    @Transactional
    void seedOperatorsAndGroup() {
        Operator owner = ensureOperator("scoped-owner", "scoped-owner");
        if (GlobalGrant.count("operator = ?1 and kind = ?2", owner, GlobalRightKind.ZONE_CREATE) == 0) {
            operatorAuthService.grantGlobalRight(owner, GlobalRightKind.ZONE_CREATE);
        }
        Operator member = ensureOperator("scoped-member", "scoped-member");
        ensureOperator("scoped-outsider", "scoped-outsider");

        Group group = Group.findByName(GROUP_NAME);
        if (group == null) {
            group = new Group();
            group.name = GROUP_NAME;
            group.persist();
        }
        Operator admin = Operator.findByUsername("admin");
        if (!group.members.contains(member)) {
            group.members.add(member);
            member.groups.add(group);
        }
        if (admin != null && !group.members.contains(admin)) {
            group.members.add(admin);
            admin.groups.add(group);
        }
        groupId = group.id;
    }

    @Test
    void personalZoneCreateInOwnFolder() throws Exception {
        String ownerCookie = login("scoped-owner", "scoped-owner");

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", PERSONAL_OWNER,
                        "zone", "personal.test",
                        "nameserver", "ns1.personal.test",
                        "keyFile", "scoped-owner.u/personal.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201)
                .body("zone", is("personal.test"))
                .body("ownerFolder", is(PERSONAL_OWNER))
                .body("filename", is("scoped-owner.u/personal.test.json"));

        assert Files.isRegularFile(Path.of("target/test-zones/scoped-owner.u/personal.test.json"));
    }

    @Test
    void adminCanCreateGroupZoneInMemberFolder() throws Exception {
        String adminCookie = login("admin", "admin");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", GROUP_OWNER,
                        "zone", "shared.test",
                        "nameserver", "ns1.shared.test",
                        "keyFile", "scoped-zones-group.g/shared.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201)
                .body("zone", is("shared.test"))
                .body("ownerFolder", is(GROUP_OWNER));

        assert Files.isRegularFile(Path.of("target/test-zones/scoped-zones-group.g/shared.test.json"));
    }

    @Test
    void listShowsOwnerFolder() {
        String ownerCookie = login("scoped-owner", "scoped-owner");
        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", PERSONAL_OWNER,
                        "zone", "listed.test",
                        "nameserver", "ns1.listed.test",
                        "keyFile", "scoped-owner.u/listed.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.find { it.zone == 'listed.test' }.ownerFolder", is(PERSONAL_OWNER));
    }

    @Test
    void nonMemberCannotReadGroupZone() {
        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", GROUP_OWNER,
                        "zone", "private.test",
                        "nameserver", "ns1.private.test",
                        "keyFile", "scoped-zones-group.g/private.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        String outsiderCookie = login("scoped-outsider", "scoped-outsider");
        given()
                .cookie(SessionService.COOKIE_NAME, outsiderCookie)
                .when()
                .get("/api/v1/zones/%s/private.test/view".formatted(GROUP_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    void memberCanReconcileGroupZoneEntry() throws Exception {
        Path keysDir = Path.of("target/test-keys");
        Files.createDirectories(keysDir.resolve(GROUP_OWNER));
        Files.writeString(
                keysDir.resolve("scoped-zones-group.g/reconcile.test.key"),
                """
                key "dnscheck-key" {
                    algorithm hmac-sha256;
                    secret "c2VjcmV0";
                };
                """);

        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", GROUP_OWNER,
                        "zone", "reconcile.test",
                        "nameserver", "ns1.reconcile.test",
                        "keyFile", "scoped-zones-group.g/reconcile.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        String memberCookie = login("scoped-member", "scoped-member");
        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .queryParam("dryRun", true)
                .when()
                .post("/api/v1/zones/%s/reconcile.test/reconcile".formatted(GROUP_OWNER))
                .then()
                .statusCode(200)
                .body("dryRun", is(true))
                .body("zoneOutcomes", hasSize(1))
                .body("zoneOutcomes[0].zone", is("scoped-zones-group.g/reconcile.test"));
    }

    @Test
    void groupMemberSeesGroupZoneInCatalog() {
        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", GROUP_OWNER,
                        "zone", "visible.test",
                        "nameserver", "ns1.visible.test",
                        "keyFile", "scoped-zones-group.g/visible.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201);

        String memberCookie = login("scoped-member", "scoped-member");
        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.ownerFolder", hasItem(GROUP_OWNER))
                .body("entries.zone", hasItem("visible.test"));
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
