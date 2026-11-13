package com.tracewitness.platform;

import com.tracewitness.platform.api.OverviewBuilder;
import com.tracewitness.platform.api.RunPageService;
import com.tracewitness.platform.scenario.ScenarioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverviewLimitTest {
    @Test
    void overviewStopsAtTheDatabaseLimitAndPagesReachTheRest() throws Exception {
        Path dir = Files.createTempDirectory("tw-overview");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(
                "--spring.datasource.url=jdbc:h2:file:" + database,
                "--spring.datasource.username=sa",
                "--tracewitness.mode=MOCK",
                "--tracewitness.auto-loop=false",
                "--tracewitness.overview.events=2",
                "--tracewitness.media-dir=" + dir.resolve("media").toAbsolutePath().toString().replace('\\', '/'),
                "--server.port=0")) {
            ScenarioService scenarios = context.getBean(ScenarioService.class);
            scenarios.ensure();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String runId = scenarios.runId();
            for (int i = 0; i < 5; i++) {
                jdbc.update("insert into event(id, run_id, device_id, site_id, event_type, summary, occurred_at, tick) values (?, ?, ?, ?, ?, ?, ?, ?)",
                        "tw.event.extra." + i, runId, "device", "site", "note", "extra " + i,
                        "2026-01-01T01:00:0" + i + ".000Z", 1000 + i);
            }
            OverviewBuilder overviewBuilder = context.getBean(OverviewBuilder.class);
            Map<String, Object> first = overviewBuilder.build();
            Map<String, Object> second = overviewBuilder.build();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) first.get("events");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> again = (List<Map<String, Object>>) second.get("events");
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) first.get("overviewMeta");
            @SuppressWarnings("unchecked")
            Map<String, Object> limits = (Map<String, Object>) meta.get("limits");
            @SuppressWarnings("unchecked")
            Map<String, Object> totals = (Map<String, Object>) meta.get("totals");
            assertEquals(2, events.size());
            assertEquals(Integer.valueOf(2), limits.get("events"));
            assertTrue(((Number) totals.get("events")).intValue() > events.size());
            assertEquals(Boolean.TRUE, meta.get("truncated"));
            assertEquals(events.get(0).get("id"), again.get(0).get("id"));
            assertEquals(events.get(1).get("id"), again.get(1).get("id"));

            Map<String, Object> page = context.getBean(RunPageService.class).page("events", 2, 2).getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
            Set<String> shown = new HashSet<String>();
            shown.add(String.valueOf(events.get(0).get("id")));
            shown.add(String.valueOf(events.get(1).get("id")));
            assertEquals(2, items.size());
            assertTrue(!shown.contains(String.valueOf(items.get(0).get("id"))));
            long started = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                overviewBuilder.build();
            }
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            assertTrue(elapsedMs < 5000L, "overview elapsed " + elapsedMs + " ms");
        }
    }
}
