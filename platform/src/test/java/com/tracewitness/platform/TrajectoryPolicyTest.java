package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.trajectory.TrajectoryPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrajectoryPolicyTest {
    @Test
    void tenPositiveTrajectoryCases() throws Exception {
        Path path = Paths.get("..", "eval/positive/w10.json");
        if (!Files.exists(path)) {
            path = Paths.get("eval/positive/w10.json");
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> cases = mapper.readValue(path.toFile(), new TypeReference<List<Map<String, Object>>>() {
        });
        assertEquals(10, cases.size());
        for (Map<String, Object> item : cases) {
            String id = String.valueOf(item.get("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) item.get("input");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) item.get("expect");
            assertMatches(id, expect, TrajectoryPolicy.evaluate(String.valueOf(item.get("op")), input));
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertMatches(String id, Object expect, Object actual) {
        if (expect instanceof Map) {
            Map<String, Object> expectedMap = (Map<String, Object>) expect;
            Map<String, Object> actualMap = (Map<String, Object>) actual;
            for (Map.Entry<String, Object> entry : expectedMap.entrySet()) {
                assertMatches(id + " " + entry.getKey(), entry.getValue(), actualMap.get(entry.getKey()));
            }
            return;
        }
        if (expect instanceof List) {
            List<Object> expectedList = (List<Object>) expect;
            List<Object> actualList = (List<Object>) actual;
            assertEquals(expectedList.size(), actualList.size(), id);
            for (int i = 0; i < expectedList.size(); i++) {
                assertMatches(id + "[" + i + "]", expectedList.get(i), actualList.get(i));
            }
            return;
        }
        assertEquals(expect, actual, id);
    }
}
