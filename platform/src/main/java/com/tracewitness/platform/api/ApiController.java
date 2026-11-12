package com.tracewitness.platform.api;

import com.tracewitness.platform.api.dto.ActorDecisionRequest;
import com.tracewitness.platform.api.dto.EvidenceVerifyRequest;
import com.tracewitness.platform.api.dto.HardLabelRequest;
import com.tracewitness.platform.api.dto.RollbackRequest;
import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.config.LocalToken;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.trajectory.TrajectoryPolicy;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.plugin.PluginService;
import com.tracewitness.platform.route.RouteService;
import com.tracewitness.platform.profile.ProfileService;
import com.tracewitness.platform.retrieval.RetrievalService;
import com.tracewitness.platform.trajectory.TrajectoryService;
import com.tracewitness.platform.vision.StructureService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AppProperties properties;
    private final ScenarioService scenarios;
    private final PlatformStore store;
    private final TrackStore tracks;
    private final StructureService structure;
    private final RouteService routes;
    private final ProfileService profiles;
    private final RetrievalService retrieval;
    private final TrajectoryService trajectories;
    private final PluginService plugins;
    private final LocalToken token;

    public ApiController(AppProperties properties, ScenarioService scenarios, PlatformStore store, TrackStore tracks,
                         StructureService structure, RouteService routes, ProfileService profiles,
                         RetrievalService retrieval, TrajectoryService trajectories, PluginService plugins,
                         LocalToken token) {
        this.properties = properties;
        this.scenarios = scenarios;
        this.store = store;
        this.tracks = tracks;
        this.structure = structure;
        this.routes = routes;
        this.profiles = profiles;
        this.retrieval = retrieval;
        this.trajectories = trajectories;
        this.plugins = plugins;
        this.token = token;
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(HttpServletRequest request) {
        if (!loopback(request)) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("token", token.value());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("service", "platform");
        body.put("mode", properties.getMode());
        body.put("banner", properties.banner());
        return body;
    }

    @GetMapping("/mode")
    public Map<String, Object> mode() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("mode", properties.getMode());
        body.put("mockMode", properties.mock());
        body.put("banner", properties.banner());
        return body;
    }

    @GetMapping("/tools")
    public List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new ArrayList<Map<String, Object>>();
        tools.add(tool("search_events", true));
        tools.add(tool("get_event_detail", true));
        tools.add(tool("search_person", true));
        tools.add(tool("build_trajectory", true));
        tools.add(tool("find_companions", true));
        tools.add(tool("export_evidence", true));
        return tools;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        String runId = scenarios.runId();
        RunManifest manifest = store.findManifest(runId);
        Map<String, Object> agent = store.findAgentRunByRun(runId);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("mode", properties.getMode());
        body.put("mockMode", properties.mock());
        body.put("banner", properties.banner());
        body.put("run", manifest == null ? null : manifestView(manifest));
        body.put("sites", store.listSites(runId));
        body.put("devices", store.listDevices(runId));
        body.put("events", store.listEvents(runId, 20));
        body.put("outbox", store.outboxCounts(runId));
        body.put("agentRun", agent);
        body.put("steps", agent == null ? Collections.emptyList() : store.listSteps(String.valueOf(agent.get("id"))));
        body.put("toolCalls", agent == null ? Collections.emptyList() : store.listToolCalls(String.valueOf(agent.get("id"))));
        body.put("tracks", tracks.listTracks(runId));
        body.put("observations", structure.list());
        body.put("decisions", routes.list());
        Map<String, Object> people = profiles.overview();
        body.put("doors", people.get("doors"));
        body.put("doorEdges", people.get("edges"));
        body.put("profiles", people.get("profiles"));
        body.put("tickets", people.get("tickets"));
        body.put("audits", people.get("audits"));
        Map<String, Object> search = retrieval.overview();
        body.put("queries", search.get("queries"));
        body.put("passages", search.get("passages"));
        Map<String, Object> trail = trajectories.overview();
        body.put("trajectories", trail.get("trajectories"));
        body.put("companions", trail.get("companions"));
        body.put("packages", trail.get("packages"));
        body.put("hardCases", trail.get("hardCases"));
        body.put("compares", trail.get("compares"));
        Map<String, Object> plugin = plugins.overview();
        body.put("plugins", plugin.get("plugins"));
        body.put("pluginHealth", plugin.get("health"));
        body.put("rollbacks", plugin.get("rollbacks"));
        body.put("tools", tools());
        return body;
    }

    @GetMapping("/sites")
    public List<Map<String, Object>> sites() {
        return store.listSites(scenarios.runId());
    }

    @GetMapping("/devices")
    public List<Map<String, Object>> devices() {
        return store.listDevices(scenarios.runId());
    }

    @GetMapping("/events")
    public List<Map<String, Object>> events() {
        return store.listEvents(scenarios.runId(), 20);
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<Map<String, Object>> event(@PathVariable String id) {
        Map<String, Object> event = store.findEvent(id);
        if (event == null || !scenarios.runId().equals(event.get("runId"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(event);
    }

    @GetMapping("/agent/runs")
    public List<Map<String, Object>> agentRuns() {
        return store.listAgentRuns();
    }

    @GetMapping("/agent/runs/{id}")
    public ResponseEntity<Map<String, Object>> agentRun(@PathVariable String id) {
        Map<String, Object> run = store.findAgentRunById(id);
        if (run == null || !scenarios.runId().equals(run.get("runId"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(run);
    }

    @GetMapping("/agent/runs/{id}/tool-calls")
    public ResponseEntity<List<Map<String, Object>>> toolCalls(@PathVariable String id) {
        Map<String, Object> run = store.findAgentRunById(id);
        if (run == null || !scenarios.runId().equals(run.get("runId"))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(store.listToolCalls(id));
    }

    @GetMapping("/observations/{id}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable String id) throws java.io.IOException {
        byte[] body = structure.readThumbnail(id);
        if (body == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(body);
    }

    @PostMapping("/profiles/tickets/{id}/decision")
    public ResponseEntity<Map<String, Object>> decideTicket(@PathVariable String id, @RequestBody ActorDecisionRequest body) {
        ResponseEntity<Map<String, Object>> rejected = decisionRequest(body);
        if (rejected != null) {
            return rejected;
        }
        return applied(profiles.decideTicket(id, body.getDecision(), body.getActor().trim()));
    }

    @PostMapping("/alerts/{id}/decision")
    public ResponseEntity<Map<String, Object>> decideAlert(@PathVariable String id, @RequestBody ActorDecisionRequest body) {
        ResponseEntity<Map<String, Object>> rejected = decisionRequest(body);
        if (rejected != null) {
            return rejected;
        }
        return applied(retrieval.decideAlert(id, body.getDecision(), body.getActor().trim()));
    }

    @PostMapping("/evidence/{id}/verify")
    public ResponseEntity<Map<String, Object>> verifyEvidence(@PathVariable String id, @RequestBody EvidenceVerifyRequest body) {
        Map<String, Object> result = trajectories.verify(id, body.getManifest());
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/hard-cases/{id}/label")
    public ResponseEntity<Map<String, Object>> labelHardCase(@PathVariable String id, @RequestBody HardLabelRequest body) {
        if (blank(body.getLabel()) || blank(body.getActor())) {
            return invalid("missing_field");
        }
        if (!TrajectoryPolicy.LABELS.contains(body.getLabel())) {
            return invalid("invalid_field");
        }
        return applied(trajectories.label(id, body.getLabel(), body.getActor().trim()));
    }

    @GetMapping("/hard-cases/export")
    public List<Map<String, Object>> exportHardCases() {
        return trajectories.exportHardCases(scenarios.runId());
    }

    @PostMapping("/plugins/{id}/fault")
    public ResponseEntity<Map<String, Object>> pluginFault(@PathVariable String id) {
        Map<String, Object> result = plugins.fault(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        if (!Boolean.TRUE.equals(result.get("applied"))) {
            return ResponseEntity.status(409).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/plugins/rollback")
    public ResponseEntity<Map<String, Object>> pluginRollback(@RequestBody RollbackRequest body) {
        if (blank(body.getActor())) {
            return invalid("missing_field");
        }
        return applied(plugins.rollback(body.getActor().trim()));
    }

    private ResponseEntity<Map<String, Object>> decisionRequest(ActorDecisionRequest body) {
        if (body == null || blank(body.getDecision()) || blank(body.getActor())) {
            return invalid("missing_field");
        }
        if (!"approve".equals(body.getDecision()) && !"reject".equals(body.getDecision())) {
            return invalid("invalid_field");
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> applied(Map<String, Object> result) {
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        if (!Boolean.TRUE.equals(result.get("applied"))) {
            return ResponseEntity.status(409).body(result);
        }
        return ResponseEntity.ok(result);
    }

    private static ResponseEntity<Map<String, Object>> invalid(String error) {
        return ResponseEntity.badRequest().body(Collections.<String, Object>singletonMap("error", error));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean loopback(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return "127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote);
    }

    private static Map<String, Object> manifestView(RunManifest manifest) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("runId", manifest.getRunId());
        row.put("scenarioId", manifest.getScenarioId());
        row.put("clockMode", manifest.getClockMode());
        row.put("epochStart", manifest.getEpochStart());
        row.put("tickMs", manifest.getTickMs());
        row.put("tick", manifest.getTick());
        row.put("masterSeed", manifest.getMasterSeed());
        row.put("mockMode", manifest.isMockMode());
        row.put("createdAt", manifest.getCreatedAt());
        return row;
    }

    private static Map<String, Object> tool(String name, boolean implemented) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("implemented", implemented);
        return row;
    }
}
