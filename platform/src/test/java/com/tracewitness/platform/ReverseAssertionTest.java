package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.safety.ReverseRules;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReverseAssertionTest {
    @Test
    void tenReverseAssertionsHold() throws Exception {
        Path path = Paths.get("..", "eval", "reverse", "w06.json");
        if (!Files.exists(path)) {
            path = Paths.get("eval", "reverse", "w06.json");
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
            Map<String, Object> actual = ReverseRules.evaluate(id, input);
            for (Map.Entry<String, Object> entry : expect.entrySet()) {
                assertEquals(entry.getValue(), actual.get(entry.getKey()), id + " " + entry.getKey());
            }
        }
    }
}
