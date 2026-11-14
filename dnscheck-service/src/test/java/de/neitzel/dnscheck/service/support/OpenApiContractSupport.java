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

    /**
     * Classpath location of the published OpenAPI document.
     */
    private static final String PUBLISHED_SPEC = "/META-INF/openapi.yml";

    /**
     * Parsed OpenAPI contract cached for response checks.
     */
    private static final ContractContext CONTEXT = loadContext();

    /**
     * Prevents instantiation of the contract support.
     */
    private OpenApiContractSupport() {}

    /**
     * Fails the test when the response does not match the published operation.
     *
     * @param operationId OpenAPI operation id under test.
     * @param method HTTP method the test called.
     * @param path request path the test called.
     * @param response REST Assured response to check.
     */
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

    /**
     * Returns whether an actual path matches an OpenAPI path template.
     *
     * @param pathTemplate documented path template, including parameter segments.
     * @param actualPath request path used by the test.
     * @return true when every segment matches.
     */
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

    /**
     * Loads the published OpenAPI document and indexes its operations.
     *
     * @return the contract context used by later checks.
     */
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

    /**
     * Indexes every HTTP method published on one path template.
     *
     * @param operations operations indexed by operation id.
     * @param pathTemplate path template being indexed.
     * @param pathItem OpenAPI path item that holds the operations.
     */
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

    /**
     * Records one operation's method, path template, and success status codes.
     *
     * @param operations operations indexed by operation id.
     * @param pathTemplate path template of the operation.
     * @param method HTTP method of the operation.
     * @param operation OpenAPI operation, ignored when it has no operation id.
     */
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

    /**
     * Returns the classpath URL of the published OpenAPI document.
     *
     * @return the spec URL.
     */
    private static URL specUrl() {
        URL specUrl = OpenApiContractSupport.class.getResource(PUBLISHED_SPEC);
        if (specUrl == null) {
            throw new IllegalStateException("Published OpenAPI spec not on classpath: " + PUBLISHED_SPEC);
        }
        return specUrl;
    }

    /**
     * Builds the failure text for one contract mismatch.
     *
     * @param operationId OpenAPI operation id.
     * @param method HTTP method the test called.
     * @param path request path the test called.
     * @param statusCode HTTP status of the response.
     * @param detail explanation of the mismatch.
     * @return the failure message.
     */
    private static String failureMessage(
            String operationId, Request.Method method, String path, int statusCode, String detail) {
        return "OpenAPI contract failure for operation '" + operationId + "' " + method + " " + path + " (HTTP "
                + statusCode + "): " + detail;
    }

    /**
     * Builds the failure text when a response violates the OpenAPI schema.
     *
     * @param operationId OpenAPI operation id.
     * @param method HTTP method the test called.
     * @param path request path the test called.
     * @param statusCode HTTP status of the response.
     * @param report schema validation report.
     * @return the failure message, including each schema message.
     */
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

    /**
     * Holds the validator and the operations indexed from the published contract.
     */
    private record ContractContext(
            /**
             * Validator that checks a response against the published contract.
             */
            OpenApiInteractionValidator validator,
            /**
             * Operations from the published contract, keyed by operation id.
             */
            Map<String, OperationDefinition> operations) {}

    /**
     * Holds one published operation's method, path template, and success status codes.
     */
    private record OperationDefinition(
            /**
             * HTTP method documented for the operation.
             */
            Request.Method method,
            /**
             * Path template documented for the operation.
             */
            String pathTemplate,
            /**
             * Success status codes documented for the operation.
             */
            Set<Integer> successStatusCodes) {}
}
