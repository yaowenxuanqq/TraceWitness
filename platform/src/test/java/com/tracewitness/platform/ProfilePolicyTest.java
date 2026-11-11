package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.profile.ProfilePolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfilePolicyTest {
    @Test
    void fivePositiveAndFiveReverseProfileCases() throws Exception {
        assertCases(resolve("eval/positive/w08.json"), 5);
        assertCases(resolve("eval/reverse/w08.json"), 5);
    }

    private static void assertCases(Path path, int expected) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> cases = mapper.readValue(path.toFile(), new TypeReference<List<Map<String, Object>>>() {
        });
        assertEquals(expected, cases.size(), path.getFileName().toString());
        for (Map<String, Object> item : cases) {
            String id = String.valueOf(item.get("id"));
            String op = item.get("op") == null ? "decide" : String.valueOf(item.get("op"));
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) item.get("input");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) item.get("expect");
            Map<String, Object> actual = ProfilePolicy.evaluate(op, input);
            for (Map.Entry<String, Object> entry : expect.entrySet()) {
                assertEquals(entry.getValue(), actual.get(entry.getKey()), id + " " + entry.getKey());
            }
        }
    }

    private static Path resolve(String relative) {
        Path path = Paths.get("..", relative);
        if (!Files.exists(path)) {
            path = Paths.get(relative);
        }
        return path;
    }
}
