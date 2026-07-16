package de.neitzel.dnscheck.service.auth;

import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.zone.LiveZoneLoader;
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
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminApiTest {

    private static final String GROUP_OWNER = "dns-readers.g";

    @InjectMock
    LiveZoneLoader liveZoneLoader;

    @Inject
    OperatorAuthService operatorAuthService;

    private static String adminCookie;
    private static String readerCookie;
    private static Long createdOperatorId;
    private static Long createdGroupId;
    private static Long zoneGrantId;

    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenReturn(new LiveZone("example.com", 3600, List.of()));
    }

    @Test
    @Order(0)
    @Transactional
    void seedZoneCatalog() throws Exception {
        Path zonesDir = Path.of("target/test-zones");
        Path groupDir = zonesDir.resolve(GROUP_OWNER);
        Files.createDirectories(groupDir);
        Files.writeString(
                groupDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "dns-readers.g/example.com.key",
                  "records": []
                }
                """);

        ensureOperator("reader", "reader");
        cleanupManagedTestData();
    }

    @Transactional
    void cleanupManagedTestData() {
        Operator managed = Operator.findByUsername("managed");
        if (managed != null) {
            GlobalGrant.delete("operator", managed);
            ZoneGrant.delete("operator", managed);
            for (Group group : List.copyOf(managed.groups)) {
                group.members.remove(managed);
                managed.groups.remove(group);
            }
            managed.delete();
        }
        Group group = Group.findByName("dns-readers");
        if (group != null) {
            GlobalGrant.delete("group", group);
            ZoneGrant.delete("group", group);
            group.delete();
        }
        try {
            Files.deleteIfExists(Path.of("target/test-zones/managed.u/managed.test.json"));
        } catch (Exception ignored) {
            // best-effort cleanup for zone-create test artifact
        }
    }

    @Test
    @Order(1)
    void nonAdminCannotListOperators() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/admin/operators")
                .then()
                .statusCode(403);
    }

    @Test
    @Order(2)
    void adminCreatesOperatorGroupAndGrants() {
        adminCookie = login("admin", "admin");

        createdOperatorId = ((Number) given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("username", "managed", "password", "managed"))
                .when()
                .post("/api/v1/admin/operators")
                .then()
                .statusCode(200)
                .body("username", is("managed"))
                .body("enabled", is(true))
                .extract()
                .path("id")).longValue();

        createdGroupId = ((Number) given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("name", "dns-readers"))
                .when()
                .post("/api/v1/admin/groups")
                .then()
                .statusCode(200)
                .body("name", is("dns-readers"))
                .extract()
                .path("id")).longValue();

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .put("/api/v1/admin/groups/%d/members/%d".formatted(createdGroupId, createdOperatorId))
                .then()
                .statusCode(200)
                .body("memberUsernames", hasItem("managed"));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "zone_create", "operatorId", createdOperatorId))
                .when()
                .post("/api/v1/admin/global-grants")
                .then()
                .statusCode(200)
                .body("kind", is("zone_create"))
                .body("operatorUsername", is("managed"));

        zoneGrantId = ((Number) given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "ownerFolder", GROUP_OWNER,
                        "zoneName", "example.com",
                        "kind", "read",
                        "operatorId", createdOperatorId))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(200)
                .body("ownerFolder", is(GROUP_OWNER))
                .body("zoneName", is("example.com"))
                .body("kind", is("read"))
                .extract()
                .path("id")).longValue();
    }

    @Test
    @Order(3)
    void groupMemberCanAccessGroupZoneView() {
        String managedCookie = login("managed", "managed");
        given()
                .cookie(SessionService.COOKIE_NAME, managedCookie)
                .when()
                .get("/api/v1/zones/%s/example.com/view".formatted(GROUP_OWNER))
                .then()
                .statusCode(200)
                .body("zone", is("example.com"));
    }

    @Test
    @Order(4)
    void adminDisablesOperatorAndRemovesGroupMembership() {
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("enabled", false))
                .when()
                .patch("/api/v1/admin/operators/" + createdOperatorId)
                .then()
                .statusCode(200)
                .body("enabled", is(false));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .delete("/api/v1/admin/groups/%d/members/%d".formatted(createdGroupId, createdOperatorId))
                .then()
                .statusCode(200);

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .delete("/api/v1/admin/zone-grants/" + zoneGrantId)
                .then()
                .statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "managed", "password", "managed"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(5)
    void adminListsOperatorsAndGroups() {
        Response operatorsResponse = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/admin/operators")
                .then()
                .body("operators.username", hasItem("managed"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms(
                "listOperators", Request.Method.GET, "/api/v1/admin/operators", operatorsResponse);

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/admin/groups")
                .then()
                .statusCode(200)
                .body("groups.name", hasItem("dns-readers"));
    }

    @Test
    @Order(6)
    void reenabledOperatorWithoutGroupMembershipLosesZoneAccess() {
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("enabled", true))
                .when()
                .patch("/api/v1/admin/operators/" + createdOperatorId)
                .then()
                .statusCode(200)
                .body("enabled", is(true));

        String managedCookie = login("managed", "managed");
        given()
                .cookie(SessionService.COOKIE_NAME, managedCookie)
                .when()
                .get("/api/v1/zones/%s/example.com/view".formatted(GROUP_OWNER))
                .then()
                .statusCode(403);
    }

    @Test
    @Order(7)
    void zoneCreateGrantAllowsCreatingZonesInOwnFolder() throws Exception {
        given()
                .cookie(SessionService.COOKIE_NAME, login("managed", "managed"))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", "managed.u",
                        "zone", "managed.test",
                        "nameserver", "ns1.managed.test",
                        "keyFile", "managed.u/managed.test.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(201)
                .body("zone", is("managed.test"))
                .body("ownerFolder", is("managed.u"));

        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.zone", not(hasItem("managed.test")));

        Files.deleteIfExists(Path.of("target/test-zones/managed.u/managed.test.json"));
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
