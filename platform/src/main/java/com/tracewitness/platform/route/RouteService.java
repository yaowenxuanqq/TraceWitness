package com.tracewitness.platform.route;

import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.DecisionStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import com.tracewitness.platform.vision.StructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RouteService {
    private static final List<String> OBSERVATION_CASES = Arrays.asList("live", "gate_failure", "track_break");

    private final DecisionStore decisions;
    private final PlatformStore platform;
    private final ScenarioService scenarios;
    private final StructureService structure;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;
    private final RoutePolicy policy = new RoutePolicy();

    public RouteService(DecisionStore decisions, PlatformStore platform, ScenarioService scenarios,
                        StructureService structure, TimeSource timeSource, TransactionTemplate transactions) {
        this.decisions = decisions;
        this.platform = platform;
        this.scenarios = scenarios;
        this.structure = structure;
        this.timeSource = timeSource;
        this.transactions = transactions;
    }

    public void ensure() {
        String runId = scenarios.runId();
        if (decisions.count(runId) > 0) {
            return;
        }
        List<Map<String, Object>> observations = structure.list();
        if (observations.isEmpty()) {
            return;
        }
        RunManifest manifest = platform.findManifest(runId);
        if (manifest == null) {
            return;
        }
        ClockMode clockMode = ClockMode.valueOf(manifest.getClockMode());
        timeSource.load(clockMode, manifest.getEpochStart(), manifest.getTickMs(), manifest.getTick());
        Map<String, Map<String, Object>> byCase = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> observation : observations) {
            byCase.put(String.valueOf(observation.get("caseKey")), observation);
        }
        List<Pending> pending = new ArrayList<Pending>();
        for (String caseKey : OBSERVATION_CASES) {
            Map<String, Object> observation = byCase.get(caseKey);
            if (observation == null) {
                throw new IllegalStateException("missing observation for " + caseKey);
            }
            pending.add(new Pending(caseKey, String.valueOf(observation.get("id")), facts(caseKey, clockMode, observation)));
        }
        pending.add(new Pending("unknown_business", null, unknownFacts(clockMode)));
        String decidedAt = timeSource.formatNow();
        transactions.execute(status -> {
            if (decisions.count(runId) > 0) {
                return null;
            }
            for (Pending item : pending) {
                RouteDecision decision = policy.decide(item.facts);
                String id = platform.nextId(runId, "decision");
                decisions.insert(id, runId, item.caseKey, item.observationId, decision.policyVersion, clockMode.name(),
                        item.facts.business, item.facts.faceQuality, item.facts.bodyQuality, item.facts.continuity,
                        item.facts.historyCount, item.facts.qObserved, item.facts.qScripted, decision.qInput,
                        decision.thetaUEff, decision.uncertainty, decision.degraded, decision.route, decision.reason,
                        decision.fusion, decision.conclusion, decision.cloudCalled, decision.localLabel,
                        decision.localScore, decision.cloudLabel, decision.cloudScore, decidedAt);
            }
            return null;
        });
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : decisions.list(scenarios.runId())) {
            rows.add(view(row));
        }
        return rows;
    }

    public void verifyReplay() {
        for (Map<String, Object> row : decisions.list(scenarios.runId())) {
            RouteDecision again = policy.decide(factsFrom(row));
            RouteDecision stored = stored(row);
            if (!stored.same(again)) {
                throw new IllegalStateException("route replay diverged for " + row.get("caseKey"));
            }
        }
    }

    private static RouteFacts facts(String caseKey, ClockMode clockMode, Map<String, Object> observation) {
        RouteWindows.DeviceWindow window = RouteWindows.get(caseKey);
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) observation.get("attributes");
        return new RouteFacts(
                caseKey,
                clockMode,
                (BigDecimal) observation.get("faceQuality"),
                (BigDecimal) observation.get("bodyQuality"),
                RoutePolicy.continuity(String.valueOf(observation.get("mode"))),
                window.samples.size(),
                "incident_review",
                RouteWindows.Q_OBSERVED,
                RouteWindows.Q_SCRIPTED,
                String.valueOf(attributes.get("upper_color")),
                window.cloudLabel,
                window.cloudScore);
    }

    private static RouteFacts unknownFacts(ClockMode clockMode) {
        RouteWindows.DeviceWindow window = RouteWindows.get("unknown_business");
        return new RouteFacts("unknown_business", clockMode, new BigDecimal("0.10"), new BigDecimal("0.10"),
                new BigDecimal("0.20"), window.samples.size(), "visitor_chat", RouteWindows.Q_OBSERVED,
                RouteWindows.Q_SCRIPTED, "light", window.cloudLabel, window.cloudScore);
    }

    private static RouteFacts factsFrom(Map<String, Object> row) {
        String localLabel = (String) row.get("localLabel");
        return new RouteFacts(
                String.valueOf(row.get("caseKey")),
                ClockMode.valueOf(String.valueOf(row.get("clockMode"))),
                (BigDecimal) row.get("faceQuality"),
                (BigDecimal) row.get("bodyQuality"),
                (BigDecimal) row.get("continuity"),
                ((Number) row.get("historyCount")).intValue(),
                String.valueOf(row.get("business")),
                (BigDecimal) row.get("qObserved"),
                (BigDecimal) row.get("qScripted"),
                localLabel == null ? "unused" : localLabel,
                (String) row.get("cloudLabel"),
                (BigDecimal) row.get("cloudScore"));
    }

    private static RouteDecision stored(Map<String, Object> row) {
        return new RouteDecision(
                String.valueOf(row.get("policyVersion")),
                (BigDecimal) row.get("uncertainty"),
                (BigDecimal) row.get("qInput"),
                (BigDecimal) row.get("thetaUEff"),
                Boolean.TRUE.equals(row.get("degraded")),
                String.valueOf(row.get("route")),
                String.valueOf(row.get("reason")),
                String.valueOf(row.get("fusion")),
                String.valueOf(row.get("conclusion")),
                Boolean.TRUE.equals(row.get("cloudCalled")),
                (String) row.get("localLabel"),
                (BigDecimal) row.get("localScore"),
                (String) row.get("cloudLabel"),
                (BigDecimal) row.get("cloudScore"));
    }

    private static Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", row.get("id"));
        body.put("caseKey", row.get("caseKey"));
        body.put("observationId", row.get("observationId"));
        body.put("policyVersion", row.get("policyVersion"));
        body.put("business", row.get("business"));
        body.put("historyCount", row.get("historyCount"));
        body.put("degraded", row.get("degraded"));
        body.put("qObserved", row.get("qObserved"));
        body.put("qScripted", row.get("qScripted"));
        body.put("qInput", row.get("qInput"));
        body.put("thetaUEff", row.get("thetaUEff"));
        body.put("uncertainty", row.get("uncertainty"));
        body.put("route", row.get("route"));
        body.put("reason", row.get("reason"));
        body.put("fusion", row.get("fusion"));
        body.put("conclusion", row.get("conclusion"));
        body.put("cloudCalled", row.get("cloudCalled"));
        body.put("localLabel", row.get("localLabel"));
        body.put("cloudLabel", row.get("cloudLabel"));
        body.put("decidedAt", row.get("decidedAt"));
        return body;
    }

    private static final class Pending {
        private final String caseKey;
        private final String observationId;
        private final RouteFacts facts;

        private Pending(String caseKey, String observationId, RouteFacts facts) {
            this.caseKey = caseKey;
            this.observationId = observationId;
            this.facts = facts;
        }
    }
}
