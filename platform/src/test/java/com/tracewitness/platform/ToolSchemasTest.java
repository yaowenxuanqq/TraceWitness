package com.tracewitness.platform;

import com.tracewitness.platform.agent.ToolSchemas;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToolSchemasTest {
    @Test
    void extraFieldIsRejectedAndKnownCallPasses() {
        Map<String, Object> extra = new LinkedHashMap<String, Object>();
        extra.put("limit", Integer.valueOf(5));
        extra.put("unexpected", "accepted?");
        assertEquals("unexpected_field", ToolSchemas.reject("search_events", extra));

        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        assertEquals("missing_field", ToolSchemas.reject("get_event_detail", missing));

        assertNull(ToolSchemas.reject("search_events", Collections.<String, Object>singletonMap("limit", Integer.valueOf(5))));
    }
}
