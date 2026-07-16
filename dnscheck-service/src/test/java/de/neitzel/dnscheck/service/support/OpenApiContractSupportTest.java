package de.neitzel.dnscheck.service.support;

import com.atlassian.oai.validator.model.Request;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenApiContractSupportTest {

    @Test
    void acceptsValidLoginResponse() {
        Response response = mockResponse(200, "{\"username\":\"admin\"}", "application/json");

        assertDoesNotThrow(() -> OpenApiContractSupport.assertResponseConforms(
                "login", Request.Method.POST, "/api/v1/auth/login", response));
    }

    @Test
    void acceptsParameterizedPathTemplate() {
        Response response = mockResponse(
                200,
                "{\"zone\":\"example.com\",\"defaultTtl\":3600,\"records\":[]}",
                "application/json");

        assertDoesNotThrow(() -> OpenApiContractSupport.assertResponseConforms(
                "getZoneView", Request.Method.GET, "/api/v1/zones/admin.u/example.com/view", response));
    }

    @Test
    void rejectsUnknownOperationId() {
        Response response = mockResponse(200, "{\"username\":\"admin\"}", "application/json");

        AssertionError error = assertThrows(AssertionError.class, () ->
                OpenApiContractSupport.assertResponseConforms(
                        "missingOperation", Request.Method.POST, "/api/v1/auth/login", response));

        assertTrue(error.getMessage().contains("operation 'missingOperation'"));
        assertTrue(error.getMessage().contains("HTTP 200"));
        assertTrue(error.getMessage().contains("unknown OpenAPI operationId"));
    }

    @Test
    void rejectsPathTemplateMismatchForOperation() {
        Response response = mockResponse(200, "{\"username\":\"admin\"}", "application/json");

        AssertionError error = assertThrows(AssertionError.class, () ->
                OpenApiContractSupport.assertResponseConforms(
                        "createZone", Request.Method.POST, "/api/v1/auth/login", response));

        assertTrue(error.getMessage().contains("operation 'createZone'"));
        assertTrue(error.getMessage().contains("HTTP 200"));
        assertTrue(error.getMessage().contains("documented path template is '/api/v1/zones'"));
    }

    @Test
    void rejectsWrongHttpMethod() {
        Response response = mockResponse(200, "{\"username\":\"admin\"}", "application/json");

        AssertionError error = assertThrows(AssertionError.class, () ->
                OpenApiContractSupport.assertResponseConforms(
                        "login", Request.Method.GET, "/api/v1/auth/login", response));

        assertTrue(error.getMessage().contains("operation 'login'"));
        assertTrue(error.getMessage().contains("HTTP 200"));
        assertTrue(error.getMessage().contains("documented method is POST"));
    }

    @Test
    void rejectsUndocumentedSuccessStatus() {
        Response response = mockResponse(
                200,
                "{\"zone\":\"brand.new\",\"filename\":\"brand.new.json\",\"valid\":true,\"nameserver\":\"ns1.brand.new\"}",
                "application/json");

        AssertionError error = assertThrows(AssertionError.class, () ->
                OpenApiContractSupport.assertResponseConforms(
                        "createZone", Request.Method.POST, "/api/v1/zones", response));

        assertTrue(error.getMessage().contains("documented success statuses are [201]"));
    }

    @Test
    void rejectsPathThatDoesNotMatchTemplate() {
        Response response = mockResponse(
                200,
                "{\"zone\":\"example.com\",\"defaultTtl\":3600,\"records\":[]}",
                "application/json");

        AssertionError error = assertThrows(AssertionError.class, () ->
                OpenApiContractSupport.assertResponseConforms(
                        "getZoneView", Request.Method.GET, "/api/v1/zones/example.com/views", response));

        assertTrue(error.getMessage().contains("documented path template is '/api/v1/zones/{owner}/{zoneName}/view'"));
    }

    private static Response mockResponse(int statusCode, String body, String contentType) {
        Response response = mock(Response.class);
        io.restassured.response.ResponseBody responseBody = mock(io.restassured.response.ResponseBody.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.getBody()).thenReturn(responseBody);
        when(responseBody.asString()).thenReturn(body);
        when(response.getContentType()).thenReturn(contentType);
        return response;
    }
}
