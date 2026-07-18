package de.neitzel.dnscheck.service.keys;

import de.neitzel.dnscheck.service.auth.SessionService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Regression: an empty missing-key summary must be HTTP 200 with {@code missing: []},
 * not an error — the dashboard UI only shows "Could not load…" when the request fails.
 */
@QuarkusTest
class MissingKeysEmptySummaryApiTest {

    @BeforeEach
    void cleanDirs() throws Exception {
        cleanDir(Path.of("target/test-keys"));
        cleanDir(Path.of("target/test-zones"));
        Files.createDirectories(Path.of("target/test-keys"));
        Files.createDirectories(Path.of("target/test-zones"));
    }

    @Test
    void missingKeySummaryWhenNoKeysMissingReturnsEmptyArray() {
        String cookie = given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "admin", "password", "admin"))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .detailedCookie(SessionService.COOKIE_NAME)
                .getValue();

        given()
                .cookie(SessionService.COOKIE_NAME, cookie)
                .when()
                .get("/api/v1/keys/missing")
                .then()
                .statusCode(200)
                .body("missing", notNullValue())
                .body("missing", empty());
    }

    private static void cleanDir(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort cleanup between tests
                }
            });
        }
    }
}
