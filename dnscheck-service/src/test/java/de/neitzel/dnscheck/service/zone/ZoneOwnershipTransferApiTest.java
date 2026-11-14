package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.core.model.LiveZone;
import de.neitzel.dnscheck.core.model.ZoneDefinition;
import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Group;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests transfer of a zone definition into another owner folder.
 */
@QuarkusTest
class ZoneOwnershipTransferApiTest {

    /**
     * Mock live zone loader that returns an empty live zone for the requested zone.
     */
    @InjectMock
    LiveZoneLoader liveZoneLoader;

    /**
     * Service used to create the owner, member, and collaborator.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Stubs live zone loading with an empty record set for whichever zone is requested.
     */
    @BeforeEach
    void stubLiveZone() throws Exception {
        when(liveZoneLoader.load(any(ZoneDefinition.class), any(Path.class)))
                .thenAnswer(invocation -> {
                    ZoneDefinition definition = invocation.getArgument(0);
                    return new LiveZone(definition.zone(), 3600, List.of());
                });
    }

    /**
     * Creates the operators and group, clears the collaborator's supplemental zone grants, and writes the personal and group zone definitions.
     */
    @BeforeEach
    @Transactional
    void seed() throws Exception {
        Operator owner = ensureOperator("xfer-owner", "xfer-owner");
        Operator member = ensureOperator("xfer-member", "xfer-member");
        ensureOperator("xfer-collab", "xfer-collab");

        Group group = Group.findByName("xfer-team");
        if (group == null) {
            group = new Group();
            group.name = "xfer-team";
            group.persist();
        }
        if (!group.members.contains(member)) {
            group.members.add(member);
            member.groups.add(group);
        }
        if (!group.members.contains(owner)) {
            group.members.add(owner);
            owner.groups.add(group);
        }

        ZoneGrant.delete("operator", Operator.findByUsername("xfer-collab"));

        Path zonesDir = Path.of("target/test-zones");
        Path personalDir = zonesDir.resolve("xfer-owner.u");
        Path groupDir = zonesDir.resolve("xfer-team.g");
        Files.createDirectories(personalDir);
        Files.createDirectories(groupDir);
        writeZone(personalDir.resolve("personal.example.json"), "personal.example", "xfer-owner.u");
        writeZone(groupDir.resolve("shared.example.json"), "shared.example", "xfer-team.g");
        Files.deleteIfExists(groupDir.resolve("personal.example.json"));
        Files.deleteIfExists(personalDir.resolve("shared.example.json"));
        Files.deleteIfExists(zonesDir.resolve("admin.u/shared.example.json"));
    }

    /**
     * The owner can transfer a personal zone definition into the group owner folder.
     */
    @Test
    void ownerCanTransferPersonalZoneToGroup() {
        String ownerCookie = login("xfer-owner", "xfer-owner");

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("targetOwner", "xfer-team.g"))
                .when()
                .post("/api/v1/zones/xfer-owner.u/personal.example/transfer")
                .then()
                .statusCode(200)
                .body("ownerFolder", is("xfer-team.g"))
                .body("zone", is("personal.example"))
                .body("filename", is("xfer-team.g/personal.example.json"));

        assertFalse(Files.exists(Path.of("target/test-zones/xfer-owner.u/personal.example.json")));
        assertTrue(Files.exists(Path.of("target/test-zones/xfer-team.g/personal.example.json")));
    }

    /**
     * An admin can transfer a group zone definition into the admin owner folder.
     */
    @Test
    void adminCanTransferGroupZone() throws Exception {
        String adminCookie = login("admin", "admin");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("targetOwner", "admin.u"))
                .when()
                .post("/api/v1/zones/xfer-team.g/shared.example/transfer")
                .then()
                .statusCode(200)
                .body("ownerFolder", is("admin.u"))
                .body("zone", is("shared.example"));

        assertFalse(Files.exists(Path.of("target/test-zones/xfer-team.g/shared.example.json")));
        assertTrue(Files.exists(Path.of("target/test-zones/admin.u/shared.example.json")));
    }

    /**
     * A group member cannot transfer the group's zone definition.
     */
    @Test
    void groupMemberCannotTransferGroupZone() {
        String memberCookie = login("xfer-member", "xfer-member");

        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("targetOwner", "xfer-member.u"))
                .when()
                .post("/api/v1/zones/xfer-team.g/shared.example/transfer")
                .then()
                .statusCode(403);

        assertTrue(Files.exists(Path.of("target/test-zones/xfer-team.g/shared.example.json")));
    }

    /**
     * A supplemental read zone grant moves with the zone definition, and the collaborator can still read the zone view.
     */
    @Test
    void supplementalGrantsFollowTransfer() {
        String adminCookie = login("admin", "admin");
        Operator collab = Operator.findByUsername("xfer-collab");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "ownerFolder", "xfer-owner.u",
                        "zoneName", "personal.example",
                        "kind", "read",
                        "operatorId", collab.id))
                .when()
                .post("/api/v1/admin/zone-grants")
                .then()
                .statusCode(200);

        String ownerCookie = login("xfer-owner", "xfer-owner");
        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("targetOwner", "xfer-team.g"))
                .when()
                .post("/api/v1/zones/xfer-owner.u/personal.example/transfer")
                .then()
                .statusCode(200);

        String collabCookie = login("xfer-collab", "xfer-collab");
        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .when()
                .get("/api/v1/zones/xfer-team.g/personal.example/view")
                .then()
                .statusCode(200)
                .body("zone", is("personal.example"));

        given()
                .cookie(SessionService.COOKIE_NAME, collabCookie)
                .when()
                .get("/api/v1/auth/me/rights")
                .then()
                .statusCode(200)
                .body(
                        "zoneRights.findAll { it.ownerFolder == 'xfer-team.g' && it.zoneName == 'personal.example' && it.kind == 'read' }",
                        hasSize(1))
                .body(
                        "zoneRights.findAll { it.ownerFolder == 'xfer-owner.u' && it.zoneName == 'personal.example' }",
                        hasSize(0));
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
     * Writes an empty zone definition.
     *
     * @param path zone definition file to write.
     * @param zone zone name stored in the file.
     * @param ownerFolder owner folder referenced by the key file path.
     */
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
