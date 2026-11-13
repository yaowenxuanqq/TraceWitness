package com.tracewitness.platform.agent;

import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.safety.ReverseRules;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOG = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_ROUNDS = 4;

    private final PlatformStore store;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;
    private final PythonAgentClient client;
    private final ScenarioService scenarios;
    private final AppProperties properties;
    private final InvestigationTools tools;

    public AgentService(PlatformStore store, TimeSource timeSource, TransactionTemplate transactions,
                        PythonAgentClient client, ScenarioService scenarios, AppProperties properties,
                        InvestigationTools tools) {
        this.store = store;
        this.timeSource = timeSource;
        this.transactions = transactions;
        this.client = client;
        this.scenarios = scenarios;
        this.properties = properties;
        this.tools = tools;
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
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        List<AgentModels.ToolSelection> selected = new ArrayList<AgentModels.ToolSelection>();
        Set<String> seen = new LinkedHashSet<String>();
        boolean retried = false;
        try {
            for (int round = 0; round < MAX_ROUNDS; round++) {
                List<AgentModels.ToolSelection> calls = client.selectTools(
                        runId, manifest.getClockMode(), now, tick, task, InvestigationTools.NAMES, results);
                List<AgentModels.ToolSelection> fresh = new ArrayList<AgentModels.ToolSelection>();
                for (AgentModels.ToolSelection call : calls) {
                    if (call == null || call.toolName == null) {
                        continue;
                    }
                    String key = call.toolName + ":" + store.writeJson(call.arguments == null ? new LinkedHashMap<String, Object>() : call.arguments);
                    if (!seen.add(key)) {
                        continue;
                    }
                    fresh.add(call);
                }
                if (fresh.isEmpty()) {
                    break;
                }
                for (AgentModels.ToolSelection call : fresh) {
                    selected.add(call);
                    results.add(tools.execute(runId, call.toolName, call.arguments));
                }
            }
            Set<String> allowed = sourceIds(results);
            AgentModels.DraftReport report = client.draftReport(
                    runId, manifest.getClockMode(), now, tick, task, results, null);
            boolean invalid = CitationCheck.invalid(allowed, report.sourceIds, report.insufficientEvidence);
            if (invalid) {
                retried = true;
                report = client.draftReport(
                        runId, manifest.getClockMode(), now, tick, task, results,
                        "上次引用非法。只能使用工具返回的 ID。没有可用 ID 时必须标记证据不足。");
                invalid = CitationCheck.invalid(allowed, report.sourceIds, report.insufficientEvidence);
            }
            persist(runId, task, now, selected, results, report, invalid, retried);
        } catch (PythonAgentClient.AgentCallException exception) {
            LOG.warn("investigation failed run={} type={}", runId, exception.failureType());
            transactions.executeWithoutResult(status -> saveFailure(
                    runId, task, now, exception.failureType(), exception.publicDetail()));
        }
    }

    private void persist(String runId, String task, String now, List<AgentModels.ToolSelection> selected,
                         List<Map<String, Object>> results, AgentModels.DraftReport report,
                         boolean invalid, boolean retried) {
        transactions.executeWithoutResult(status -> {
            if (store.findAgentRunByRun(runId) != null) {
                return;
            }
            String agentId = store.nextId(runId, "agent");
            for (int i = 0; i < selected.size(); i++) {
                AgentModels.ToolSelection call = selected.get(i);
                Map<String, Object> result = results.get(i);
                store.insertStep(store.nextId(runId, "step"), agentId, runId, "select", call.toolName, now);
                store.insertToolCall(
                        store.nextId(runId, "tool"),
                        agentId,
                        runId,
                        call.toolName,
                        store.writeJson(call.arguments == null ? new LinkedHashMap<String, Object>() : call.arguments),
                        store.writeJson(result),
                        store.writeJson(sourceIds(Collections.singletonList(result))),
                        now);
                store.insertStep(store.nextId(runId, "step"), agentId, runId, "tool", call.toolName, now);
            }
            if (retried) {
                store.insertStep(store.nextId(runId, "step"), agentId, runId, "retry", "citation_invalid", now);
            }
            List<Map<String, Object>> actions = gateActions(report.requestedActions);
            if (invalid) {
                insertRun(agentId, runId, task, now, "证据不足，无法结案", true,
                        new BigDecimal("0.00"), Collections.<String>emptyList(), "citation_invalid", actions);
                store.insertStep(store.nextId(runId, "step"), agentId, runId, "report", "citation_invalid", now);
            } else {
                boolean empty = report.sourceIds == null || report.sourceIds.isEmpty();
                String conclusion = empty && report.insufficientEvidence ? "证据不足" : report.conclusion;
                insertRun(agentId, runId, task, now, conclusion, report.insufficientEvidence || empty,
                        confidence(report.confidence), empty ? Collections.<String>emptyList() : report.sourceIds,
                        null, actions);
                store.insertStep(store.nextId(runId, "step"), agentId, runId, "report", "ok", now);
            }
        });
    }

    private List<Map<String, Object>> gateActions(List<?> requested) {
        List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
        if (requested == null) {
            return actions;
        }
        for (Object item : requested) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> raw = (Map<?, ?>) item;
            String action = raw.get("action") == null ? "" : String.valueOf(raw.get("action"));
            Map<String, Object> decision = ReverseRules.evaluate("high_risk", Collections.<String, Object>singletonMap("action", action));
            Map<String, Object> stored = new LinkedHashMap<String, Object>();
            stored.put("action", action);
            stored.put("status", decision.get("status"));
            stored.put("executed", decision.get("executed"));
            actions.add(stored);
        }
        return actions;
    }

    private void saveFailure(String runId, String task, String now, String failureType, String detail) {
        if (store.findAgentRunByRun(runId) != null) {
            return;
        }
        String agentId = store.nextId(runId, "agent");
        store.insertStep(store.nextId(runId, "step"), agentId, runId, "select", detail, now);
        insertRun(agentId, runId, task, now, "证据不足，无法结案", true,
                new BigDecimal("0.00"), Collections.<String>emptyList(), failureType, Collections.emptyList());
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
    private static Set<String> sourceIds(List<Map<String, Object>> results) {
        Set<String> ids = new LinkedHashSet<String>();
        for (Map<String, Object> result : results) {
            Object value = result.get("source_ids");
            if (!(value instanceof List)) {
                continue;
            }
            for (Object id : (List<Object>) value) {
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        return ids;
    }

    private static BigDecimal confidence(BigDecimal value) {
        return value == null ? new BigDecimal("0.00") : Quantities.persisted(value);
    }
}
