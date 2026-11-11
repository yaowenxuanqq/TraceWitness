package com.tracewitness.platform.vision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracewitness.platform.agent.PythonAgentClient;
import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.ObservationStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.TrackStore;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StructureService {
    private static final List<String> CASES = Arrays.asList("live", "gate_failure", "track_break");
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<Map<String, Object>>() {
    };

    private final ObservationStore observations;
    private final TrackStore tracks;
    private final PlatformStore platform;
    private final ScenarioService scenarios;
    private final PythonAgentClient client;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;
    private final AppProperties properties;
    private final ObjectMapper mapper;

    public StructureService(ObservationStore observations, TrackStore tracks, PlatformStore platform,
                            ScenarioService scenarios, PythonAgentClient client, TimeSource timeSource,
                            TransactionTemplate transactions, AppProperties properties, ObjectMapper mapper) {
        this.observations = observations;
        this.tracks = tracks;
        this.platform = platform;
        this.scenarios = scenarios;
        this.client = client;
        this.timeSource = timeSource;
        this.transactions = transactions;
        this.properties = properties;
        this.mapper = mapper;
    }

    public void ensure() {
        String runId = scenarios.runId();
        if (observations.count(runId) > 0) {
            return;
        }
        List<Map<String, Object>> storedTracks = tracks.listTracks(runId);
        if (storedTracks.isEmpty()) {
            return;
        }
        RunManifest manifest = platform.findManifest(runId);
        if (manifest == null) {
            return;
        }
        timeSource.load(ClockMode.valueOf(manifest.getClockMode()), manifest.getEpochStart(), manifest.getTickMs(), manifest.getTick());
        Map<String, Object> track = storedTracks.get(0);
        List<Map<String, Object>> livePoints = pythonPoints(track);
        List<Map<String, Object>> liveFaults = pythonFaults(tracks.listFaults(runId));
        List<Map<String, Object>> pending = new ArrayList<Map<String, Object>>();
        for (String caseKey : CASES) {
            List<Map<String, Object>> points = "live".equals(caseKey) ? livePoints : Collections.<Map<String, Object>>emptyList();
            List<Map<String, Object>> faults = "live".equals(caseKey) ? liveFaults : Collections.<Map<String, Object>>emptyList();
            Map<String, Object> result = client.structure(
                    runId, manifest.getClockMode(), timeSource.formatNow(), timeSource.tick(), caseKey, points, faults);
            int calls = ((Number) result.get("attribute_calls")).intValue();
            if (calls != 1) {
                throw new IllegalStateException("attribute head must be called once");
            }
            result.put("case_key", caseKey);
            result.put("track_id", "live".equals(caseKey) ? track.get("id") : null);
            pending.add(result);
        }
        String createdAt = timeSource.formatNow();
        transactions.execute(status -> {
            if (observations.count(runId) > 0) {
                return null;
            }
            try {
                for (Map<String, Object> result : pending) {
                    storeOne(runId, result, createdAt);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("observation thumbnail failed", exception);
            }
            return null;
        });
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : observations.list(scenarios.runId())) {
            rows.add(view(row));
        }
        return rows;
    }

    public byte[] readThumbnail(String id) throws IOException {
        Map<String, Object> row = observations.find(id);
        if (row == null) {
            return null;
        }
        return Files.readAllBytes(resolve(String.valueOf(row.get("thumbnailPath"))));
    }

    private void storeOne(String runId, Map<String, Object> result, String createdAt) throws IOException {
        String id = platform.nextId(runId, "observation");
        String relative = runId + "/observations/" + id + ".png";
        Path file = resolve(relative);
        Files.createDirectories(file.getParent());
        Thumbnails.write(
                String.valueOf(result.get("mode")),
                String.valueOf(result.get("frame_kind")),
                ((Number) result.get("chosen_frame")).intValue(),
                file);
        Object trackId = result.get("track_id");
        observations.insert(
                id,
                runId,
                trackId == null ? null : String.valueOf(trackId),
                String.valueOf(result.get("case_key")),
                String.valueOf(result.get("mode")),
                String.valueOf(result.get("frame_kind")),
                ((Number) result.get("chosen_frame")).intValue(),
                ((Number) result.get("chosen_tick")).longValue(),
                ((Number) result.get("attribute_calls")).intValue(),
                quantity(result.get("face_quality")),
                quantity(result.get("body_quality")),
                mapper.writeValueAsString(result.get("attributes")),
                mapper.writeValueAsString(result.get("masked")),
                relative,
                createdAt);
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", row.get("id"));
        body.put("trackId", row.get("trackId"));
        body.put("caseKey", row.get("caseKey"));
        body.put("mode", row.get("mode"));
        body.put("frameKind", row.get("frameKind"));
        body.put("chosenFrame", row.get("chosenFrame"));
        body.put("chosenTick", row.get("chosenTick"));
        body.put("attributeCalls", row.get("attributeCalls"));
        body.put("faceQuality", row.get("faceQuality"));
        body.put("bodyQuality", row.get("bodyQuality"));
        body.put("attributes", readJson(String.valueOf(row.get("attributesJson"))));
        body.put("masked", readJson(String.valueOf(row.get("maskedJson"))));
        body.put("thumbnailUrl", "/api/observations/" + row.get("id") + "/thumbnail");
        body.put("createdAt", row.get("createdAt"));
        return body;
    }

    private Map<String, Object> readJson(String json) {
        try {
            return mapper.readValue(json, JSON_MAP);
        } catch (IOException exception) {
            throw new IllegalStateException("observation json", exception);
        }
    }

    private static List<Map<String, Object>> pythonPoints(Map<String, Object> track) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) track.get("points");
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> point : points) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("frame_index", point.get("frameIndex"));
            row.put("tick", point.get("tick"));
            row.put("x", point.get("x"));
            row.put("y", point.get("y"));
            row.put("w", point.get("w"));
            row.put("h", point.get("h"));
            row.put("confidence", point.get("confidence"));
            rows.add(row);
        }
        return rows;
    }

    private static List<Map<String, Object>> pythonFaults(List<Map<String, Object>> faults) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> fault : faults) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("frame_index", fault.get("frameIndex"));
            row.put("tick", fault.get("tick"));
            row.put("fault_type", fault.get("faultType"));
            row.put("detail", fault.get("detail"));
            rows.add(row);
        }
        return rows;
    }

    private Path resolve(String relative) throws IOException {
        Path root = Paths.get(properties.getMediaDir()).toAbsolutePath().normalize();
        Path path = root.resolve(relative).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IOException("observation path escapes root");
        }
        return path;
    }

    private static BigDecimal quantity(Object value) {
        return Quantities.persisted(new BigDecimal(String.valueOf(value)));
    }
}
