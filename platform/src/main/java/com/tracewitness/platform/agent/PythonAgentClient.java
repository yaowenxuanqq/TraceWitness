package com.tracewitness.platform.agent;

import com.tracewitness.platform.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PythonAgentClient {
    public static class AgentCallException extends RuntimeException {
        private final String failureType;
        private final String publicDetail;

        AgentCallException(String message, String failureType, String publicDetail, Throwable cause) {
            super(message, cause);
            this.failureType = failureType;
            this.publicDetail = publicDetail;
        }

        public String failureType() {
            return failureType;
        }

        public String publicDetail() {
            return publicDetail;
        }
    }

    private final AppProperties properties;
    private final RestTemplate rest;

    public PythonAgentClient(AppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(60000);
        this.rest = new RestTemplate(factory);
        this.rest.getInterceptors().add(authorization());
    }

    private ClientHttpRequestInterceptor authorization() {
        return (request, body, execution) -> {
            String token = properties.getServiceToken();
            if (token != null && !token.trim().isEmpty()) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
            }
            return execution.execute(request, body);
        };
    }

    public List<AgentModels.ToolSelection> selectTools(String runId, String clockMode, String requestTimestamp,
                                                       long tick, String task, List<String> tools,
                                                       List<Map<String, Object>> toolResults) {
        Map<String, Object> body = clock(runId, clockMode, requestTimestamp, tick);
        body.put("task", task);
        body.put("tools", tools);
        body.put("tool_results", toolResults);
        body.put("model_seed", properties.getEval().getMasterSeed());
        AgentModels.ToolPlan plan = post("/v1/agent/select-tool", body, AgentModels.ToolPlan.class);
        List<AgentModels.ToolSelection> calls = new java.util.ArrayList<AgentModels.ToolSelection>();
        if (plan == null) {
            return calls;
        }
        if (plan.toolCalls != null) {
            calls.addAll(plan.toolCalls);
        } else if (plan.toolName != null) {
            AgentModels.ToolSelection one = new AgentModels.ToolSelection();
            one.toolName = plan.toolName;
            one.arguments = plan.arguments;
            calls.add(one);
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> trackSample(String runId, String clockMode, String requestTimestamp,
                                            long tick, int frames) {
        Map<String, Object> body = clock(runId, clockMode, requestTimestamp, tick);
        body.put("frames", frames);
        body.put("frame_width", 1920);
        body.put("frame_height", 1080);
        return post("/v1/vision/track", body, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> structure(String runId, String clockMode, String requestTimestamp, long tick,
                                          String caseKey, List<Map<String, Object>> points, List<Map<String, Object>> faults) {
        Map<String, Object> body = clock(runId, clockMode, requestTimestamp, tick);
        body.put("case_key", caseKey);
        body.put("points", points);
        body.put("faults", faults);
        return post("/v1/vision/structure", body, Map.class);
    }

    public AgentModels.DraftReport draftReport(String runId, String clockMode, String requestTimestamp,
                                                long tick, String task, List<Map<String, Object>> toolResults,
                                                String citationHint) {
        Map<String, Object> body = clock(runId, clockMode, requestTimestamp, tick);
        body.put("task", task);
        body.put("tool_results", toolResults);
        body.put("model_seed", properties.getEval().getMasterSeed());
        if (citationHint != null) {
            body.put("citation_hint", citationHint);
        }
        return post("/v1/agent/report", body, AgentModels.DraftReport.class);
    }

    private Map<String, Object> clock(String runId, String clockMode, String requestTimestamp, long tick) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("run_id", runId);
        body.put("clock_mode", clockMode);
        body.put("request_timestamp", requestTimestamp);
        body.put("tick", tick);
        return body;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return rest.postForObject(properties.getAiBaseUrl() + path, body, type);
        } catch (HttpStatusCodeException exception) {
            int code = exception.getRawStatusCode();
            boolean authentication = code == HttpStatus.UNAUTHORIZED.value() || code == HttpStatus.FORBIDDEN.value();
            String failure = authentication ? "service_auth_failed" : "model_failed";
            String detail = authentication ? "服务认证失败" : "模型调用失败";
            throw new AgentCallException("AI " + path + " -> " + code, failure, detail, exception);
        } catch (RestClientException exception) {
            throw new AgentCallException("AI 服务不可用: " + path, "model_failed", "模型调用失败", exception);
        }
    }
}
