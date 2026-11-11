package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.retrieval.PassagePolicy;
import com.tracewitness.platform.retrieval.RetrievalPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalPolicyTest {
    @Test
    void fivePositiveAndFiveReverseRetrievalCases() throws Exception {
        assertCases(resolve("eval/positive/w09.json"), 5);
        assertCases(resolve("eval/reverse/w09.json"), 5);
    }

    private static void assertCases(Path path, int expected) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> cases = mapper.readValue(path.toFile(), new TypeReference<List<Map<String, Object>>>() {
        });
        assertEquals(expected, cases.size(), path.getFileName().toString());
        for (Map<String, Object> item : cases) {
            String id = String.valueOf(item.get("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) item.get("input");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) item.get("expect");
            Map<String, Object> actual = "passage".equals(item.get("op"))
                    ? PassagePolicy.evaluate(input)
                    : RetrievalPolicy.evaluate(input);
            assertMatches(id, expect, actual);
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

    private static Path resolve(String relative) {
        Path path = Paths.get("..", relative);
        if (!Files.exists(path)) {
            path = Paths.get(relative);
        }
        return path;
    }
}
