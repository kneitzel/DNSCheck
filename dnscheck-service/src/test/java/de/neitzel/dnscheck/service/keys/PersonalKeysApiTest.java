package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
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
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Tests TSIG key files stored in an operator owner folder.
 */
@QuarkusTest
class PersonalKeysApiTest {

    /**
     * BIND native key file body used as a valid personal TSIG key.
     */
    private static final String VALID_KEY = """
            key "example.com" {
              algorithm hmac-sha256;
              secret "abc123";
            };
            """;

    /**
     * Service used to create fixture operators and grant the admin global right.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Clears the test key directory before each test.
     */
    @BeforeEach
    void prepareKeysDir() throws Exception {
        Path keysDir = Path.of("target/test-keys");
        Files.createDirectories(keysDir);
        if (Files.isDirectory(keysDir)) {
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
    }

    /**
     * Creates an admin operator and two operators without a global right.
     */
    @BeforeEach
    @Transactional
    void seedOperators() {
        ensureOperator("keys-admin", "keys-admin", GlobalRightKind.ADMIN);
        ensureOperator("keys-owner", "keys-owner", null);
        ensureOperator("keys-other", "keys-other", null);
    }

    /**
     * The owner can create, list, read, replace, download, and delete a TSIG key in that operator's owner folder.
     */
    @Test
    void ownerCanUploadListEditAndDeletePersonalKey() {
        String cookie = login("keys-owner", "keys-owner");

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "example.com.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(201)
                .body("keyName", is("example.com.key"))
                .body("scopedPath", is("keys-owner.u/example.com.key"));

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .when()
                .get("/api/v1/account/keys")
                .then()
                .statusCode(200)
                .body("keys.keyName", org.hamcrest.Matchers.hasItem("example.com.key"));

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .when()
                .get("/api/v1/account/keys/example.com.key")
                .then()
                .statusCode(200)
                .body("content", notNullValue());

        String updated = VALID_KEY.replace("abc123", "def456");
        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(Map.of("content", updated))
                .when()
                .put("/api/v1/account/keys/example.com.key")
                .then()
                .statusCode(200);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .when()
                .get("/api/v1/account/keys/example.com.key/download")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("def456"));

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .when()
                .delete("/api/v1/account/keys/example.com.key")
                .then()
                .statusCode(204);
    }

    /**
     * Uploading a personal key that already exists is rejected.
     */
    @Test
    void duplicateUploadReturns409() {
        String cookie = login("keys-owner", "keys-owner");
        Map<String, String> body = Map.of("keyName", "dup.test.key", "content", VALID_KEY);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(409);
    }

    /**
     * An admin's personal key list contains only that admin's keys and does not reveal another operator's key.
     */
    @Test
    void adminOnlySeesOwnPersonalKeys() {
        String ownerCookie = login("keys-owner", "keys-owner");
        String adminCookie = login("keys-admin", "keys-admin");

        given()
                .cookie(SessionService.COOKIE_NAME, ownerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "owner.test.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .contentType(ContentType.JSON)
                .body(Map.of("keyName", "admin.test.key", "content", VALID_KEY))
                .when()
                .post("/api/v1/account/keys")
                .then()
                .statusCode(201);

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/account/keys")
                .then()
                .statusCode(200)
                .body("keys.keyName", org.hamcrest.Matchers.hasItem("admin.test.key"))
                .body("keys.keyName", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("owner.test.key")));

        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/account/keys/owner.test.key")
                .then()
                .statusCode(404);
    }

    /**
     * Listing personal keys without a session is rejected.
     */
    @Test
    void unauthenticatedRequestsAreRejected() {
        given()
                .when()
                .get("/api/v1/account/keys")
                .then()
                .statusCode(401);
    }

    /**
     * Creates the operator when it is missing and grants a global right when one is supplied.
     *
     * @param username operator username.
     * @param password operator password.
     * @param globalRight global right to grant, or null when the operator should have none.
     */
    private void ensureOperator(String username, String password, GlobalRightKind globalRight) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            operator = operatorAuthService.createOperator(username, password);
        }
        if (globalRight != null) {
            operatorAuthService.grantGlobalRight(operator, globalRight);
        }
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
