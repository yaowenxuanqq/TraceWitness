package com.tracewitness.platform.trajectory;

import com.tracewitness.platform.profile.DoorGraph;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.safety.ReverseRules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrajectoryPolicy {
    public static final int COMPANION_WINDOW = 60;
    public static final String THIN_RULE = "first_last_and_door_change";
    public static final List<String> LABELS = Collections.unmodifiableList(java.util.Arrays.asList(
            "true_positive", "true_negative", "false_positive", "false_negative"));

    private TrajectoryPolicy() {
    }

    public static Map<String, Object> evaluate(String op, Map<String, Object> input) {
        if ("companions".equals(op)) {
            return companions(input);
        }
        if ("evidence".equals(op)) {
            return evidence(input);
        }
        if ("hard_case".equals(op)) {
            return hardCases(input);
        }
        if ("compare".equals(op)) {
            return compare(input);
        }
        return trajectory(input);
    }

    public static String canonical(String trajectoryId, List<String> points, List<Map<String, Object>> companions,
                                   List<String> sources) {
        StringBuilder text = new StringBuilder();
        text.append("trajectory=").append(trajectoryId == null ? "" : trajectoryId).append('\n');
        text.append("points=").append(join(points)).append('\n');
        text.append("companions=").append(companionText(companions)).append('\n');
        text.append("sources=").append(join(sources)).append('\n');
        return text.toString();
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 missing", exception);
        }
    }

    public static int placeX(String door) {
        if ("lobby".equals(door)) {
            return 40;
        }
        if ("unit-a".equals(door)) {
            return 40;
        }
        if ("unit-b".equals(door)) {
            return 80;
        }
        if ("stair".equals(door)) {
            return 10;
        }
        if ("roof".equals(door)) {
            return 80;
        }
        return -1;
    }

    public static int placeY(String door) {
        if ("lobby".equals(door)) {
            return 80;
        }
        if ("unit-a".equals(door)) {
            return 40;
        }
        if ("unit-b".equals(door)) {
            return 40;
        }
        if ("stair".equals(door)) {
            return 50;
        }
        if ("roof".equals(door)) {
            return 10;
        }
        return -1;
    }

    private static Map<String, Object> trajectory(Map<String, Object> input) {
        List<Point> points = points(input.get("points"));
        Collections.sort(points, new Comparator<Point>() {
            @Override
            public int compare(Point left, Point right) {
                int time = Integer.compare(left.at, right.at);
                if (time != 0) {
                    return time;
                }
                return left.id.compareTo(right.id);
            }
        });
        Map<String, List<Point>> groups = new LinkedHashMap<String, List<Point>>();
        for (Point point : points) {
            List<Point> group = groups.get(point.profile);
            if (group == null) {
                group = new ArrayList<Point>();
                groups.put(point.profile, group);
            }
            group.add(point);
        }
        List<String> kept = new ArrayList<String>();
        List<Map<String, Object>> dropped = new ArrayList<Map<String, Object>>();
        List<String> thinned = new ArrayList<String>();
        List<Map<String, Object>> map = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> annotated = new ArrayList<Map<String, Object>>();
        for (List<Point> group : groups.values()) {
            walk(group, kept, dropped, thinned, map, annotated);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("thin_rule", THIN_RULE);
        result.put("kept", kept);
        result.put("dropped", dropped);
        result.put("thinned", thinned);
        result.put("map", map);
        result.put("points", annotated);
        return result;
    }

    private static void walk(List<Point> group, List<String> kept, List<Map<String, Object>> dropped,
                             List<String> thinned, List<Map<String, Object>> map, List<Map<String, Object>> annotated) {
        List<Point> reachable = new ArrayList<Point>();
        Map<String, String> reasons = new LinkedHashMap<String, String>();
        String previousDoor = null;
        int previousAt = 0;
        boolean hasPrevious = false;
        for (Point point : group) {
            String reason = reachReason(previousDoor, hasPrevious, previousAt, point);
            if (reason != null) {
                reasons.put(point.id, reason);
                continue;
            }
            reachable.add(point);
            previousDoor = point.door;
            previousAt = point.at;
            hasPrevious = true;
        }
        List<Map<String, Object>> flags = new ArrayList<Map<String, Object>>();
        for (Point point : group) {
            Map<String, Object> flag = new LinkedHashMap<String, Object>();
            flag.put("id", point.id);
            flag.put("reachable", Boolean.valueOf(!reasons.containsKey(point.id)));
            flags.add(flag);
        }
        Map<String, Object> ruled = ReverseRules.evaluate("unreachable_track",
                Collections.<String, Object>singletonMap("points", flags));
        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) ruled.get("kept");
        List<Point> keptPoints = new ArrayList<Point>();
        for (Point point : reachable) {
            if (allowed.contains(point.id)) {
                keptPoints.add(point);
                kept.add(point.id);
            } else if (!reasons.containsKey(point.id)) {
                reasons.put(point.id, "unreachable");
            }
        }
        for (Point point : group) {
            if (reasons.containsKey(point.id)) {
                Map<String, Object> drop = new LinkedHashMap<String, Object>();
                drop.put("id", point.id);
                drop.put("reason", reasons.get(point.id));
                dropped.add(drop);
            }
        }
        List<String> thinIds = new ArrayList<String>();
        List<Map<String, Object>> thinMap = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < keptPoints.size(); i++) {
            Point point = keptPoints.get(i);
            boolean endpoint = i == 0 || i == keptPoints.size() - 1;
            String previous = thinMap.isEmpty() ? null : String.valueOf(thinMap.get(thinMap.size() - 1).get("door"));
            boolean changed = previous == null || !previous.equals(point.door);
            if (endpoint || changed) {
                thinMap.add(placed(point));
            } else {
                thinIds.add(point.id);
            }
        }
        thinned.addAll(thinIds);
        map.addAll(thinMap);
        for (Point point : group) {
            boolean onMap = false;
            for (Map<String, Object> place : thinMap) {
                if (point.id.equals(place.get("id"))) {
                    onMap = true;
                }
            }
            boolean isKept = kept.contains(point.id);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", point.id);
            row.put("profile", point.profile);
            row.put("door", point.door);
            row.put("at", Integer.valueOf(point.at));
            row.put("kept", Boolean.valueOf(isKept));
            row.put("thinned", Boolean.valueOf(thinIds.contains(point.id)));
            if (!isKept) {
                row.put("reason", reasons.get(point.id));
                row.put("x", null);
                row.put("y", null);
            } else if (thinIds.contains(point.id)) {
                row.put("reason", "thinned");
                row.put("x", Integer.valueOf(placeX(point.door)));
                row.put("y", Integer.valueOf(placeY(point.door)));
            } else {
                row.put("reason", null);
                row.put("x", onMap ? placeX(point.door) : null);
                row.put("y", onMap ? placeY(point.door) : null);
            }
            annotated.add(row);
        }
    }

    private static String reachReason(String previousDoor, boolean hasPrevious, int previousAt, Point point) {
        if (!known(point.door)) {
            return "unknown_door";
        }
        if (!linked(point.door)) {
            return "unreachable";
        }
        if (!hasPrevious) {
            return null;
        }
        int travel = DoorGraph.shortestSeconds(previousDoor, point.door);
        if (travel < 0) {
            return "unreachable";
        }
        if (point.at - previousAt < travel) {
            return "travel_conflict";
        }
        return null;
    }

    private static Map<String, Object> placed(Point point) {
        Map<String, Object> place = new LinkedHashMap<String, Object>();
        place.put("id", point.id);
        place.put("door", point.door);
        place.put("x", Integer.valueOf(placeX(point.door)));
        place.put("y", Integer.valueOf(placeY(point.door)));
        return place;
    }

    private static Map<String, Object> companions(Map<String, Object> input) {
        int window = input.get("window_seconds") == null ? COMPANION_WINDOW : integer(input.get("window_seconds"));
        List<Visit> visits = visits(input.get("visits"));
        Map<String, Integer> best = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < visits.size(); i++) {
            for (int j = i + 1; j < visits.size(); j++) {
                Visit left = visits.get(i);
                Visit right = visits.get(j);
                if (left.profile.equals(right.profile) || !left.door.equals(right.door)) {
                    continue;
                }
                int gap = Math.abs(left.at - right.at);
                if (gap > window) {
                    continue;
                }
                String first = left.profile.compareTo(right.profile) <= 0 ? left.profile : right.profile;
                String second = first.equals(left.profile) ? right.profile : left.profile;
                String key = first + "|" + second + "|" + left.door;
                Integer current = best.get(key);
                if (current == null || gap < current.intValue()) {
                    best.put(key, Integer.valueOf(gap));
                }
            }
        }
        List<String> keys = new ArrayList<String>(best.keySet());
        Collections.sort(keys);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (String key : keys) {
            String[] parts = key.split("\\|", -1);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("left", parts[0]);
            row.put("right", parts[1]);
            row.put("door", parts[2]);
            row.put("gap_seconds", best.get(key));
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("companions", rows);
        return result;
    }

    private static Map<String, Object> evidence(Map<String, Object> input) {
        String trajectoryId = text(input.get("trajectory_id"));
        List<String> points = strings(input.get("points"));
        List<Map<String, Object>> companions = companionRows(input.get("companions"));
        List<Map<String, Object>> sources = sourceRows(input.get("sources"));
        boolean missing = false;
        List<String> present = new ArrayList<String>();
        for (Map<String, Object> source : sources) {
            if (!Boolean.TRUE.equals(source.get("exists"))) {
                missing = true;
                continue;
            }
            present.add(text(source.get("id")));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (missing) {
            result.put("error", "evidence_missing");
            result.put("digest", null);
            result.put("manifest", "");
            result.put("valid", Boolean.FALSE);
            result.put("tampered_valid", Boolean.FALSE);
            return result;
        }
        String manifest = canonical(trajectoryId, points, companions, present);
        String digest = sha256(manifest);
        String tampered = manifest.replace("trajectory=" + trajectoryId + "\n", "trajectory=" + trajectoryId + "-x\n");
        result.put("error", "");
        result.put("digest", digest);
        result.put("manifest", manifest);
        result.put("valid", Boolean.valueOf(digest.equals(sha256(manifest))));
        result.put("tampered_valid", Boolean.valueOf(digest.equals(sha256(tampered))));
        result.put("companion_text", companionText(companions));
        result.put("point_keys", join(points));
        result.put("source_ids", join(present));
        return result;
    }

    private static Map<String, Object> hardCases(Map<String, Object> input) {
        List<Map<String, Object>> exported = new ArrayList<Map<String, Object>>();
        int unlabeled = 0;
        for (Map<String, Object> item : rows(input.get("cases"))) {
            String label = text(item.get("label"));
            if (!LABELS.contains(label)) {
                unlabeled++;
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", text(item.get("id")));
            row.put("reason", text(item.get("reason")));
            row.put("label", label);
            exported.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("exported", exported);
        result.put("unlabeled", Integer.valueOf(unlabeled));
        return result;
    }

    private static Map<String, Object> compare(Map<String, Object> input) {
        List<Map<String, Object>> candidates = rows(input.get("candidates"));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("trained", Boolean.FALSE);
        if (candidates.size() != 2) {
            result.put("winner", "none");
            result.put("left_score", null);
            result.put("right_score", null);
            result.put("delta", null);
            return result;
        }
        String leftName = text(candidates.get(0).get("name"));
        String rightName = text(candidates.get(1).get("name"));
        BigDecimal left = Quantities.persisted(new BigDecimal(text(candidates.get(0).get("score"))));
        BigDecimal right = Quantities.persisted(new BigDecimal(text(candidates.get(1).get("score"))));
        String winner = "tie";
        if (left.compareTo(right) > 0) {
            winner = leftName;
        } else if (right.compareTo(left) > 0) {
            winner = rightName;
        }
        result.put("winner", winner);
        result.put("left_name", leftName);
        result.put("right_name", rightName);
        result.put("left_score", left.toPlainString());
        result.put("right_score", right.toPlainString());
        result.put("delta", Quantities.persisted(right.subtract(left).abs()).toPlainString());
        return result;
    }

    private static String companionText(List<Map<String, Object>> companions) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < companions.size(); i++) {
            if (i > 0) {
                text.append(';');
            }
            Map<String, Object> row = companions.get(i);
            text.append(text(row.get("left"))).append('|')
                    .append(text(row.get("right"))).append('|')
                    .append(text(row.get("door"))).append('|')
                    .append(integer(row.get("gap_seconds")));
        }
        return text.toString();
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

    private static boolean known(String door) {
        for (DoorGraph.Node node : DoorGraph.NODES) {
            if (node.code.equals(door)) {
                return true;
            }
        }
        return false;
    }

    private static boolean linked(String door) {
        for (DoorGraph.Edge edge : DoorGraph.EDGES) {
            if (edge.from.equals(door) || edge.to.equals(door)) {
                return true;
            }
        }
        return false;
    }

    private static List<Point> points(Object raw) {
        List<Point> points = new ArrayList<Point>();
        for (Map<String, Object> row : rows(raw)) {
            points.add(new Point(text(row.get("id")), text(row.get("profile")), text(row.get("door")), integer(row.get("at"))));
        }
        return points;
    }

    private static List<Visit> visits(Object raw) {
        List<Visit> visits = new ArrayList<Visit>();
        for (Map<String, Object> row : rows(raw)) {
            visits.add(new Visit(text(row.get("profile")), text(row.get("door")), integer(row.get("at"))));
        }
        return visits;
    }

    private static List<Map<String, Object>> companionRows(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows(raw)) {
            String left = text(row.get("left"));
            String right = text(row.get("right"));
            if (left.compareTo(right) > 0) {
                String swap = left;
                left = right;
                right = swap;
            }
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            copy.put("left", left);
            copy.put("right", right);
            copy.put("door", text(row.get("door")));
            copy.put("gap_seconds", Integer.valueOf(integer(row.get("gap_seconds"))));
            rows.add(copy);
        }
        return rows;
    }

    private static List<Map<String, Object>> sourceRows(Object raw) {
        return rows(raw);
    }

    private static List<String> strings(Object raw) {
        List<String> values = new ArrayList<String>();
        if (!(raw instanceof List)) {
            return values;
        }
        for (Object item : (List<?>) raw) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        if (!(raw instanceof List)) {
            return rows;
        }
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                rows.add((Map<String, Object>) item);
            }
        }
        return rows;
    }

    private static int integer(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class Point {
        private final String id;
        private final String profile;
        private final String door;
        private final int at;

        private Point(String id, String profile, String door, int at) {
            this.id = id;
            this.profile = profile;
            this.door = door;
            this.at = at;
        }
    }

    private static final class Visit {
        private final String profile;
        private final String door;
        private final int at;

        private Visit(String profile, String door, int at) {
            this.profile = profile;
            this.door = door;
            this.at = at;
        }
    }
}
