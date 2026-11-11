package com.tracewitness.platform.vision;

import com.tracewitness.platform.agent.PythonAgentClient;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class VisionService {
    static final int SAMPLE_FRAMES = 8;

    private final TrackStore tracks;
    private final PlatformStore platform;
    private final ScenarioService scenarios;
    private final PythonAgentClient client;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;

    public VisionService(TrackStore tracks, PlatformStore platform, ScenarioService scenarios,
                         PythonAgentClient client, TimeSource timeSource, TransactionTemplate transactions) {
        this.tracks = tracks;
        this.platform = platform;
        this.scenarios = scenarios;
        this.client = client;
        this.timeSource = timeSource;
        this.transactions = transactions;
    }

    public void ensure() {
        String runId = scenarios.runId();
        if (tracks.countTracks(runId) > 0) {
            return;
        }
        String deviceId = cameraId(runId);
        if (deviceId == null) {
            return;
        }
        RunManifest manifest = platform.findManifest(runId);
        if (manifest == null) {
            return;
        }
        timeSource.load(ClockMode.valueOf(manifest.getClockMode()), manifest.getEpochStart(), manifest.getTickMs(), manifest.getTick());
        Map<String, Object> result = client.trackSample(
                runId, manifest.getClockMode(), timeSource.formatNow(), timeSource.tick(), SAMPLE_FRAMES);
        transactions.execute(status -> {
            if (tracks.countTracks(runId) > 0) {
                return null;
            }
            storeResult(runId, deviceId, result, timeSource.formatNow());
            return null;
        });
    }

    private void storeResult(String runId, String deviceId, Map<String, Object> result, String createdAt) {
        List<?> trackRows = result.get("tracks") instanceof List ? (List<?>) result.get("tracks") : java.util.Collections.emptyList();
        for (Object item : trackRows) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> track = (Map<String, Object>) item;
            List<?> points = track.get("points") instanceof List ? (List<?>) track.get("points") : java.util.Collections.emptyList();
            if (points.isEmpty()) {
                continue;
            }
            long opened = Long.MAX_VALUE;
            long closed = Long.MIN_VALUE;
            for (Object pointItem : points) {
                @SuppressWarnings("unchecked")
                Map<String, Object> point = (Map<String, Object>) pointItem;
                long tick = ((Number) point.get("tick")).longValue();
                opened = Math.min(opened, tick);
                closed = Math.max(closed, tick);
            }
            String trackId = platform.nextId(runId, "track");
            tracks.insertTrack(trackId, runId, deviceId, String.valueOf(track.get("local_id")), points.size(), opened, closed);
            for (Object pointItem : points) {
                @SuppressWarnings("unchecked")
                Map<String, Object> point = (Map<String, Object>) pointItem;
                tracks.insertPoint(
                        platform.nextId(runId, "point"),
                        trackId,
                        runId,
                        ((Number) point.get("frame_index")).intValue(),
                        ((Number) point.get("tick")).longValue(),
                        quantity(point.get("x")),
                        quantity(point.get("y")),
                        quantity(point.get("w")),
                        quantity(point.get("h")),
                        quantity(point.get("confidence")));
            }
        }
        List<?> faults = result.get("faults") instanceof List ? (List<?>) result.get("faults") : java.util.Collections.emptyList();
        for (Object item : faults) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fault = (Map<String, Object>) item;
            String detail = String.valueOf(fault.get("detail"));
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            tracks.insertFault(
                    platform.nextId(runId, "fault"),
                    runId,
                    deviceId,
                    fault.get("frame_index") == null ? null : ((Number) fault.get("frame_index")).intValue(),
                    fault.get("tick") == null ? null : ((Number) fault.get("tick")).longValue(),
                    String.valueOf(fault.get("fault_type")),
                    detail,
                    createdAt);
        }
    }

    private String cameraId(String runId) {
        for (Map<String, Object> device : platform.listDevices(runId)) {
            if ("camera".equals(device.get("kind"))) {
                return String.valueOf(device.get("id"));
            }
        }
        return null;
    }

    private static BigDecimal quantity(Object value) {
        return Quantities.persisted(new BigDecimal(String.valueOf(value)));
    }
}
