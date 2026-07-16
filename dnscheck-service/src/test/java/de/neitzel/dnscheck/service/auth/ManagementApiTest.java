package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.Group;
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class ManagementApiTest {

    @InjectMock
    LiveZoneLoader liveZoneLoader;

    @Inject
    OperatorAuthService operatorAuthService;

    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenReturn(new LiveZone("example.com", 3600, List.of()));
        Path zonesDir = Path.of("target/test-zones");
        Path adminDir = zonesDir.resolve("admin.u");
        Files.createDirectories(adminDir);
        Files.writeString(
                adminDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": []
                }
                """);
    }

    @BeforeEach
    @Transactional
    void cleanupManagementFixtures() {
        deleteOperatorIfExists("pwd-user");
        deleteOperatorIfExists("reset-user");
        deleteOperatorIfExists("rights-user");
        Group group = Group.findByName("rights-test-group");
        if (group != null) {
            GlobalGrant.delete("group", group);
            ZoneGrant.delete("group", group);
            group.delete();
        }
    }

    private void deleteOperatorIfExists(String username) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            return;
        }
        GlobalGrant.delete("operator", operator);
        ZoneGrant.delete("operator", operator);
        for (Group group : List.copyOf(operator.groups)) {
            group.members.remove(operator);
            operator.groups.remove(group);
        }
        operator.delete();
    }

    private long ensureOperator(String adminCookie, String username, String password) {
        Operator existing = Operator.findByUsername(username);
        if (existing != null) {
            given()
                    .cookie(SessionService.COOKIE_NAME, adminCookie)
                    .contentType(ContentType.JSON)
                    .body(Map.of("password", password, "enabled", true))
                    .when()
                    .patch("/api/v1/admin/operators/" + existing.id)
                    .then()
                    .statusCode(200);
            return existing.id;
        }
        return ((Number) given()
                        .cookie(SessionService.COOKIE_NAME, adminCookie)
                        .contentType(ContentType.JSON)
                        .body(Map.of("username", username, "password", password))
                        .post("/api/v1/admin/operators")
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("id"))
                .longValue();
    }

    @Test
    @Transactional
    void operatorChangesOwnPassword() {
        String adminCookie = login("admin", "admin");
        ensureOperator(adminCookie, "pwd-user", "pwd-user");

        String cookie = login("pwd-user", "pwd-user");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", "pwd-user", "newPassword", "pwd-user-new"))
                .when()
                .post("/api/v1/auth/change-password")
                .then()
                .statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "pwd-user", "password", "pwd-user-new"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        String cookie = login("admin", "admin");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", "wrong", "newPassword", "new-pass"))
                .when()
                .post("/api/v1/auth/change-password")
                .then()
                .statusCode(401);
    }

    @Test
    void myRightsIncludesInheritedGroupGrant() {
        String adminCookie = login("admin", "admin");
        Long groupId = ((Number) given()
                        .cookie(SessionService.COOKIE_NAME, adminCookie)
                        .contentType(ContentType.JSON)
                        .body(Map.of("name", "rights-test-group"))
                        .post("/api/v1/admin/groups")
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("id"))
                .longValue();

        Long operatorId = ((Number) given()
                        .cookie(SessionService.COOKIE_NAME, adminCookie)
                        .contentType(ContentType.JSON)
                        .body(Map.of("username", "rights-user", "password", "rights-user"))
                        .post("/api/v1/admin/operators")
                        .then()
                        .statusCode(200)
                        .extract()
                        .path("id"))
                .longValue();

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .put("/api/v1/admin/groups/%d/members/%d".formatted(groupId, operatorId))
                .then()
                .statusCode(200);

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "kind", "read",
                        "groupId", groupId,
                        "ownerFolder", "admin.u",
                        "zoneName", "example.com"))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(200);

        String userCookie = login("rights-user", "rights-user");
        given()
                .cookie(SessionService.COOKIE_NAME, userCookie)
                .when()
                .get("/api/v1/auth/me/rights")
                .then()
                .statusCode(200)
                .body(
                        "zoneRights.find { it.ownerFolder == 'admin.u' && it.zoneName == 'example.com' && it.kind == 'read' }.sources.type",
                        hasItem("group"))
                .body(
                        "zoneRights.find { it.ownerFolder == 'admin.u' && it.zoneName == 'example.com' && it.kind == 'read' }.sources.groupName",
                        hasItem("rights-test-group"));
    }

    @Test
    void operatorRightsShowsDirectAndGroupSources() {
        String adminCookie = login("admin", "admin");
        ensureReader();
        Operator reader = Operator.findByUsername("reader");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/admin/operators/" + reader.id + "/rights")
                .then()
                .statusCode(200)
                .body(
                        "zoneRights.find { it.ownerFolder == 'admin.u' && it.zoneName == 'example.com' && it.kind == 'read' }.sources.type",
                        hasItem("direct"))
                .body(
                        "zoneRights.find { it.ownerFolder == 'admin.u' && it.zoneName == 'example.com' && it.kind == 'read' }.sources.grantId",
                        notNullValue());
    }

    @Test
    @Transactional
    void adminResetsOperatorPassword() {
        String adminCookie = login("admin", "admin");
        long operatorId = ensureOperator(adminCookie, "reset-user", "reset-user");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("password", "reset-by-admin"))
                .when()
                .patch("/api/v1/admin/operators/" + operatorId)
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "reset-user", "password", "reset-by-admin"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200);
    }

    @Test
    void nonAdminCannotReadOperatorRights() {
        ensureReader();
        Operator reader = Operator.findByUsername("reader");
        String readerCookie = login("reader", "reader");

        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/admin/operators/" + reader.id + "/rights")
                .then()
                .statusCode(403);
    }

    @Transactional
    void ensureReader() {
        Operator reader = Operator.findByUsername("reader");
        if (reader == null) {
            reader = operatorAuthService.createOperator("reader", "reader");
        } else if (operatorAuthService.authenticate("reader", "reader").isEmpty()) {
            reader.passwordHash = io.quarkus.elytron.security.common.BcryptUtil.bcryptHash("reader");
        }
        if (ZoneGrant.count(
                        "operator = ?1 and ownerFolder = ?2 and zoneName = ?3 and kind = ?4",
                        reader,
                        "admin.u",
                        "example.com",
                        ZoneRightKind.READ)
                == 0) {
            operatorAuthService.grantZoneRight(reader, "admin.u", "example.com", ZoneRightKind.READ);
        }
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
