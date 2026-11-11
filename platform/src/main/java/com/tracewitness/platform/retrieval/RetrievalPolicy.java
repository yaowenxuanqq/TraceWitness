package com.tracewitness.platform.retrieval;

import com.tracewitness.platform.profile.ProfilePolicy;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.safety.ReverseRules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RetrievalPolicy {
    public static final String ORDER = "hard_filter,face_max,body_weighted,fuse,soft_weight";
    public static final BigDecimal SOFT_BONUS = new BigDecimal("0.05");
    public static final BigDecimal SCORE_CAP = new BigDecimal("1.00");

    private RetrievalPolicy() {
    }

    public static Map<String, Object> evaluate(Map<String, Object> input) {
        List<Candidate> candidates = candidates(input.get("candidates"));
        List<ImageQuery> images = images(input.get("images"));
        Map<String, String> hard = strings(input.get("hard"));
        Map<String, String> soft = strings(input.get("soft"));
        List<Map<String, Object>> hits = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> exclusions = new ArrayList<Map<String, Object>>();
        for (Candidate candidate : candidates) {
            String failed = hardFailure(candidate, hard);
            if (failed != null) {
                exclusions.add(exclusion(candidate.id, "hard_attribute", failed));
                continue;
            }
            BigDecimal face = faceMax(candidate, images);
            BigDecimal body = bodyWeighted(candidate, images);
            BigDecimal fused = fuse(face, body);
            boolean softApplied = softMatch(candidate, soft);
            BigDecimal rank = softApplied ? cap(fused.add(SOFT_BONUS)) : fused;
            Map<String, Object> hit = new LinkedHashMap<String, Object>();
            hit.put("id", candidate.id);
            hit.put("face_score", face == null ? null : face.toPlainString());
            hit.put("body_score", body == null ? null : body.toPlainString());
            hit.put("fused", fused.toPlainString());
            hit.put("rank_score", rank.toPlainString());
            hit.put("soft_applied", Boolean.valueOf(softApplied));
            hit.put("explanation", explanation(face, body, fused, softApplied, rank));
            hits.add(hit);
        }
        Collections.sort(hits, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                int score = new BigDecimal(String.valueOf(right.get("rank_score")))
                        .compareTo(new BigDecimal(String.valueOf(left.get("rank_score"))));
                if (score != 0) {
                    return score;
                }
                return String.valueOf(left.get("id")).compareTo(String.valueOf(right.get("id")));
            }
        });
        for (int i = 0; i < hits.size(); i++) {
            hits.get(i).put("rank", Integer.valueOf(i + 1));
        }
        Map<String, Object> rule = ReverseRules.evaluate("empty_search",
                Collections.<String, Object>singletonMap("hits", hits));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("order", ORDER);
        result.put("hits", hits);
        result.put("exclusions", exclusions);
        result.put("person_created", rule.get("person_created"));
        result.put("created", Boolean.FALSE);
        return result;
    }

    private static String hardFailure(Candidate candidate, Map<String, String> hard) {
        for (Map.Entry<String, String> entry : hard.entrySet()) {
            String actual = candidate.attributes.get(entry.getKey());
            if (actual == null || !actual.equals(entry.getValue())) {
                return "hard " + entry.getKey() + " wanted " + entry.getValue() + " got " + actual;
            }
        }
        return null;
    }

    private static boolean softMatch(Candidate candidate, Map<String, String> soft) {
        if (soft.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : soft.entrySet()) {
            if (!entry.getValue().equals(candidate.attributes.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal faceMax(Candidate candidate, List<ImageQuery> images) {
        if (candidate.face == null) {
            return null;
        }
        BigDecimal best = null;
        for (ImageQuery image : images) {
            if (image.face == null) {
                continue;
            }
            BigDecimal score = ProfilePolicy.cosine(image.face, candidate.face);
            if (best == null || score.compareTo(best) > 0) {
                best = score;
            }
        }
        return best;
    }

    private static BigDecimal bodyWeighted(Candidate candidate, List<ImageQuery> images) {
        if (candidate.body == null) {
            return null;
        }
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (ImageQuery image : images) {
            if (image.body == null || image.bodyQuality == null) {
                continue;
            }
            BigDecimal score = ProfilePolicy.cosine(image.body, candidate.body);
            weight = weight.add(image.bodyQuality);
            sum = sum.add(image.bodyQuality.multiply(score));
        }
        if (weight.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return Quantities.persisted(sum.divide(weight, 8, java.math.RoundingMode.HALF_UP));
    }

    private static BigDecimal fuse(BigDecimal face, BigDecimal body) {
        if (face != null && body != null) {
            return Quantities.persisted(face.add(body).divide(new BigDecimal("2"), 8, java.math.RoundingMode.HALF_UP));
        }
        if (face != null) {
            return face;
        }
        if (body != null) {
            return body;
        }
        return Quantities.persisted(BigDecimal.ZERO);
    }

    private static BigDecimal cap(BigDecimal value) {
        BigDecimal persisted = Quantities.persisted(value);
        if (persisted.compareTo(SCORE_CAP) > 0) {
            return SCORE_CAP;
        }
        return persisted;
    }

    private static String explanation(BigDecimal face, BigDecimal body, BigDecimal fused, boolean soft, BigDecimal rank) {
        return "hard pass; face " + show(face) + "; body " + show(body) + "; fused " + fused.toPlainString()
                + "; soft " + (soft ? SOFT_BONUS.toPlainString() : "none") + "; rank " + rank.toPlainString();
    }

    private static String show(BigDecimal value) {
        return value == null ? "none" : value.toPlainString();
    }

    private static Map<String, Object> exclusion(String id, String reason, String explanation) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("reason", reason);
        row.put("explanation", explanation);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static List<Candidate> candidates(Object raw) {
        List<Candidate> rows = new ArrayList<Candidate>();
        if (!(raw instanceof List)) {
            return rows;
        }
        for (Object item : (List<Object>) raw) {
            Map<?, ?> row = (Map<?, ?>) item;
            rows.add(new Candidate(text(row.get("id")), vector(row.get("face")), vector(row.get("body")), strings(row.get("attributes"))));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<ImageQuery> images(Object raw) {
        List<ImageQuery> rows = new ArrayList<ImageQuery>();
        if (!(raw instanceof List)) {
            return rows;
        }
        for (Object item : (List<Object>) raw) {
            Map<?, ?> row = (Map<?, ?>) item;
            BigDecimal quality = row.get("body_quality") == null ? null : new BigDecimal(String.valueOf(row.get("body_quality")));
            rows.add(new ImageQuery(vector(row.get("face")), vector(row.get("body")), quality));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> strings(Object raw) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (!(raw instanceof Map)) {
            return values;
        }
        Map<Object, Object> source = (Map<Object, Object>) raw;
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return values;
    }

    private static List<BigDecimal> vector(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String) {
            return ProfilePolicy.parseVector((String) raw);
        }
        List<BigDecimal> values = new ArrayList<BigDecimal>();
        for (Object item : (List<?>) raw) {
            values.add(new BigDecimal(String.valueOf(item)));
        }
        return values;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class Candidate {
        private final String id;
        private final List<BigDecimal> face;
        private final List<BigDecimal> body;
        private final Map<String, String> attributes;

        private Candidate(String id, List<BigDecimal> face, List<BigDecimal> body, Map<String, String> attributes) {
            this.id = id;
            this.face = face;
            this.body = body;
            this.attributes = attributes;
        }
    }

    private static final class ImageQuery {
        private final List<BigDecimal> face;
        private final List<BigDecimal> body;
        private final BigDecimal bodyQuality;

        private ImageQuery(List<BigDecimal> face, List<BigDecimal> body, BigDecimal bodyQuality) {
            this.face = face;
            this.body = body;
            this.bodyQuality = bodyQuality;
        }
    }
}
