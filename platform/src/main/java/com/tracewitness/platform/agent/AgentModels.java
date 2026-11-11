package com.tracewitness.platform.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class AgentModels {
    private AgentModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolSelection {
        @JsonProperty("tool_name")
        public String toolName;
        public Map<String, Object> arguments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolPlan {
        @JsonProperty("tool_calls")
        public List<ToolSelection> toolCalls;
        @JsonProperty("tool_name")
        public String toolName;
        public Map<String, Object> arguments;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DraftReport {
        public String conclusion;
        @JsonProperty("source_ids")
        public List<String> sourceIds;
        public BigDecimal confidence;
        @JsonProperty("insufficient_evidence")
        public boolean insufficientEvidence;
        @JsonProperty("requested_actions")
        public List<Object> requestedActions;
    }
}
