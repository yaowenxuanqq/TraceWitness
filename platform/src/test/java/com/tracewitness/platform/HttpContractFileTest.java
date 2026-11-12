package com.tracewitness.platform;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpContractFileTest {
    private static final Set<String> REQUIRED_PATHS = new HashSet<String>(Arrays.asList(
            "/api/session",
            "/api/health",
            "/api/mode",
            "/api/tools",
            "/api/overview",
            "/api/sites",
            "/api/devices",
            "/api/events",
            "/api/events/{id}",
            "/api/agent/runs",
            "/api/agent/runs/{id}",
            "/api/agent/runs/{id}/tool-calls",
            "/api/observations/{id}/thumbnail",
            "/api/profiles/tickets/{id}/decision",
            "/api/alerts/{id}/decision",
            "/api/evidence/{id}/verify",
            "/api/hard-cases/{id}/label",
            "/api/hard-cases/export",
            "/api/plugins/{id}/fault",
            "/api/plugins/rollback",
            "/api/media",
            "/api/media/assets/{id}/file",
            "/api/media/segments/{id}/frames/{index}",
            "/api/media/uploads"
    ));

    @Test
    void contractIsValidAndCoversEveryHttpRoute() {
        Path file = Paths.get("..", "contracts", "http-api.yaml");
        SwaggerParseResult parsed = new OpenAPIParser().readLocation(
                file.toUri().toString(), null, null);

        assertTrue(parsed.getMessages().isEmpty(), String.join("\n", parsed.getMessages()));
        OpenAPI contract = parsed.getOpenAPI();
        assertNotNull(contract);
        assertNotNull(contract.getPaths());
        assertTrue(contract.getPaths().keySet().containsAll(REQUIRED_PATHS),
                "Missing paths: " + difference(REQUIRED_PATHS, contract.getPaths().keySet()));
        assertNotNull(contract.getComponents().getSecuritySchemes().get("TraceToken"));

        for (String path : REQUIRED_PATHS) {
            PathItem item = contract.getPaths().get(path);
            for (Operation operation : item.readOperations()) {
                assertNotNull(operation.getResponses(), path + " has no responses");
            }
            if (path.contains("{")) {
                assertDeclaredPathParameters(contract, path, item);
            }
        }
        assertNotNull(contract.getPaths().get("/api/media/uploads").getPost().getSecurity());
    }

    private static void assertDeclaredPathParameters(OpenAPI contract, String path, PathItem item) {
        for (Operation operation : item.readOperations()) {
            assertNotNull(operation.getParameters(), path + " has no parameters");
            for (String parameter : pathParameters(path)) {
                boolean declared = operation.getParameters().stream()
                        .map(value -> resolve(contract, value))
                        .anyMatch(value -> parameter.equals(value.getName()) && "path".equals(value.getIn()));
                assertTrue(declared, path + " does not declare " + parameter);
            }
        }
    }

    private static Parameter resolve(OpenAPI contract, Parameter parameter) {
        String ref = parameter.get$ref();
        if (ref == null) {
            return parameter;
        }
        String name = ref.substring(ref.lastIndexOf('/') + 1);
        return contract.getComponents().getParameters().get(name);
    }

    private static Set<String> pathParameters(String path) {
        Set<String> values = new HashSet<String>();
        int start = path.indexOf('{');
        while (start >= 0) {
            int end = path.indexOf('}', start);
            values.add(path.substring(start + 1, end));
            start = path.indexOf('{', end + 1);
        }
        return values;
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<String>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
