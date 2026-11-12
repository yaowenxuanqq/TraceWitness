package com.tracewitness.platform.trajectory;

import com.tracewitness.platform.media.MediaService;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.store.PlatformStore.RunManifest;
import com.tracewitness.platform.store.ProfileStore;
import com.tracewitness.platform.store.TrajectoryStore;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrajectoryService {
    private final TrajectoryStore trajectories;
    private final PlatformStore platform;
    private final ProfileStore profiles;
    private final MediaService media;
    private final ScenarioService scenarios;
    private final TransactionTemplate transactions;

    public TrajectoryService(TrajectoryStore trajectories, PlatformStore platform, ProfileStore profiles,
                             MediaService media, ScenarioService scenarios, TransactionTemplate transactions) {
        this.trajectories = trajectories;
        this.platform = platform;
        this.profiles = profiles;
        this.media = media;
        this.scenarios = scenarios;
        this.transactions = transactions;
    }

    public void ensure() {
        final String runId = scenarios.runId();
        if (trajectories.countTrajectories(runId) > 0) {
            return;
        }
        final RunManifest manifest = platform.findManifest(runId);
        if (manifest == null) {
            return;
        }
        transactions.execute(status -> {
            if (trajectories.countTrajectories(runId) > 0) {
                return null;
            }
            seed(runId);
            return null;
        });
    }

    public Map<String, Object> overview() {
        String runId = scenarios.runId();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> trajectory : trajectories.listTrajectories(runId)) {
            trajectory.put("points", trajectories.listPoints(String.valueOf(trajectory.get("id"))));
            trajectory.put("thinRule", TrajectoryPolicy.THIN_RULE);
            rows.add(trajectory);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("trajectories", rows);
        body.put("companions", trajectories.listCompanions(runId));
        body.put("packages", trajectories.listPackages(runId));
        body.put("hardCases", trajectories.listHardCases(runId));
        body.put("compares", trajectories.listCompares(runId));
        return body;
    }

    public Map<String, Object> verify(String packageId, String submitted) {
        Map<String, Object> stored = trajectories.findPackage(packageId);
        if (stored == null || !scenarios.runId().equals(stored.get("runId"))) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("package", stored);
        if (!"valid".equals(stored.get("status")) || stored.get("digest") == null) {
            body.put("valid", Boolean.FALSE);
            body.put("error", "evidence_missing");
            return body;
        }
        String manifest = String.valueOf(stored.get("manifest"));
        String digest = String.valueOf(stored.get("digest"));
        boolean intact = digest.equals(TrajectoryPolicy.sha256(manifest));
        if (submitted != null && !submitted.equals(manifest)) {
            intact = false;
        }
        body.put("valid", Boolean.valueOf(intact));
        body.put("error", intact ? "" : "digest_mismatch");
        return body;
    }

    public Map<String, Object> label(String hardCaseId, String label, String actor) {
        Map<String, Object> hardCase = trajectories.findHardCase(hardCaseId);
        if (hardCase == null || !scenarios.runId().equals(hardCase.get("runId"))) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("applied", Boolean.FALSE);
        body.put("hardCase", hardCase);
        if (!"open".equals(hardCase.get("status")) || !TrajectoryPolicy.LABELS.contains(label)) {
            return body;
        }
        String runId = String.valueOf(hardCase.get("runId"));
        String stamped = stamp(platform.findManifest(runId));
        String who = actor == null || actor.trim().isEmpty() ? "investigator" : actor.trim();
        transactions.execute(status -> {
            Map<String, Object> current = trajectories.findHardCase(hardCaseId);
            if (current == null || !"open".equals(current.get("status"))) {
                return null;
            }
            trajectories.updateHardCase(hardCaseId, label, "labeled");
            profiles.insertAudit(platform.nextId(runId, "audit"), runId, "hard_labeled", hardCaseId,
                    String.valueOf(current.get("reason")), who, stamped);
            return null;
        });
        Map<String, Object> updated = trajectories.findHardCase(hardCaseId);
        body.put("applied", Boolean.valueOf("labeled".equals(updated.get("status"))));
        body.put("hardCase", updated);
        return body;
    }

    public List<Map<String, Object>> exportHardCases(String runId) {
        List<Map<String, Object>> cases = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> hardCase : trajectories.listHardCases(runId)) {
            Map<String, Object> input = new LinkedHashMap<String, Object>();
            input.put("id", hardCase.get("caseKey"));
            input.put("reason", hardCase.get("reason"));
            input.put("label", hardCase.get("label"));
            cases.add(input);
        }
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("cases", cases);
        Map<String, Object> evaluated = TrajectoryPolicy.evaluate("hard_case", input);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exported = (List<Map<String, Object>>) evaluated.get("exported");
        for (Map<String, Object> row : exported) {
            for (Map<String, Object> hardCase : trajectories.listHardCases(runId)) {
                if (String.valueOf(row.get("id")).equals(hardCase.get("caseKey"))) {
                    row.put("hard_case_id", hardCase.get("id"));
                }
            }
        }
        return exported;
    }

    public Map<String, Object> toolTrajectory(String runId, String profileId) {
        String code = profileCode(runId, profileId);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<String> sourceIds = new ArrayList<String>();
        for (Map<String, Object> trajectory : trajectories.listTrajectories(runId)) {
            if (code.length() > 0 && !code.equals(trajectory.get("profileCode")) && !profileId.equals(trajectory.get("id"))) {
                continue;
            }
            List<String> keptDoors = new ArrayList<String>();
            List<String> dropped = new ArrayList<String>();
            for (Map<String, Object> point : trajectories.listPoints(String.valueOf(trajectory.get("id")))) {
                if (Boolean.TRUE.equals(point.get("kept"))) {
                    keptDoors.add(String.valueOf(point.get("doorCode")));
                } else {
                    dropped.add(String.valueOf(point.get("doorCode")));
                }
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", trajectory.get("id"));
            row.put("profileCode", trajectory.get("profileCode"));
            row.put("keptDoors", keptDoors);
            row.put("droppedDoors", dropped);
            rows.add(row);
            sourceIds.add(String.valueOf(trajectory.get("id")));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tool_name", "build_trajectory");
        result.put("trajectories", rows);
        result.put("source_ids", sourceIds);
        return result;
    }

    public Map<String, Object> toolCompanions(String runId, String profileId) {
        String code = profileCode(runId, profileId);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        List<String> sourceIds = new ArrayList<String>();
        for (Map<String, Object> link : trajectories.listCompanions(runId)) {
            if (code.length() > 0 && !code.equals(link.get("leftCode")) && !code.equals(link.get("rightCode"))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", link.get("id"));
            row.put("left", link.get("leftCode"));
            row.put("right", link.get("rightCode"));
            row.put("door", link.get("doorCode"));
            row.put("gap_seconds", link.get("gapSeconds"));
            rows.add(row);
            sourceIds.add(String.valueOf(link.get("id")));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tool_name", "find_companions");
        result.put("companions", rows);
        result.put("source_ids", sourceIds);
        return result;
    }

    public Map<String, Object> sealClips(String runId, List<String> clipIds) {
        List<String> sorted = new ArrayList<String>(clipIds);
        Collections.sort(sorted);
        String caseKey = "clips-" + TrajectoryPolicy.sha256(join(sorted)).substring(0, 12);
        Map<String, Object> existing = trajectories.findPackageByCase(runId, caseKey);
        if (existing != null) {
            return existing;
        }
        List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
        for (String clipId : sorted) {
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("id", clipId);
            source.put("exists", Boolean.TRUE);
            sources.add(source);
        }
        return persistPackage(runId, caseKey, null, Collections.<String>emptyList(),
                Collections.<Map<String, Object>>emptyList(), sources);
    }

    private void seed(String runId) {
        List<Map<String, Object>> visits = new ArrayList<Map<String, Object>>();
        String chenId = saveTrajectory(runId, "chen-walk", "resident-chen", visits, Arrays.asList(
                point("a", "resident-chen", "lobby", 0),
                point("b", "resident-chen", "unit-a", 40),
                point("c", "resident-chen", "unit-b", 90),
                point("d", "resident-chen", "roof", 100)));
        saveTrajectory(runId, "dwell-thin", "dwell-chen", visits, Arrays.asList(
                point("a", "dwell-chen", "lobby", 5000),
                point("b", "dwell-chen", "lobby", 5010),
                point("c", "dwell-chen", "lobby", 5020),
                point("d", "dwell-chen", "stair", 5040)));
        saveTrajectory(runId, "fast-unit", "fast-chen", visits, Arrays.asList(
                point("f1", "fast-chen", "lobby", 10000),
                point("f2", "fast-chen", "unit-b", 10010)));
        saveTrajectory(runId, "companion-jia", "stranger-jia", visits,
                Collections.singletonList(point("j1", "stranger-jia", "lobby", 30)));
        Map<String, Object> companionInput = new LinkedHashMap<String, Object>();
        companionInput.put("window_seconds", Integer.valueOf(TrajectoryPolicy.COMPANION_WINDOW));
        companionInput.put("visits", visits);
        Map<String, Object> companionResult = TrajectoryPolicy.evaluate("companions", companionInput);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> companions = (List<Map<String, Object>>) companionResult.get("companions");
        for (Map<String, Object> companion : companions) {
            trajectories.insertCompanion(platform.nextId(runId, "companion"), runId,
                    String.valueOf(companion.get("left")), String.valueOf(companion.get("right")),
                    String.valueOf(companion.get("door")), ((Number) companion.get("gap_seconds")).intValue());
        }
        List<String> keptKeys = new ArrayList<String>();
        for (Map<String, Object> point : trajectories.listPoints(chenId)) {
            if (Boolean.TRUE.equals(point.get("kept"))) {
                keptKeys.add(String.valueOf(point.get("pointKey")));
            }
        }
        String clipId = firstClip(runId);
        List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
        if (clipId != null) {
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            source.put("id", clipId);
            source.put("exists", Boolean.TRUE);
            sources.add(source);
        }
        persistPackage(runId, "chen-walk", chenId, keptKeys, companions, sources);
        List<Map<String, Object>> missing = new ArrayList<Map<String, Object>>();
        Map<String, Object> absent = new LinkedHashMap<String, Object>();
        absent.put("id", "missing-clip");
        absent.put("exists", Boolean.FALSE);
        missing.add(absent);
        persistPackage(runId, "missing-clip", null, keptKeys, companions, missing);
        trajectories.insertHardCase(platform.nextId(runId, "hardcase"), runId, "roof-point", "unreachable", null, "open");
        Map<String, Object> compareInput = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
        candidates.add(candidate("local-small", "0.62"));
        candidates.add(candidate("cloud-script", "0.81"));
        compareInput.put("candidates", candidates);
        Map<String, Object> compared = TrajectoryPolicy.evaluate("compare", compareInput);
        trajectories.insertCompare(platform.nextId(runId, "compare"), runId, "scripted-pair",
                String.valueOf(compared.get("left_name")), String.valueOf(compared.get("right_name")),
                new BigDecimal(String.valueOf(compared.get("left_score"))),
                new BigDecimal(String.valueOf(compared.get("right_score"))),
                String.valueOf(compared.get("winner")), false);
    }

    private String saveTrajectory(String runId, String caseKey, String profile, List<Map<String, Object>> visits,
                                  List<Map<String, Object>> points) {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> raw = new ArrayList<Map<String, Object>>();
        raw.addAll(points);
        input.put("points", raw);
        Map<String, Object> evaluated = TrajectoryPolicy.evaluate("trajectory", input);
        @SuppressWarnings("unchecked")
        List<String> kept = (List<String>) evaluated.get("kept");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dropped = (List<Map<String, Object>>) evaluated.get("dropped");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> map = (List<Map<String, Object>>) evaluated.get("map");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> annotated = (List<Map<String, Object>>) evaluated.get("points");
        String id = platform.nextId(runId, "trajectory");
        trajectories.insertTrajectory(id, runId, caseKey, profile, kept.size(), dropped.size(), map.size());
        int seq = 1;
        for (Map<String, Object> point : annotated) {
            Integer x = point.get("x") == null ? null : Integer.valueOf(((Number) point.get("x")).intValue());
            Integer y = point.get("y") == null ? null : Integer.valueOf(((Number) point.get("y")).intValue());
            trajectories.insertPoint(platform.nextId(runId, "point"), runId, id, String.valueOf(point.get("id")),
                    String.valueOf(point.get("door")), ((Number) point.get("at")).intValue(), x, y,
                    Boolean.TRUE.equals(point.get("kept")), Boolean.TRUE.equals(point.get("thinned")),
                    point.get("reason") == null ? null : String.valueOf(point.get("reason")), seq++);
            if (Boolean.TRUE.equals(point.get("kept"))) {
                Map<String, Object> visit = new LinkedHashMap<String, Object>();
                visit.put("profile", profile);
                visit.put("door", point.get("door"));
                visit.put("at", point.get("at"));
                visits.add(visit);
            }
        }
        return id;
    }

    private Map<String, Object> persistPackage(String runId, String caseKey, String trajectoryId, List<String> pointKeys,
                                               List<Map<String, Object>> companions, List<Map<String, Object>> sources) {
        Map<String, Object> existing = trajectories.findPackageByCase(runId, caseKey);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("trajectory_id", trajectoryId == null ? caseKey : trajectoryId);
        input.put("points", pointKeys);
        input.put("companions", companions);
        input.put("sources", sources);
        Map<String, Object> evaluated = TrajectoryPolicy.evaluate("evidence", input);
        String error = String.valueOf(evaluated.get("error"));
        boolean missing = "evidence_missing".equals(error);
        String manifest = missing ? "" : String.valueOf(evaluated.get("manifest"));
        String digest = missing ? null : String.valueOf(evaluated.get("digest"));
        String id = platform.nextId(runId, "package");
        trajectories.insertPackage(id, runId, caseKey, trajectoryId,
                missing ? "" : String.valueOf(evaluated.get("point_keys")),
                missing ? "" : String.valueOf(evaluated.get("companion_text")),
                missing ? "" : String.valueOf(evaluated.get("source_ids")),
                manifest, digest, missing ? "evidence_missing" : "valid", missing ? "evidence_missing" : "");
        return trajectories.findPackage(id);
    }

    private String firstClip(String runId) {
        List<Map<String, Object>> events = platform.listEvents(runId, 1);
        if (events.isEmpty()) {
            return null;
        }
        return media.existingClipId(runId, String.valueOf(events.get(0).get("id")));
    }

    private String profileCode(String runId, String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return "";
        }
        String trimmed = profileId.trim();
        for (Map<String, Object> profile : profiles.listProfiles(runId)) {
            if (trimmed.equals(profile.get("id")) || trimmed.equals(profile.get("code"))) {
                return String.valueOf(profile.get("code"));
            }
        }
        return trimmed;
    }

    private static Map<String, Object> point(String id, String profile, String door, int at) {
        Map<String, Object> point = new LinkedHashMap<String, Object>();
        point.put("id", id);
        point.put("profile", profile);
        point.put("door", door);
        point.put("at", Integer.valueOf(at));
        return point;
    }

    private static Map<String, Object> candidate(String name, String score) {
        Map<String, Object> candidate = new LinkedHashMap<String, Object>();
        candidate.put("name", name);
        candidate.put("score", score);
        return candidate;
    }

    private static String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(values.get(i));
        }
        return text.toString();
    }

    private static String stamp(RunManifest manifest) {
        if (manifest == null) {
            return TimeSource.FORMAT.format(Instant.parse("2026-01-01T00:00:00.000Z"));
        }
        return TimeSource.FORMAT.format(Instant.parse(manifest.getEpochStart()).plusMillis(
                Math.multiplyExact(manifest.getTick(), manifest.getTickMs())));
    }
}
