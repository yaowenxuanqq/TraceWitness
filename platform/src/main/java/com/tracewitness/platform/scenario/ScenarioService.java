package com.tracewitness.platform.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.outbox.OutboxPublisher;
import com.tracewitness.platform.outbox.OutboxQueue;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScenarioService {
    private final PlatformStore store;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;
    private final OutboxQueue queue;
    private final OutboxPublisher publisher;
    private final AppProperties properties;
    private final ObjectMapper mapper;

    public ScenarioService(PlatformStore store, TimeSource timeSource, TransactionTemplate transactions,
                           OutboxQueue queue, OutboxPublisher publisher, AppProperties properties,
                           ObjectMapper mapper) {
        this.store = store;
        this.timeSource = timeSource;
        this.transactions = transactions;
        this.queue = queue;
        this.publisher = publisher;
        this.properties = properties;
        this.mapper = mapper;
    }

    public String runId() {
        AppProperties.Eval eval = properties.getEval();
        return "run-" + eval.getScenarioId() + "-" + eval.getMasterSeed();
    }

    public void ensure() {
        String runId = runId();
        RunManifest existing = store.findManifest(runId);
        if (existing != null) {
            timeSource.load(ClockMode.valueOf(existing.getClockMode()), existing.getEpochStart(), existing.getTickMs(), existing.getTick());
            publisher.publishAll(runId);
            return;
        }
        ScenarioScript script = readScript();
        List<Long> outboxIds = transactions.execute(status -> seed(runId, script));
        queue.addAll(outboxIds == null ? Collections.<Long>emptyList() : outboxIds);
        publisher.publishAll(runId);
    }

    private List<Long> seed(String runId, ScenarioScript script) {
        AppProperties.Eval eval = properties.getEval();
        timeSource.load(ClockMode.EVAL, eval.getEpochStart(), eval.getTickMs(), 0);
        store.insertManifest(new RunManifest(
                runId,
                eval.getScenarioId(),
                ClockMode.EVAL.name(),
                eval.getEpochStart(),
                eval.getTickMs(),
                0,
                eval.getMasterSeed(),
                properties.mock(),
                timeSource.formatNow()));

        Map<String, String> siteIds = new LinkedHashMap<>();
        for (ScenarioScript.SiteSpec site : script.sites) {
            String id = store.nextId(runId, "site");
            store.insertSite(id, runId, site.code, site.name,
                    Quantities.persisted(site.xM), Quantities.persisted(site.yM));
            siteIds.put(site.code, id);
        }
        Map<String, String> deviceIds = new LinkedHashMap<String, String>();
        Map<String, String> deviceSites = new LinkedHashMap<String, String>();
        for (ScenarioScript.DeviceSpec device : script.devices) {
            String siteId = siteIds.get(device.siteCode);
            if (siteId == null) {
                throw new IllegalStateException("unknown site: " + device.siteCode);
            }
            String id = store.nextId(runId, "device");
            store.insertDevice(id, runId, siteId, device.code, device.kind, device.name);
            deviceIds.put(device.code, id);
            deviceSites.put(device.code, siteId);
        }

        List<Long> outboxIds = new ArrayList<Long>();
        long lastTick = 0;
        for (ScenarioScript.EventSpec event : script.events) {
            String deviceId = deviceIds.get(event.deviceCode);
            if (deviceId == null) {
                throw new IllegalStateException("unknown device: " + event.deviceCode);
            }
            timeSource.setTick(event.tick);
            String eventId = store.nextId(runId, "event");
            store.insertEvent(eventId, runId, deviceId, deviceSites.get(event.deviceCode),
                    event.eventType, event.summary, timeSource.formatNow(), event.tick);
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("eventId", eventId);
            payload.put("eventType", event.eventType);
            outboxIds.add(store.insertOutbox(runId, eventId, store.writeJson(payload), timeSource.formatNow()));
            lastTick = Math.max(lastTick, event.tick);
        }
        timeSource.setTick(lastTick);
        store.updateTick(runId, lastTick);
        return outboxIds;
    }

    private ScenarioScript readScript() {
        ClassPathResource resource = new ClassPathResource("scenarios/w02-mock-incident.json");
        try (InputStream input = resource.getInputStream()) {
            return mapper.readValue(input, ScenarioScript.class);
        } catch (IOException exception) {
            throw new IllegalStateException("scenario script unreadable", exception);
        }
    }
}
