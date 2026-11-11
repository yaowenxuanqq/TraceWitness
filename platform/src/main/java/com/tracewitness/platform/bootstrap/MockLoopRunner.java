package com.tracewitness.platform.bootstrap;

import com.tracewitness.platform.agent.AgentService;
import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.media.MediaService;
import com.tracewitness.platform.plugin.PluginService;
import com.tracewitness.platform.profile.ProfileService;
import com.tracewitness.platform.retrieval.RetrievalService;
import com.tracewitness.platform.route.RouteService;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.trajectory.TrajectoryService;
import com.tracewitness.platform.vision.StructureService;
import com.tracewitness.platform.vision.VisionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MockLoopRunner implements ApplicationRunner {
    private final AppProperties properties;
    private final ScenarioService scenarios;
    private final MediaService media;
    private final VisionService vision;
    private final StructureService structure;
    private final RouteService routes;
    private final ProfileService profiles;
    private final RetrievalService retrieval;
    private final TrajectoryService trajectories;
    private final PluginService plugins;
    private final AgentService agents;

    public MockLoopRunner(AppProperties properties, ScenarioService scenarios, MediaService media,
                          VisionService vision, StructureService structure, RouteService routes,
                          ProfileService profiles, RetrievalService retrieval, TrajectoryService trajectories,
                          PluginService plugins, AgentService agents) {
        this.properties = properties;
        this.scenarios = scenarios;
        this.media = media;
        this.vision = vision;
        this.structure = structure;
        this.routes = routes;
        this.profiles = profiles;
        this.retrieval = retrieval;
        this.trajectories = trajectories;
        this.plugins = plugins;
        this.agents = agents;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoLoop()) {
            return;
        }
        scenarios.ensure();
        media.ensure();
        vision.ensure();
        structure.ensure();
        routes.ensure();
        profiles.ensure();
        retrieval.ensure();
        trajectories.ensure();
        plugins.ensure();
        agents.investigate();
    }
}
