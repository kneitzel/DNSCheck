package de.neitzel.dnscheck.service.zone;

import de.neitzel.dnscheck.service.auth.OperatorAuthService;
import de.neitzel.dnscheck.service.auth.SessionService;
import de.neitzel.dnscheck.service.auth.entity.GlobalGrant;
import de.neitzel.dnscheck.service.auth.entity.GlobalRightKind;
import de.neitzel.dnscheck.service.auth.entity.Operator;
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

/**
 * Tests creation of zone definitions.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ZoneCreateApiTest {

    /**
     * Owner folder of the operator who holds the zone-create global right.
     */
    private static final String CREATOR_OWNER = "creator.u";

    /**
     * Service used to create fixture operators and grant the zone-create global right.
     */
    @Inject
    OperatorAuthService operatorAuthService;

    /**
     * Session cookie for the operator who holds the zone-create global right.
     */
    private static String creatorCookie;
    /**
     * Session cookie for an operator without the zone-create global right.
     */
    private static String readerCookie;

    /**
     * Creates the creator, grants that operator the zone-create global right, and creates a reader.
     */
    @Test
    @Order(0)
    @Transactional
    void seedOperators() throws Exception {
        Files.createDirectories(Path.of("target/test-zones"));

        Operator creator = ensureOperator("creator", "creator");
        if (GlobalGrant.count("operator = ?1 and kind = ?2", creator, GlobalRightKind.ZONE_CREATE) == 0) {
            operatorAuthService.grantGlobalRight(creator, GlobalRightKind.ZONE_CREATE);
        }
        ensureOperator("reader", "reader");
    }

    /**
     * Creating a zone definition without a session is rejected.
     */
    @Test
    @Order(1)
    void unauthenticatedCreateIsRejected() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", CREATOR_OWNER,
                        "zone", "brand.new",
                        "nameserver", "ns1.brand.new",
                        "keyFile", "creator.u/brand.new.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(401);
    }

    /**
     * An operator without the zone-create global right cannot create a zone definition.
     */
    @Test
    @Order(2)
    void readerCannotCreateZones() {
        readerCookie = login("reader", "reader");
        given()
                .cookie(SessionService.COOKIE_NAME, readerCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", "reader.u",
                        "zone", "brand.new",
                        "nameserver", "ns1.brand.new",
                        "keyFile", "reader.u/brand.new.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(403);
    }

    /**
     * An operator with the zone-create global right can create a valid zone definition in that operator's owner folder.
     */
    @Test
    @Order(3)
    void creatorCanCreateZoneDefinition() throws Exception {
        creatorCookie = login("creator", "creator");
        Response response = given()
                .cookie(SessionService.COOKIE_NAME, creatorCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", CREATOR_OWNER,
                        "zone", "brand.new",
                        "nameserver", "ns1.brand.new",
                        "keyFile", "creator.u/brand.new.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .body("zone", is("brand.new"))
                .body("ownerFolder", is(CREATOR_OWNER))
                .body("filename", is("creator.u/brand.new.json"))
                .body("valid", is(true))
                .body("nameserver", is("ns1.brand.new"))
                .extract()
                .response();
        OpenApiContractSupport.assertResponseConforms("createZone", Request.Method.POST, "/api/v1/zones", response);

        assert Files.isRegularFile(Path.of("target/test-zones/creator.u/brand.new.json"));
    }

    /**
     * An admin sees the created zone catalog entry.
     */
    @Test
    @Order(4)
    void adminSeesCreatedZoneInCatalog() {
        String adminCookie = login("admin", "admin");
        given()
                .cookie(SessionService.COOKIE_NAME, adminCookie)
                .when()
                .get("/api/v1/zones")
                .then()
                .statusCode(200)
                .body("entries.find { it.zone == 'brand.new' && it.ownerFolder == 'creator.u' }.zone", is("brand.new"));
    }

    /**
     * Creating the same zone definition again is rejected.
     */
    @Test
    @Order(5)
    void duplicateZoneReturnsConflict() {
        given()
                .cookie(SessionService.COOKIE_NAME, creatorCookie)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "owner", CREATOR_OWNER,
                        "zone", "brand.new",
                        "nameserver", "ns1.brand.new",
                        "keyFile", "creator.u/brand.new.key"))
                .when()
                .post("/api/v1/zones")
                .then()
                .statusCode(409);
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
