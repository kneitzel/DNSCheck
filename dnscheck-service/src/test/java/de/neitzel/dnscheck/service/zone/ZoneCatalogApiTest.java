package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.Operator;
import de.neitzel.dnscheck.service.auth.entity.ZoneGrant;
import de.neitzel.dnscheck.service.support.OpenApiContractSupport;
import com.atlassian.oai.validator.model.Request;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;

/**
 * Tests listing of the zone catalog.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneCatalogApiTest {

    /**
     * Owner folder used for the admin's zone definitions.
     */
    private static final String ADMIN_OWNER = "admin.u";
    /**
     * Owner folder used for the reader's zone definitions.
     */
    private static final String READER_OWNER = "reader.u";

    /**
     * Service used to create the reader operator.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the admin operator.
     */
    private static String adminCookie;
    /**
     * Session cookie for the operator who owns the reader folder.
     */
    private static String readerCookie;

    /**
     * Writes valid and invalid zone definitions under two owner folders and creates the reader.
     */
    @Test
    @Order(0)
    @Transactional
    void seedCatalogAndReader() throws Exception {
        Path zonesDir = Path.of("target/test-zones");
        Path adminDir = zonesDir.resolve(ADMIN_OWNER);
        Path readerDir = zonesDir.resolve(READER_OWNER);
        Files.createDirectories(adminDir);
        Files.createDirectories(readerDir);
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
        Files.writeString(
                adminDir.resolve("wrong.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.example.com",
                  "keyFile": "admin.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                adminDir.resolve("other.test.json"),
                """
                {
                  "zone": "other.test",
                  "nameserver": "ns1.other.test",
                  "keyFile": "admin.u/other.test.key",
                  "records": []
                }
                """);
        Files.writeString(
                readerDir.resolve("example.com.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.reader.example.com",
                  "keyFile": "reader.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                readerDir.resolve("wrong.json"),
                """
                {
                  "zone": "example.com",
                  "nameserver": "ns1.reader.example.com",
                  "keyFile": "reader.u/example.com.key",
                  "records": []
                }
                """);
        Files.writeString(
                zonesDir.resolve("legacy-flat.json"),
                """
                {
                  "zone": "legacy.test",
                  "nameserver": "ns1.legacy.test",
                  "keyFile": "legacy.test.key",
                  "records": []
                }
                """);

        ensureOperator("reader", "reader");
    }

    /**
     * Listing the zone catalog without a session is rejected.
     */
    @Test
    @Order(1)
    void unauthenticatedListingIsRejected() {
        given()
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(401);
    }

    /**
     * An admin sees every zone catalog entry in both owner folders, including an invalid filename mismatch.
     */
    @Test
    @Order(2)
    void adminSeesAllCatalogEntries() {
        adminCookie = login("admin", "admin");
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .body("zonesDir", is("target/test-zones"))
                .body("entries.findAll { it.ownerFolder == 'admin.u' }.size()", is(3))
                .body("entries.findAll { it.ownerFolder == 'reader.u' }.size()", is(2))
                .body("entries.zone", hasItem("example.com"))
                .body("entries.zone", hasItem("other.test"))
                .body("entries.ownerFolder", hasItem(ADMIN_OWNER))
                .body("entries.ownerFolder", hasItem(READER_OWNER))
                .body("entries.find { it.filename == 'admin.u/wrong.json' }.valid", is(false))
                .body("entries.find { it.filename == 'admin.u/example.com.json' }.keyFile", is("admin.u/example.com.key"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms("listZones", Request.Method.GET, "/api/v1/zones", response);
    }

    /**
     * A reader sees only the zone catalog entries in that operator's owner folder.
     */
    @Test
    @Order(3)
    void readerSeesOnlyOwnedZones() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.size()", is(2))
                .body("entries.ownerFolder", hasItem(READER_OWNER))
                .body("entries.find { it.filename == 'reader.u/wrong.json' }.valid", is(false))
                .body("entries.zone", hasItem("example.com"));
    }

    /**
     * A reader does not see zone catalog entries owned by the admin.
     */
    @Test
    @Order(4)
    void readerDoesNotSeeUnownedZones() {
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.ownerFolder.findAll { it == 'admin.u' }", empty())
                .body("entries.zone.findAll { it == 'other.test' }", empty());
    }

    /**
     * Returns the named operator, creating it when it is missing, and clears that operator's supplemental zone grants.
     *
     * @param username operator username.
     * @param password operator password.
     * @return the operator, with supplemental zone grants removed.
     */
    private Operator ensureOperator(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            operator = operatorAuthService.createOperator(username, password);
        }
        ZoneGrant.delete("operator", operator);
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
