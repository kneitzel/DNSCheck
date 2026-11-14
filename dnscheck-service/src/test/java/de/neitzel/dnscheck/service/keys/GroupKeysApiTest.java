package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
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
import static org.hamcrest.CoreMatchers.is;

/**
 * Tests TSIG key files stored in a group owner folder.
 */
@QuarkusTest
class GroupKeysApiTest {

    /**
     * BIND native key file body used as a valid group TSIG key.
     */
    private static final String VALID_KEY = """
            key "shared.example" {
              algorithm hmac-sha256;
              secret "group-secret";
            };
            """;

    /**
     * Service used to create the group member.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Database id of the group that owns the key files.
     */
    private long groupId;

    /**
     * Clears the test key directory before each test.
     */
    @BeforeEach
    void prepareKeysDir() throws Exception {
        Path keysDir = Path.of("target/test-keys");
        Files.createDirectories(keysDir);
        try (var paths = Files.list(keysDir)) {
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

    /**
     * Ensures the group exists and that the member operator belongs to it.
     */
    @BeforeEach
    @Transactional
    void seedGroup() {
        Operator member = ensureOperator("group-member", "group-member");
        Group group = Group.findByName("keys-group");
        if (group == null) {
            group = new Group();
            group.name = "keys-group";
            group.persist();
        }
        if (!group.members.contains(member)) {
            group.members.add(member);
            member.groups.add(group);
        }
        groupId = group.id;
    }

    /**
     * An admin can create, list, download, and delete a TSIG key in the group owner folder.
     */
    @Test
    void adminCanManageGroupKeys() {
        String adminCookie = login("admin", "admin");

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "shared.test.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/admin/groups/%d/keys".formatted(groupId))
                .then()
                .statusCode(201)
                .body("scopedPath", is("keys-group.g/shared.test.key"));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/admin/groups/%d/keys".formatted(groupId))
                .then()
                .statusCode(200)
                .body("keys.keyName", org.hamcrest.Matchers.hasItem("shared.test.key"));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/admin/groups/%d/keys/shared.test.key/download".formatted(groupId))
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("group-secret"));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .delete("/api/v1/admin/groups/%d/keys/shared.test.key".formatted(groupId))
                .then()
                .statusCode(204);
    }

    /**
     * A group member can list the group's key files but cannot download or create them.
     */
    @Test
    void memberCanListButNotDownloadGroupKeys() {
        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "member-visible.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/admin/groups/%d/keys".formatted(groupId))
                .then()
                .statusCode(201);

        String memberCookie = login("group-member", "group-member");

        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .when()
                .get("/api/v1/admin/groups/%d/keys".formatted(groupId))
                .then()
                .statusCode(200)
                .body("keys.keyName", org.hamcrest.Matchers.hasItem("member-visible.key"));

        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .when()
                .get("/api/v1/admin/groups/%d/keys/member-visible.key/download".formatted(groupId))
                .then()
                .statusCode(403);

        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "blocked.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/admin/groups/%d/keys".formatted(groupId))
                .then()
                .statusCode(403);
    }

    /**
     * A group member can read the group detail.
     */
    @Test
    void memberCanViewGroupDetail() {
        String memberCookie = login("group-member", "group-member");
        given()
                .cookie(SessionService.COOKIE_NAME, memberCookie)
                .when()
                .get("/api/v1/admin/groups/%d".formatted(groupId))
                .then()
                .statusCode(200)
                .body("name", is("keys-group"));
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
