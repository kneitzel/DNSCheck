package de.neitzel.dnscheck.service.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.restassured.response.Response;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validates REST Assured responses against the published OpenAPI contract.
 */
public final class OpenApiContractSupport {

    private static final String PUBLISHED_SPEC = "/META-INF/openapi.yml";

    private static final ContractContext CONTEXT = loadContext();

    private OpenApiContractSupport() {}

    public static void assertResponseConforms(
            String operationId, Request.Method method, String path, Response response) {
        int statusCode = response.statusCode();
        OperationDefinition operation = CONTEXT.operations().get(operationId);
        if (operation == null) {
            fail(failureMessage(
                    operationId, method, path, statusCode, "unknown OpenAPI operationId '" + operationId + "'"));
        }
        if (operation.method() != method) {
            fail(failureMessage(
                    operationId,
                    method,
                    path,
                    statusCode,
                    "documented method is " + operation.method() + " but test used " + method));
        }
        if (!pathsMatch(operation.pathTemplate(), path)) {
            fail(failureMessage(
                    operationId,
                    method,
                    path,
                    statusCode,
                    "documented path template is '" + operation.pathTemplate() + "' but test used '" + path + "'"));
        }
        if (!operation.successStatusCodes().contains(statusCode)) {
            fail(failureMessage(
                    operationId,
                    method,
                    path,
                    statusCode,
                    "documented success statuses are " + operation.successStatusCodes()));
        }

        SimpleResponse.Builder builder = SimpleResponse.Builder.status(statusCode);
        String body = response.getBody().asString();
        if (body != null && !body.isBlank()) {
            builder.withBody(body);
        }
        String contentType = response.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            builder.withContentType(contentType.split(";")[0].trim());
        }

        ValidationReport report = CONTEXT.validator().validateResponse(path, method, builder.build());
        if (report.hasErrors()) {
            fail(formatSchemaReport(operationId, method, path, statusCode, report));
        }
    }

    private static boolean pathsMatch(String pathTemplate, String actualPath) {
        String[] templateSegments = pathTemplate.split("/");
        String[] actualSegments = actualPath.split("/");
        if (templateSegments.length != actualSegments.length) {
            return false;
        }
        for (int index = 0; index < templateSegments.length; index++) {
            String templateSegment = templateSegments[index];
            String actualSegment = actualSegments[index];
            if (templateSegment.startsWith("{") && templateSegment.endsWith("}")) {
                if (actualSegment.isBlank()) {
                    return false;
                }
            } else if (!templateSegment.equals(actualSegment)) {
                return false;
            }
        }
        return true;
    }

    private static ContractContext loadContext() {
        URL specUrl = specUrl();
        OpenAPI openApi = new OpenAPIV3Parser().read(specUrl.toString());
        if (openApi == null || openApi.getPaths() == null) {
            throw new IllegalStateException("Failed to parse OpenAPI spec: " + PUBLISHED_SPEC);
        }
        OpenApiInteractionValidator validator = OpenApiInteractionValidator.createFor(openApi).build();
        Map<String, OperationDefinition> operations = new HashMap<>();
        openApi.getPaths().forEach((pathTemplate, pathItem) -> indexPath(operations, pathTemplate, pathItem));
        return new ContractContext(validator, Map.copyOf(operations));
    }

    private static void indexPath(Map<String, OperationDefinition> operations, String pathTemplate, PathItem pathItem) {
        indexOperation(operations, pathTemplate, Request.Method.GET, pathItem.getGet());
        indexOperation(operations, pathTemplate, Request.Method.PUT, pathItem.getPut());
        indexOperation(operations, pathTemplate, Request.Method.POST, pathItem.getPost());
        indexOperation(operations, pathTemplate, Request.Method.DELETE, pathItem.getDelete());
        indexOperation(operations, pathTemplate, Request.Method.PATCH, pathItem.getPatch());
        indexOperation(operations, pathTemplate, Request.Method.HEAD, pathItem.getHead());
        indexOperation(operations, pathTemplate, Request.Method.OPTIONS, pathItem.getOptions());
        indexOperation(operations, pathTemplate, Request.Method.TRACE, pathItem.getTrace());
    }

    private static void indexOperation(
            Map<String, OperationDefinition> operations,
            String pathTemplate,
            Request.Method method,
            Operation operation) {
        if (operation == null || operation.getOperationId() == null) {
            return;
        }
        Set<Integer> successStatusCodes = new TreeSet<>();
        if (operation.getResponses() != null) {
            for (String statusCode : operation.getResponses().keySet()) {
                if (statusCode.chars().allMatch(Character::isDigit)) {
                    int code = Integer.parseInt(statusCode);
                    if (code >= 200 && code < 300) {
                        successStatusCodes.add(code);
                    }
                }
            }
        }
        operations.put(
                operation.getOperationId(),
                new OperationDefinition(method, pathTemplate, Set.copyOf(successStatusCodes)));
    }

    private static URL specUrl() {
        URL specUrl = OpenApiContractSupport.class.getResource(PUBLISHED_SPEC);
        if (specUrl == null) {
            throw new IllegalStateException("Published OpenAPI spec not on classpath: " + PUBLISHED_SPEC);
        }
        return specUrl;
    }

    private static String failureMessage(
            String operationId, Request.Method method, String path, int statusCode, String detail) {
        return "OpenAPI contract failure for operation '" + operationId + "' " + method + " " + path + " (HTTP "
                + statusCode + "): " + detail;
    }

    private static String formatSchemaReport(
            String operationId,
            Request.Method method,
            String path,
            int statusCode,
            ValidationReport report) {
        String messages = report.getMessages().stream()
                .map(message -> message.getKey() + ": " + message.getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
        return failureMessage(operationId, method, path, statusCode, "response violates OpenAPI schema")
                + System.lineSeparator()
                + messages;
    }

    private record ContractContext(
            OpenApiInteractionValidator validator, Map<String, OperationDefinition> operations) {}

    private record OperationDefinition(
            Request.Method method, String pathTemplate, Set<Integer> successStatusCodes) {}
}
