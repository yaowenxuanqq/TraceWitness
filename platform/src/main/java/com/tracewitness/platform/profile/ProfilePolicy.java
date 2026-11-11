package com.tracewitness.platform.profile;

import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.safety.ReverseRules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfilePolicy {
    public static final BigDecimal MATCH_MIN = new BigDecimal("0.80");
    public static final BigDecimal MARGIN = new BigDecimal("0.05");

    private ProfilePolicy() {
    }

    public static Map<String, Object> evaluate(String op, Map<String, Object> input) {
        if ("centroid".equals(op)) {
            return centroidCase(input);
        }
        if ("path".equals(op)) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("travel_seconds", DoorGraph.shortestSeconds(text(input.get("from_door")), text(input.get("to_door"))));
            return result;
        }
        if ("address".equals(op)) {
            String current = input.get("current") == null ? null : text(input.get("current"));
            Map<String, Integer> visits = integers(input.get("visits"));
            String address = addressLabel(current, visits);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("address", address);
            result.put("frequency", Boolean.valueOf(frequency(visits.get(address))));
            return result;
        }
        if ("approval".equals(op)) {
            return approval(text(input.get("status")), text(input.get("decision")));
        }
        return decide(
                decimal(input.get("face_quality")),
                vector(input.get("vector")),
                text(input.get("from_door")),
                input.get("to_door") == null ? null : text(input.get("to_door")),
                integer(input.get("tick_from")),
                integer(input.get("tick_to")),
                integer(input.get("tick_ms")),
                galleries(input.get("profiles"))).toMap();
    }

    public static Decision decide(BigDecimal faceQuality, List<BigDecimal> vector, String fromDoor, String toDoor,
                                  long tickFrom, long tickTo, long tickMs, List<GalleryProfile> profiles) {
        List<Hit> ranked = rank(vector, profiles);
        Hit best = ranked.isEmpty() ? null : ranked.get(0);
        Hit second = ranked.size() < 2 ? null : ranked.get(1);
        boolean secondDoor = toDoor != null && toDoor.length() > 0 && !toDoor.equals(fromDoor);
        Integer travel = null;
        Integer observed = null;
        if (secondDoor) {
            travel = Integer.valueOf(DoorGraph.shortestSeconds(fromDoor, toDoor));
            observed = Integer.valueOf((int) ((tickTo - tickFrom) * tickMs / 1000L));
            if (travel.intValue() < 0) {
                return Decision.blocked("forbidden", "unreachable", best, second, false, travel, observed);
            }
            boolean conflict = observed.intValue() < travel.intValue();
            Map<String, Object> rule = ReverseRules.evaluate("travel_conflict",
                    Collections.<String, Object>singletonMap("travel_conflict", Boolean.valueOf(conflict)));
            if ("forbidden".equals(rule.get("merge"))) {
                return Decision.blocked("forbidden", "travel_conflict", best, second, false, travel, observed);
            }
        }
        if (ambiguous(best, second)) {
            return Decision.blocked("pending_human", "ambiguous", best, second, false, travel, observed);
        }
        if (best != null && best.score.compareTo(MATCH_MIN) >= 0) {
            return new Decision("auto", "matched", best.gallery, best.id, null, false, travel, observed);
        }
        Map<String, Object> quality = ReverseRules.evaluate("low_quality_face",
                Collections.<String, Object>singletonMap("face_quality", faceQuality.toPlainString()));
        if (!Boolean.TRUE.equals(quality.get("profile_created"))) {
            return new Decision("none", "low_quality", "none", null, null, false, travel, observed);
        }
        return new Decision("none", "created", "stranger", null, null, true, travel, observed);
    }

    public static List<BigDecimal> centroid(List<SampleWeight> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("centroid requires a sample");
        }
        int dimensions = samples.get(0).vector.size();
        BigDecimal weight = BigDecimal.ZERO;
        List<BigDecimal> sum = new ArrayList<BigDecimal>();
        for (int i = 0; i < dimensions; i++) {
            sum.add(BigDecimal.ZERO);
        }
        for (SampleWeight sample : samples) {
            weight = weight.add(sample.quality);
            for (int i = 0; i < dimensions; i++) {
                sum.set(i, sum.get(i).add(sample.quality.multiply(sample.vector.get(i))));
            }
        }
        List<BigDecimal> center = new ArrayList<BigDecimal>();
        for (BigDecimal component : sum) {
            center.add(Quantities.persisted(component.divide(weight, 8, java.math.RoundingMode.HALF_UP)));
        }
        return center;
    }

    public static String addressLabel(String current, Map<String, Integer> visits) {
        if (visits == null || visits.isEmpty()) {
            return current;
        }
        int max = 0;
        for (Integer count : visits.values()) {
            max = Math.max(max, count == null ? 0 : count.intValue());
        }
        if (max == 0) {
            return current;
        }
        if (current != null && current.length() > 0 && count(visits, current) == max) {
            return current;
        }
        String chosen = null;
        for (Map.Entry<String, Integer> entry : visits.entrySet()) {
            int value = entry.getValue() == null ? 0 : entry.getValue().intValue();
            if (value == max && (chosen == null || entry.getKey().compareTo(chosen) < 0)) {
                chosen = entry.getKey();
            }
        }
        return chosen;
    }

    public static boolean frequency(Integer visits) {
        return visits != null && visits.intValue() >= 3;
    }

    public static Map<String, Object> approval(String status, String decision) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("merged", Boolean.FALSE);
        if (!"pending_human".equals(status)) {
            result.put("status", status);
            return result;
        }
        if ("approve".equals(decision)) {
            result.put("status", "approved");
            result.put("merged", Boolean.TRUE);
            return result;
        }
        if ("reject".equals(decision)) {
            result.put("status", "rejected");
            return result;
        }
        result.put("status", status);
        return result;
    }

    public static BigDecimal cosine(List<BigDecimal> left, List<BigDecimal> right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i).doubleValue();
            double b = right.get(i).doubleValue();
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return Quantities.persisted(BigDecimal.ZERO);
        }
        double value = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        return Quantities.persisted(BigDecimal.valueOf(value));
    }

    public static String formatVector(List<BigDecimal> vector) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector.get(i).toPlainString());
        }
        return builder.toString();
    }

    public static List<BigDecimal> parseVector(String raw) {
        String[] parts = raw.split(",");
        List<BigDecimal> vector = new ArrayList<BigDecimal>();
        for (String part : parts) {
            vector.add(new BigDecimal(part.trim()));
        }
        return vector;
    }

    private static Map<String, Object> centroidCase(Map<String, Object> input) {
        List<SampleWeight> samples = new ArrayList<SampleWeight>();
        for (Object item : (List<?>) input.get("samples")) {
            Map<?, ?> row = (Map<?, ?>) item;
            samples.add(new SampleWeight(decimal(row.get("quality")), vector(row.get("vector"))));
        }
        List<String> formatted = new ArrayList<String>();
        for (BigDecimal component : centroid(samples)) {
            formatted.add(component.toPlainString());
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("centroid", formatted);
        return result;
    }

    private static List<Hit> rank(List<BigDecimal> vector, List<GalleryProfile> profiles) {
        List<Hit> hits = new ArrayList<Hit>();
        for (GalleryProfile profile : profiles) {
            hits.add(new Hit(profile.id, profile.gallery, cosine(vector, profile.vector)));
        }
        List<Hit> ranked = new ArrayList<Hit>();
        while (!hits.isEmpty()) {
            int winner = 0;
            for (int i = 1; i < hits.size(); i++) {
                if (hits.get(i).score.compareTo(hits.get(winner).score) > 0) {
                    winner = i;
                }
            }
            ranked.add(hits.remove(winner));
        }
        return ranked;
    }

    private static boolean ambiguous(Hit best, Hit second) {
        return best != null && second != null
                && best.score.compareTo(MATCH_MIN) >= 0
                && second.score.compareTo(MATCH_MIN) >= 0
                && best.score.subtract(second.score).compareTo(MARGIN) < 0;
    }

    private static int count(Map<String, Integer> visits, String door) {
        Integer value = visits.get(door);
        return value == null ? 0 : value.intValue();
    }

    @SuppressWarnings("unchecked")
    private static List<GalleryProfile> galleries(Object raw) {
        if (!(raw instanceof List)) {
            return Collections.emptyList();
        }
        List<GalleryProfile> profiles = new ArrayList<GalleryProfile>();
        for (Object item : (List<Object>) raw) {
            Map<?, ?> row = (Map<?, ?>) item;
            profiles.add(new GalleryProfile(text(row.get("id")), text(row.get("gallery")), vector(row.get("vector"))));
        }
        return profiles;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> integers(Object raw) {
        Map<String, Integer> visits = new LinkedHashMap<String, Integer>();
        if (!(raw instanceof Map)) {
            return visits;
        }
        Map<Object, Object> source = (Map<Object, Object>) raw;
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            visits.put(String.valueOf(entry.getKey()), Integer.valueOf(integer(entry.getValue())));
        }
        return visits;
    }

    private static List<BigDecimal> vector(Object raw) {
        List<BigDecimal> values = new ArrayList<BigDecimal>();
        if (raw instanceof String) {
            return parseVector((String) raw);
        }
        for (Object item : (List<?>) raw) {
            values.add(decimal(item));
        }
        return values;
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private static int integer(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static final class GalleryProfile {
        public final String id;
        public final String gallery;
        public final List<BigDecimal> vector;

        public GalleryProfile(String id, String gallery, List<BigDecimal> vector) {
            this.id = id;
            this.gallery = gallery;
            this.vector = vector;
        }
    }

    public static final class SampleWeight {
        public final BigDecimal quality;
        public final List<BigDecimal> vector;

        public SampleWeight(BigDecimal quality, List<BigDecimal> vector) {
            this.quality = quality;
            this.vector = vector;
        }
    }

    public static final class Decision {
        public final String merge;
        public final String reason;
        public final String gallery;
        public final String matchedId;
        public final String secondId;
        public final boolean profileCreated;
        public final Integer travelSeconds;
        public final Integer observedSeconds;

        public Decision(String merge, String reason, String gallery, String matchedId, String secondId,
                        boolean profileCreated, Integer travelSeconds, Integer observedSeconds) {
            this.merge = merge;
            this.reason = reason;
            this.gallery = gallery;
            this.matchedId = matchedId;
            this.secondId = secondId;
            this.profileCreated = profileCreated;
            this.travelSeconds = travelSeconds;
            this.observedSeconds = observedSeconds;
        }

        static Decision blocked(String merge, String reason, Hit best, Hit second, boolean created,
                                Integer travel, Integer observed) {
            return new Decision(merge, reason,
                    best == null ? "none" : best.gallery,
                    best == null ? null : best.id,
                    second == null ? null : second.id,
                    created, travel, observed);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("merge", merge);
            row.put("reason", reason);
            row.put("gallery", gallery);
            row.put("matched_id", matchedId);
            row.put("second_id", secondId);
            row.put("profile_created", Boolean.valueOf(profileCreated));
            if (travelSeconds != null) {
                row.put("travel_seconds", travelSeconds);
            }
            if (observedSeconds != null) {
                row.put("observed_seconds", observedSeconds);
            }
            return row;
        }
    }

    private static final class Hit {
        private final String id;
        private final String gallery;
        private final BigDecimal score;

        private Hit(String id, String gallery, BigDecimal score) {
            this.id = id;
            this.gallery = gallery;
            this.score = score;
        }
    }
}
