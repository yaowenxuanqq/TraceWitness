package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class EvalSuiteWeek11Test {
    @Test
    void thirtyPositiveAndTwentyReverseScenariosPass() throws Exception {
        int positive = run("eval/positive/w07.json")
                + run("eval/positive/w08.json")
                + run("eval/positive/w09.json")
                + run("eval/positive/w10.json");
        int reverse = run("eval/reverse/w06.json")
                + run("eval/reverse/w08.json")
                + run("eval/reverse/w09.json");
        assertEquals(30, positive);
        assertEquals(20, reverse);
    }

    private static int run(String relative) throws Exception {
        Path path = Paths.get("..", relative);
        if (!Files.exists(path)) {
            path = Paths.get(relative);
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> cases = mapper.readValue(path.toFile(), new TypeReference<List<Map<String, Object>>>() {
        });
        String file = path.getFileName().toString();
        for (Map<String, Object> item : cases) {
            assertFalse(String.valueOf(item.get("id")).isEmpty(), file);
            if ("w07.json".equals(file)) {
                @SuppressWarnings("unchecked")
                List<Object> tools = (List<Object>) item.get("tools");
                assertFalse(tools.isEmpty(), String.valueOf(item.get("id")));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) item.get("input");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) item.get("expect");
            assertMatches(String.valueOf(item.get("id")), expect, evaluate(file, item, input));
        }
        return cases.size();
    }

    private static Map<String, Object> evaluate(String file, Map<String, Object> item, Map<String, Object> input) {
        String op = item.get("op") == null ? "decide" : String.valueOf(item.get("op"));
        if ("w06.json".equals(file)) {
            return ReverseRules.evaluate(String.valueOf(item.get("id")), input);
        }
        if ("w08.json".equals(file)) {
            return ProfilePolicy.evaluate(op, input);
        }
        if ("w09.json".equals(file)) {
            return "passage".equals(item.get("op")) ? PassagePolicy.evaluate(input) : RetrievalPolicy.evaluate(input);
        }
        return TrajectoryPolicy.evaluate(op, input);
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
