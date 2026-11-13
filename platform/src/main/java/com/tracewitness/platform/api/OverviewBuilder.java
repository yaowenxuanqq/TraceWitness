package com.tracewitness.platform.api;

import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.plugin.PluginService;
import com.tracewitness.platform.profile.ProfileService;
import com.tracewitness.platform.retrieval.RetrievalService;
import com.tracewitness.platform.route.RouteService;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.DecisionStore;
import com.tracewitness.platform.store.ObservationStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.trajectory.TrajectoryService;
import com.tracewitness.platform.vision.StructureService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OverviewBuilder {
    private final AppProperties properties;
    private final ScenarioService scenarios;
    private final PlatformStore store;
    private final TrackStore tracks;
    private final ObservationStore observations;
    private final DecisionStore decisions;
    private final StructureService structure;
    private final RouteService routes;
    private final ProfileService profiles;
    private final RetrievalService retrieval;
    private final TrajectoryService trajectories;
    private final PluginService plugins;

    public OverviewBuilder(AppProperties properties, ScenarioService scenarios, PlatformStore store, TrackStore tracks,
                           ObservationStore observations, DecisionStore decisions, StructureService structure,
                           RouteService routes, ProfileService profiles, RetrievalService retrieval,
                           TrajectoryService trajectories, PluginService plugins) {
        this.properties = properties;
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

    public Map<String, Object> build() {
        AppProperties.Overview limits = properties.getOverview();
        String runId = scenarios.runId();
        RunManifest manifest = store.findManifest(runId);
        Map<String, Object> agent = store.findAgentRunByRun(runId);
        String agentId = agent == null ? null : String.valueOf(agent.get("id"));
        List<Map<String, Object>> events = store.listEvents(runId, limits.getEvents(), 0);
        List<?> steps = agentId == null ? Collections.emptyList() : store.listSteps(agentId, limits.getAgentSteps(), 0);
        List<?> toolCalls = agentId == null ? Collections.emptyList() : store.listToolCalls(agentId, limits.getToolCalls(), 0);
        List<?> trackRows = tracks.listTracks(runId, limits.getTracks(), 0, limits.getChildItems());
        List<?> observationRows = structure.list(limits.getObservations(), 0);
        List<?> decisionRows = routes.list(limits.getDecisions(), 0);
        Map<String, Object> people = profiles.overview(limits);
        Map<String, Object> search = retrieval.overview(limits);
        Map<String, Object> trail = trajectories.overview(limits);
        Map<String, Object> plugin = plugins.overview(limits);

        Map<String, Object> metaLimits = new LinkedHashMap<String, Object>();
        Map<String, Object> metaTotals = new LinkedHashMap<String, Object>();
        boolean truncated = false;
        truncated = note(metaLimits, metaTotals, "events", limits.getEvents(), store.countEvents(runId), events.size(), false) || truncated;
        truncated = note(metaLimits, metaTotals, "agentSteps", limits.getAgentSteps(), agentId == null ? 0 : store.countSteps(agentId), steps.size(), false) || truncated;
        truncated = note(metaLimits, metaTotals, "toolCalls", limits.getToolCalls(), agentId == null ? 0 : store.countToolCalls(agentId), toolCalls.size(), false) || truncated;
        truncated = note(metaLimits, metaTotals, "tracks", limits.getTracks(), tracks.countTracks(runId), trackRows.size(), false) || truncated;
        truncated = note(metaLimits, metaTotals, "observations", limits.getObservations(), observations.count(runId), observationRows.size(), false) || truncated;
        truncated = note(metaLimits, metaTotals, "decisions", limits.getDecisions(), decisions.count(runId), decisionRows.size(), false) || truncated;
        truncated = take(people, metaLimits, metaTotals, "doors", "doorsTotal", limits.getDoors()) || truncated;
        truncated = take(people, metaLimits, metaTotals, "edges", "edgesTotal", limits.getDoors()) || truncated;
        truncated = take(people, metaLimits, metaTotals, "profiles", "profilesTotal", limits.getProfiles()) || truncated;
        truncated = take(people, metaLimits, metaTotals, "tickets", "ticketsTotal", limits.getTickets()) || truncated;
        truncated = take(people, metaLimits, metaTotals, "audits", "auditsTotal", limits.getAudits()) || truncated;
        truncated = take(search, metaLimits, metaTotals, "queries", "queriesTotal", limits.getQueries()) || truncated;
        truncated = take(search, metaLimits, metaTotals, "passages", "passagesTotal", limits.getPassages()) || truncated;
        truncated = Boolean.TRUE.equals(search.remove("childrenTruncated")) || truncated;
        truncated = take(trail, metaLimits, metaTotals, "trajectories", "trajectoriesTotal", limits.getTrajectories()) || truncated;
        truncated = take(trail, metaLimits, metaTotals, "companions", "companionsTotal", limits.getCompanions()) || truncated;
        truncated = take(trail, metaLimits, metaTotals, "packages", "packagesTotal", limits.getPackages()) || truncated;
        truncated = take(trail, metaLimits, metaTotals, "hardCases", "hardCasesTotal", limits.getHardCases()) || truncated;
        truncated = take(trail, metaLimits, metaTotals, "compares", "comparesTotal", limits.getCompares()) || truncated;
        truncated = Boolean.TRUE.equals(trail.remove("childrenTruncated")) || truncated;
        truncated = take(plugin, metaLimits, metaTotals, "plugins", "pluginsTotal", limits.getPlugins()) || truncated;
        truncated = take(plugin, metaLimits, metaTotals, "health", "healthTotal", limits.getPlugins()) || truncated;
        truncated = take(plugin, metaLimits, metaTotals, "rollbacks", "rollbacksTotal", limits.getPlugins()) || truncated;

        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("truncated", Boolean.valueOf(truncated));
        meta.put("limits", metaLimits);
        meta.put("totals", metaTotals);

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("overviewMeta", meta);
        body.put("mode", properties.getMode());
        body.put("mockMode", properties.mock());
        body.put("banner", properties.banner());
        body.put("run", manifest == null ? null : manifestView(manifest));
        body.put("sites", store.listSites(runId));
        body.put("devices", store.listDevices(runId));
        body.put("events", events);
        body.put("outbox", store.outboxCounts(runId));
        body.put("agentRun", agent);
        body.put("steps", steps);
        body.put("toolCalls", toolCalls);
        body.put("tracks", trackRows);
        body.put("observations", observationRows);
        body.put("decisions", decisionRows);
        body.put("doors", people.get("doors"));
        body.put("doorEdges", people.get("edges"));
        body.put("profiles", people.get("profiles"));
        body.put("tickets", people.get("tickets"));
        body.put("audits", people.get("audits"));
        body.put("queries", search.get("queries"));
        body.put("passages", search.get("passages"));
        body.put("trajectories", trail.get("trajectories"));
        body.put("companions", trail.get("companions"));
        body.put("packages", trail.get("packages"));
        body.put("hardCases", trail.get("hardCases"));
        body.put("compares", trail.get("compares"));
        body.put("plugins", plugin.get("plugins"));
        body.put("pluginHealth", plugin.get("health"));
        body.put("rollbacks", plugin.get("rollbacks"));
        body.put("tools", tools());
        return body;
    }

    private static boolean take(Map<String, Object> source, Map<String, Object> limits, Map<String, Object> totals,
                                String listKey, String totalKey, int limit) {
        int total = ((Number) source.remove(totalKey)).intValue();
        int shown = ((List<?>) source.get(listKey)).size();
        return note(limits, totals, listKey, limit, total, shown, false);
    }

    private static boolean note(Map<String, Object> limits, Map<String, Object> totals, String name,
                                int limit, int total, int shown, boolean extra) {
        limits.put(name, Integer.valueOf(limit));
        totals.put(name, Integer.valueOf(total));
        return extra || total > shown;
    }

    private static List<Map<String, Object>> tools() {
        List<Map<String, Object>> tools = new java.util.ArrayList<Map<String, Object>>();
        tools.add(tool("search_events"));
        tools.add(tool("get_event_detail"));
        tools.add(tool("search_person"));
        tools.add(tool("build_trajectory"));
        tools.add(tool("find_companions"));
        tools.add(tool("export_evidence"));
        return tools;
    }

    private static Map<String, Object> tool(String name) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("implemented", Boolean.TRUE);
        return row;
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
}
