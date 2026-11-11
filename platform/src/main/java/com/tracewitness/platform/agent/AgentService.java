package com.tracewitness.platform.agent;

import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgentService {
    private final PlatformStore store;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;
    private final PythonAgentClient client;
    private final ScenarioService scenarios;
    private final AppProperties properties;

    public AgentService(PlatformStore store, TimeSource timeSource, TransactionTemplate transactions,
                        PythonAgentClient client, ScenarioService scenarios, AppProperties properties) {
        this.store = store;
        this.timeSource = timeSource;
        this.transactions = transactions;
        this.client = client;
        this.scenarios = scenarios;
        this.properties = properties;
    }

    public void investigate() {
        scenarios.ensure();
        String runId = scenarios.runId();
        RunManifest manifest = store.findManifest(runId);
        if (manifest == null) {
            throw new IllegalStateException("run manifest missing");
        }
        timeSource.load(ClockMode.valueOf(manifest.getClockMode()), manifest.getEpochStart(), manifest.getTickMs(), manifest.getTick());
        if (store.findAgentRunByRun(runId) != null) {
            return;
        }
        String now = timeSource.formatNow();
        long tick = timeSource.tick();
        String task = properties.getEval().getTask();
        try {
            List<AgentModels.ToolSelection> calls = client.selectTools(
                    runId, manifest.getClockMode(), now, tick, task,
                    Collections.singletonList("search_events"), Collections.<Map<String, Object>>emptyList());
            if (calls.isEmpty() || !"search_events".equals(calls.get(0).toolName)) {
                saveFailure(runId, task, now, "tool_failed", "未执行 search_events");
                return;
            }
            Map<String, Object> result = searchEvents(runId, calls.get(0).arguments);
            AgentModels.DraftReport report = client.draftReport(
                    runId, manifest.getClockMode(), now, tick, task,
                    Collections.singletonList(result), null);
            persist(runId, task, now, calls.get(0), result, report);
        } catch (RuntimeException exception) {
            saveFailure(runId, task, now, "model_failed", "模型调用失败");
        }
    }

    private Map<String, Object> searchEvents(String runId, Map<String, Object> arguments) {
        int limit = 5;
        if (arguments != null && arguments.get("limit") instanceof Number) {
            limit = Math.min(20, Math.max(1, ((Number) arguments.get("limit")).intValue()));
        }
        List<Map<String, Object>> events = store.listEvents(runId, limit);
        List<String> sourceIds = new ArrayList<String>();
        for (Map<String, Object> event : events) {
            sourceIds.add(String.valueOf(event.get("id")));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tool_name", "search_events");
        result.put("events", events);
        result.put("source_ids", sourceIds);
        return result;
    }

    private void persist(String runId, String task, String now, AgentModels.ToolSelection call,
                         Map<String, Object> result, AgentModels.DraftReport report) {
        Set<String> allowed = sourceIds(result);
        boolean invalid = CitationCheck.invalid(allowed, report.sourceIds, report.insufficientEvidence);
        transactions.executeWithoutResult(status -> {
            if (store.findAgentRunByRun(runId) != null) {
                return;
            }
            String agentId = store.nextId(runId, "agent");
            store.insertStep(store.nextId(runId, "step"), agentId, runId, "select", call.toolName, now);
            store.insertToolCall(
                    store.nextId(runId, "tool"),
                    agentId,
                    runId,
                    call.toolName,
                    store.writeJson(call.arguments == null ? new LinkedHashMap<String, Object>() : call.arguments),
                    store.writeJson(result),
                    store.writeJson(new ArrayList<String>(allowed)),
                    now);
            store.insertStep(store.nextId(runId, "step"), agentId, runId, "tool", call.toolName, now);
            if (invalid) {
                insertRun(agentId, runId, task, now, "证据不足，无法结案", true,
                        new BigDecimal("0.00"), Collections.<String>emptyList(), "citation_invalid",
                        Collections.emptyList());
            } else {
                insertRun(agentId, runId, task, now, report.conclusion, report.insufficientEvidence,
                        confidence(report.confidence), report.sourceIds, null, report.requestedActions);
            }
            store.insertStep(store.nextId(runId, "step"), agentId, runId, "report",
                    invalid ? "citation_invalid" : "ok", now);
        });
    }

    private void saveFailure(String runId, String task, String now, String failureType, String detail) {
        if (store.findAgentRunByRun(runId) != null) {
            return;
        }
        transactions.executeWithoutResult(status -> {
            String agentId = store.nextId(runId, "agent");
            store.insertStep(store.nextId(runId, "step"), agentId, runId, "select", detail, now);
            insertRun(agentId, runId, task, now, "证据不足，无法结案", true,
                    new BigDecimal("0.00"), Collections.<String>emptyList(), failureType, Collections.emptyList());
        });
    }

    private void insertRun(String agentId, String runId, String task, String createdAt, String conclusion,
                           boolean insufficient, BigDecimal confidence, List<String> sourceIds,
                           String failureType, List<?> requestedActions) {
        store.insertAgentRun(
                agentId,
                runId,
                task,
                "COMPLETED",
                properties.mock(),
                conclusion,
                insufficient,
                confidence,
                store.writeJson(sourceIds == null ? Collections.<String>emptyList() : sourceIds),
                failureType,
                store.writeJson(requestedActions == null ? Collections.emptyList() : requestedActions),
                createdAt);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> sourceIds(Map<String, Object> result) {
        Set<String> ids = new LinkedHashSet<String>();
        Object value = result.get("source_ids");
        if (!(value instanceof List)) {
            return ids;
        }
        for (Object id : (List<Object>) value) {
            if (id != null) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    private static BigDecimal confidence(BigDecimal value) {
        return value == null ? new BigDecimal("0.00") : Quantities.persisted(value);
    }
}
