package com.tracewitness.platform.bootstrap;

import com.tracewitness.platform.agent.AgentService;
import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.media.MediaService;
import com.tracewitness.platform.scenario.ScenarioService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MockLoopRunner implements ApplicationRunner {
    private final AppProperties properties;
    private final ScenarioService scenarios;
    private final MediaService media;
    private final AgentService agents;

    public MockLoopRunner(AppProperties properties, ScenarioService scenarios, MediaService media, AgentService agents) {
        this.properties = properties;
        this.scenarios = scenarios;
        this.media = media;
        this.agents = agents;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isAutoLoop()) {
            return;
        }
        scenarios.ensure();
        media.ensure();
        agents.investigate();
    }
}
