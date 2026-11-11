package com.tracewitness.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.plugin.PluginPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPolicyTest {
    @Test
    void signatureIsCheckedBeforePayloadHash() throws Exception {
        Path path = Paths.get("..", "eval/positive/w11.json");
        if (!Files.exists(path)) {
            path = Paths.get("eval/positive/w11.json");
        }
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> cases = mapper.readValue(path.toFile(), new TypeReference<List<Map<String, Object>>>() {
        });
        assertEquals(5, cases.size());
        for (Map<String, Object> item : cases) {
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) item.get("expect");
            Map<String, Object> actual = PluginPolicy.evaluate(item);
            for (Map.Entry<String, Object> entry : expect.entrySet()) {
                assertEquals(entry.getValue(), actual.get(entry.getKey()), item.get("id") + " " + entry.getKey());
            }
            assertEquals(PluginPolicy.CHECK_ORDER, actual.get("check_order"));
        }
    }

    @Test
    void oneThousandChecksStayInsideTwoSeconds() {
        Map<String, Object> report = PluginPolicy.bench(1000);
        assertEquals(Integer.valueOf(1000), report.get("accepted"));
        assertTrue(((Number) report.get("elapsed_ms")).longValue() < 2000L, String.valueOf(report.get("elapsed_ms")));
    }
}
