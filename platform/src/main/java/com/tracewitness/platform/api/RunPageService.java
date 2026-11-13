package com.tracewitness.platform.api;

import com.tracewitness.platform.route.RouteService;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.DecisionStore;
import com.tracewitness.platform.store.ObservationStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.store.PluginStore;
import com.tracewitness.platform.store.RetrievalStore;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.store.TrajectoryStore;
import com.tracewitness.platform.vision.StructureService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RunPageService {
    private static final int MAX_LIMIT = 200;
    private static final int MAX_OFFSET = 100000;

    private final ScenarioService scenarios;
    private final PlatformStore store;
    private final TrackStore tracks;
    private final ObservationStore observations;
    private final DecisionStore decisions;
    private final StructureService structure;
    private final RouteService routes;
    private final ProfileStore profiles;
    private final RetrievalStore retrieval;
    private final TrajectoryStore trajectories;
    private final PluginStore plugins;

    public RunPageService(ScenarioService scenarios, PlatformStore store, TrackStore tracks,
                          ObservationStore observations, DecisionStore decisions, StructureService structure,
                          RouteService routes, ProfileStore profiles, RetrievalStore retrieval,
                          TrajectoryStore trajectories, PluginStore plugins) {
        this.scenarios = scenarios;
        this.store = store;
        this.tracks = tracks;
        this.observations = observations;
        this.decisions = decisions;
        this.structure = structure;
        this.routes = routes;
        this.profiles = profiles;
        this.retrieval = retrieval;
        this.trajectories = trajectories;
        this.plugins = plugins;
    }

    public ResponseEntity<Map<String, Object>> page(String section, int requestedLimit, int requestedOffset) {
        int limit = requestedLimit < 1 ? 1 : Math.min(requestedLimit, MAX_LIMIT);
        int offset = requestedOffset < 0 ? 0 : Math.min(requestedOffset, MAX_OFFSET);
        String runId = scenarios.runId();
        List<?> items;
        int total;
        if ("events".equals(section)) {
            items = store.listEvents(runId, limit, offset);
            total = store.countEvents(runId);
        } else if ("steps".equals(section) || "tool-calls".equals(section)) {
            Map<String, Object> agent = store.findAgentRunByRun(runId);
            if (agent == null) {
                items = Collections.emptyList();
                total = 0;
            } else if ("steps".equals(section)) {
                String agentId = String.valueOf(agent.get("id"));
                items = store.listSteps(agentId, limit, offset);
                total = store.countSteps(agentId);
            } else {
                String agentId = String.valueOf(agent.get("id"));
                items = store.listToolCalls(agentId, limit, offset);
                total = store.countToolCalls(agentId);
            }
        } else if ("tracks".equals(section)) {
            items = tracks.listTracks(runId, limit, offset, MAX_LIMIT);
            total = tracks.countTracks(runId);
        } else if ("observations".equals(section)) {
            items = structure.list(limit, offset);
            total = observations.count(runId);
        } else if ("decisions".equals(section)) {
            items = routes.list(limit, offset);
            total = decisions.count(runId);
        } else if ("profiles".equals(section)) {
            items = profiles.listProfiles(runId, limit, offset);
            total = profiles.countProfiles(runId);
        } else if ("tickets".equals(section)) {
            items = profiles.listTickets(runId, limit, offset);
            total = profiles.countTable("merge_ticket", runId);
        } else if ("queries".equals(section)) {
            items = retrieval.listQueries(runId, limit, offset);
            total = retrieval.countQueries(runId);
        } else if ("passages".equals(section)) {
            items = retrieval.listPassages(runId, limit, offset);
            total = retrieval.countTable("passage_case", runId);
        } else if ("trajectories".equals(section)) {
            items = trajectories.listTrajectories(runId, limit, offset);
            total = trajectories.countTrajectories(runId);
        } else if ("packages".equals(section)) {
            items = trajectories.listPackages(runId, limit, offset);
            total = trajectories.countTable("evidence_package", runId);
        } else if ("hard-cases".equals(section)) {
            items = trajectories.listHardCases(runId, limit, offset);
            total = trajectories.countTable("hard_case", runId);
        } else if ("plugins".equals(section)) {
            items = plugins.listManifests(runId, limit, offset);
            total = plugins.count(runId);
        } else {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("section", section);
        body.put("items", items);
        body.put("total", Integer.valueOf(total));
        body.put("limit", Integer.valueOf(limit));
        body.put("offset", Integer.valueOf(offset));
        body.put("truncated", Boolean.valueOf(offset + items.size() < total));
        return ResponseEntity.ok(body);
    }
}
