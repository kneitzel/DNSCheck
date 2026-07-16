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

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneCatalogApiTest {

    private static final String ADMIN_OWNER = "admin.u";
    private static final String READER_OWNER = "reader.u";

    @Inject
    OperatorAuthService operatorAuthService;

    private static String adminCookie;
    private static String readerCookie;

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

    @Test
    @Order(1)
    void unauthenticatedListingIsRejected() {
        given()
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(401);
    }

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
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms("listZones", Request.Method.GET, "/api/v1/zones", response);
    }

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

    private Operator ensureOperator(String username, String password) {
        Operator operator = Operator.findByUsername(username);
        if (operator == null) {
            operator = operatorAuthService.createOperator(username, password);
        }
        ZoneGrant.delete("operator", operator);
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
