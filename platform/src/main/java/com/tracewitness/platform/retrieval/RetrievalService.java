package com.tracewitness.platform.retrieval;

import com.tracewitness.platform.profile.ProfilePolicy;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.store.RetrievalStore;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetrievalService {
    private final RetrievalStore retrieval;
    private final ProfileStore profiles;
    private final PlatformStore platform;
    private final ScenarioService scenarios;
    private final TransactionTemplate transactions;

    public RetrievalService(RetrievalStore retrieval, ProfileStore profiles, PlatformStore platform,
                            ScenarioService scenarios, TransactionTemplate transactions) {
        this.retrieval = retrieval;
        this.profiles = profiles;
        this.platform = platform;
        this.scenarios = scenarios;
        this.transactions = transactions;
    }

    public void ensure() {
        final String runId = scenarios.runId();
        if (retrieval.countQueries(runId) > 0) {
            return;
        }
        final RunManifest manifest = platform.findManifest(runId);
        if (manifest == null || profiles.countProfiles(runId) == 0) {
            return;
        }
        transactions.execute(status -> {
            if (retrieval.countQueries(runId) > 0) {
                return null;
            }
            seed(runId);
            return null;
        });
    }

    public Map<String, Object> overview() {
        String runId = scenarios.runId();
        List<Map<String, Object>> queries = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> query : retrieval.listQueries(runId)) {
            String queryId = String.valueOf(query.get("id"));
            query.put("hits", retrieval.listHits(queryId));
            query.put("exclusions", retrieval.listExclusions(queryId));
            queries.add(query);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("queries", queries);
        body.put("passages", retrieval.listPassages(runId));
        return body;
    }

    public Map<String, Object> decideAlert(String passageId, String decision, String actor) {
        Map<String, Object> passage = retrieval.findPassage(passageId);
        if (passage == null) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("applied", Boolean.FALSE);
        body.put("passage", passage);
        if (!Boolean.TRUE.equals(passage.get("alerted")) || !"pending_human".equals(passage.get("status"))) {
            return body;
        }
        if (!"approve".equals(decision) && !"reject".equals(decision)) {
            return body;
        }
        String runId = String.valueOf(passage.get("runId"));
        String stamped = stamp(platform.findManifest(runId));
        String who = actor == null || actor.trim().isEmpty() ? "investigator" : actor.trim();
        String next = "approve".equals(decision) ? "confirmed" : "rejected";
        transactions.execute(status -> {
            Map<String, Object> current = retrieval.findPassage(passageId);
            if (current == null || !"pending_human".equals(current.get("status"))) {
                return null;
            }
            retrieval.updatePassageStatus(passageId, next);
            profiles.insertAudit(platform.nextId(runId, "audit"), runId,
                    "approve".equals(decision) ? "alert_confirmed" : "alert_rejected",
                    passageId, String.valueOf(current.get("detail")), who, stamped);
            return null;
        });
        Map<String, Object> updated = retrieval.findPassage(passageId);
        body.put("applied", Boolean.valueOf(next.equals(updated.get("status"))));
        body.put("passage", updated);
        return body;
    }

    private void seed(String runId) {
        List<Map<String, Object>> candidates = gallery(runId);
        saveQuery(runId, "resident-multi", candidates, faces("0.20,0.00,0.00", "0.99,0.01,0.00"), null, null);
        saveQuery(runId, "hard-dark", candidates, faces("0.20,0.00,0.00", "0.99,0.01,0.00"), "dark", null);
        saveQuery(runId, "soft-light", candidates, faces("0.99,0.01,0.00"), null, "light");
        savePassage(runId, "bike-in", "entrance", bicycle("in"), "连续三帧非机动车进入");
        savePassage(runId, "bike-out", "entrance", bicycle("out"), "连续三帧非机动车离开");
        savePassage(runId, "bike-gap", "entrance", gap(), "进楼帧被打断");
        savePassage(runId, "person-in", "entrance", personIn(), "行人穿过门线");
    }

    private void saveQuery(String runId, String caseKey, List<Map<String, Object>> candidates, List<Map<String, Object>> images,
                           String hardColor, String softColor) {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("candidates", candidates);
        input.put("images", images);
        if (hardColor != null) {
            input.put("hard", color(hardColor));
        }
        if (softColor != null) {
            input.put("soft", color(softColor));
        }
        Map<String, Object> result = RetrievalPolicy.evaluate(input);
        String queryId = platform.nextId(runId, "query");
        retrieval.insertQuery(queryId, runId, caseKey, images.size(), hardColor, softColor,
                Boolean.TRUE.equals(result.get("person_created")), String.valueOf(result.get("order")));
        for (Object item : (List<?>) result.get("hits")) {
            Map<?, ?> hit = (Map<?, ?>) item;
            retrieval.insertHit(platform.nextId(runId, "hit"), runId, queryId, String.valueOf(hit.get("id")),
                    ((Number) hit.get("rank")).intValue(), decimal(hit.get("face_score")), decimal(hit.get("body_score")),
                    decimal(hit.get("fused")), decimal(hit.get("rank_score")),
                    Boolean.TRUE.equals(hit.get("soft_applied")), String.valueOf(hit.get("explanation")));
        }
        for (Object item : (List<?>) result.get("exclusions")) {
            Map<?, ?> exclusion = (Map<?, ?>) item;
            retrieval.insertExclusion(platform.nextId(runId, "exclusion"), runId, queryId,
                    String.valueOf(exclusion.get("id")), String.valueOf(exclusion.get("reason")),
                    String.valueOf(exclusion.get("explanation")));
        }
    }

    private void savePassage(String runId, String caseKey, String scene, List<Map<String, Object>> frames, String detail) {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("scene", scene);
        input.put("frames", frames);
        Map<String, Object> result = PassagePolicy.evaluate(input);
        boolean alerted = Boolean.TRUE.equals(result.get("alert"));
        String id = platform.nextId(runId, "passage");
        retrieval.insertPassage(id, runId, caseKey, scene, ((Number) result.get("streak")).intValue(), alerted,
                String.valueOf(result.get("level")), String.valueOf(result.get("status")),
                alerted ? "door_open" : null, String.valueOf(result.get("action_status")),
                Boolean.TRUE.equals(result.get("executed")), detail);
        if (alerted) {
            RunManifest manifest = platform.findManifest(runId);
            profiles.insertAudit(platform.nextId(runId, "audit"), runId, "alert_pending", id, detail, "system",
                    stamp(manifest));
        }
    }

    private List<Map<String, Object>> gallery(String runId) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> profile : profiles.listProfiles(runId)) {
            if (!"active".equals(profile.get("status"))) {
                continue;
            }
            String code = String.valueOf(profile.get("code"));
            Body body = body(code);
            if (body == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", code);
            row.put("face", ProfilePolicy.parseVector(String.valueOf(profile.get("centroid"))));
            row.put("body", ProfilePolicy.parseVector(body.vector));
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            attributes.put("upper_color", body.color);
            row.put("attributes", attributes);
            rows.add(row);
        }
        return rows;
    }

    private static Body body(String code) {
        if ("resident-chen".equals(code)) {
            return new Body("0.20,0.10,0.90", "light");
        }
        if ("stranger-jia".equals(code)) {
            return new Body("0.10,0.90,0.10", "dark");
        }
        if ("lookalike".equals(code)) {
            return new Body("0.90,0.20,0.10", "light");
        }
        return null;
    }

    private static List<Map<String, Object>> faces(String... vectors) {
        List<Map<String, Object>> images = new ArrayList<Map<String, Object>>();
        for (String vector : vectors) {
            Map<String, Object> image = new LinkedHashMap<String, Object>();
            image.put("face", vector);
            images.add(image);
        }
        return images;
    }

    private static Map<String, String> color(String value) {
        Map<String, String> hard = new LinkedHashMap<String, String>();
        hard.put("upper_color", value);
        return hard;
    }

    private static List<Map<String, Object>> bicycle(String direction) {
        List<Map<String, Object>> frames = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 3; i++) {
            frames.add(frame("bicycle", direction, true));
        }
        return frames;
    }

    private static List<Map<String, Object>> gap() {
        return Arrays.asList(frame("bicycle", "in", true), frame("bicycle", "in", true), frame("none", "in", false),
                frame("bicycle", "in", true));
    }

    private static List<Map<String, Object>> personIn() {
        List<Map<String, Object>> frames = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 3; i++) {
            frames.add(frame("person", "in", true));
        }
        return frames;
    }

    private static Map<String, Object> frame(String kind, String direction, boolean crossed) {
        Map<String, Object> frame = new LinkedHashMap<String, Object>();
        frame.put("kind", kind);
        frame.put("direction", direction);
        frame.put("crossed", Boolean.valueOf(crossed));
        return frame;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static String stamp(RunManifest manifest) {
        return TimeSource.FORMAT.format(Instant.parse(manifest.getEpochStart()).plusMillis(
                Math.multiplyExact(manifest.getTick(), manifest.getTickMs())));
    }

    private static final class Body {
        private final String vector;
        private final String color;

        private Body(String vector, String color) {
            this.vector = vector;
            this.color = color;
        }
    }
}
