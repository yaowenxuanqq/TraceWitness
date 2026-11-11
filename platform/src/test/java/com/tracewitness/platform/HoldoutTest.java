package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.plugin.PluginPolicy;
import com.tracewitness.platform.profile.ProfilePolicy;
import com.tracewitness.platform.retrieval.PassagePolicy;
import com.tracewitness.platform.retrieval.RetrievalPolicy;
import com.tracewitness.platform.safety.ReverseRules;
import com.tracewitness.platform.trajectory.TrajectoryPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HoldoutTest {
    @Test
    void sealedScenariosPassWithoutRetuning() throws Exception {
        Path path = Paths.get("..", "eval/holdout/final.json");
        if (!Files.exists(path)) {
            path = Paths.get("eval/holdout/final.json");
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> cases = mapper.readValue(path.toFile(), new TypeReference<List<Map<String, Object>>>() {
        });
        assertEquals(10, cases.size());
        for (Map<String, Object> item : cases) {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) item.get("input");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) item.get("expect");
            assertMatches(String.valueOf(item.get("id")), expect, evaluate(item, input));
        }
    }

    private static Map<String, Object> evaluate(Map<String, Object> item, Map<String, Object> input) {
        String op = String.valueOf(item.get("op"));
        if ("rule".equals(op)) {
            return ReverseRules.evaluate(String.valueOf(item.get("rule")), input);
        }
        if ("plugin".equals(op)) {
            return PluginPolicy.evaluate(input);
        }
        if ("passage".equals(op)) {
            return PassagePolicy.evaluate(input);
        }
        if ("search".equals(op)) {
            return RetrievalPolicy.evaluate(input);
        }
        if ("decide".equals(op)) {
            return ProfilePolicy.evaluate("decide", input);
        }
        return TrajectoryPolicy.evaluate("trajectory", input);
    }

    @SuppressWarnings("unchecked")
    private static void assertMatches(String id, Object expect, Object actual) {
        if (expect instanceof Map) {
            Map<String, Object> expectedMap = (Map<String, Object>) expect;
            Map<String, Object> actualMap = (Map<String, Object>) actual;
            assertFalse(actualMap == null, id);
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
