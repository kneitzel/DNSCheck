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

/**
 * Tests admin management of operators, groups, global rights, and zone rights.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminApiTest {

    /**
     * Owner folder of the group that owns the sample zone.
     */
    private static final String GROUP_OWNER = "dns-readers.g";

    /**
     * Mock live zone loader used so zone view checks do not contact DNS.
     */
    @InjectMock
    LiveZoneLoader liveZoneLoader;

    /**
     * Service used to create fixture operators.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the admin operator.
     */
    private static String adminCookie;
    /**
     * Session cookie for the operator who is not an admin.
     */
    private static String readerCookie;
    /**
     * Database id of the operator created by the admin.
     */
    private static Long createdOperatorId;
    /**
     * Database id of the group created by the admin.
     */
    private static Long createdGroupId;
    /**
     * Database id of the supplemental zone grant created by the admin.
     */
    private static Long zoneGrantId;

    /**
     * Stubs live zone loading with an empty live zone.
     */
    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenReturn(new LiveZone("example.com", 3600, List.of()));
    }

    /**
     * Writes the group owner folder's zone definition and removes leftover operators, groups, and grants.
     */
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

    /**
     * Removes the managed operator, the dns-readers group, their grants, and a leftover zone definition.
     */
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

    /**
     * An operator who is not an admin cannot list operators.
     */
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

    /**
     * An admin creates an operator, adds that operator to a group, grants the zone-create global right, and grants the read zone right on the group zone.
     */
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

    /**
     * A group member can read the zone view for the zone owned by that group.
     */
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

    /**
     * An admin disables the operator, removes the group membership, and deletes the supplemental zone grant, and the disabled operator can no longer sign in.
     */
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

    /**
     * An admin can list operators and groups, including the operator and group created for this test.
     */
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

    /**
     * After the operator is enabled again, that operator no longer has the zone right to read the group zone view.
     */
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

    /**
     * The zone-create global right lets the operator create a zone definition in that operator's owner folder, and another operator does not see it in the zone catalog.
     */
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
