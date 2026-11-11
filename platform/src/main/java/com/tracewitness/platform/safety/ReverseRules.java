package com.tracewitness.platform.safety;

import com.tracewitness.platform.quant.Quantities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReverseRules {
    public static final BigDecimal FACE_PROFILE_MIN = new BigDecimal("0.50");
    public static final Set<String> KNOWN_BUSINESS = Collections.singleton("incident_review");
    private static final Set<String> HIGH_RISK = Collections.unmodifiableSet(
            new java.util.HashSet<String>(Arrays.asList("door_open", "elevator_call")));

    private ReverseRules() {
    }

    public static boolean allowsPublicNetwork(String business) {
        return business != null && KNOWN_BUSINESS.contains(business);
    }

    public static Map<String, Object> evaluate(String id, Map<String, Object> input) {
        if ("tool_timeout".equals(id)) {
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            if (Boolean.TRUE.equals(input.get("timed_out"))) {
                outcome.put("outcome", "no_result");
                outcome.put("fabricated", false);
                return outcome;
            }
            outcome.put("outcome", "use_payload");
            outcome.put("fabricated", false);
            outcome.put("payload", input.get("payload"));
            return outcome;
        }
        if ("empty_search".equals(id)) {
            List<?> hits = input.get("hits") instanceof List ? (List<?>) input.get("hits") : Collections.emptyList();
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("person_created", !hits.isEmpty());
            return outcome;
        }
        if ("illegal_source".equals(id)) {
            List<?> allowed = input.get("allowed") instanceof List ? (List<?>) input.get("allowed") : Collections.emptyList();
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("accepted", allowed.contains(String.valueOf(input.get("source_id"))));
            return outcome;
        }
        if ("unreachable_track".equals(id)) {
            List<?> points = input.get("points") instanceof List ? (List<?>) input.get("points") : Collections.emptyList();
            List<String> kept = new ArrayList<String>();
            for (Object point : points) {
                if (!(point instanceof Map)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) point;
                if (Boolean.TRUE.equals(row.get("reachable"))) {
                    kept.add(String.valueOf(row.get("id")));
                }
            }
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("kept", kept);
            return outcome;
        }
        if ("travel_conflict".equals(id)) {
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("merge", Boolean.TRUE.equals(input.get("travel_conflict")) ? "forbidden" : "allowed");
            return outcome;
        }
        if ("low_quality_face".equals(id)) {
            BigDecimal quality = Quantities.persisted(new BigDecimal(String.valueOf(input.get("face_quality"))));
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("profile_created", quality.compareTo(FACE_PROFILE_MIN) >= 0);
            return outcome;
        }
        if ("bad_signature".equals(id)) {
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("loaded", Boolean.TRUE.equals(input.get("signature_valid")));
            return outcome;
        }
        if ("high_risk".equals(id)) {
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            if (HIGH_RISK.contains(String.valueOf(input.get("action")))) {
                outcome.put("status", "pending_human");
                outcome.put("executed", false);
            } else {
                outcome.put("status", "not_required");
                outcome.put("executed", false);
            }
            return outcome;
        }
        if ("unknown_business".equals(id)) {
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            outcome.put("public_network", allowsPublicNetwork(String.valueOf(input.get("business"))));
            return outcome;
        }
        if ("missing_evidence".equals(id)) {
            Map<String, Object> outcome = new LinkedHashMap<String, Object>();
            if (Boolean.TRUE.equals(input.get("path_exists"))) {
                outcome.put("error", "");
            } else {
                outcome.put("error", "evidence_missing");
            }
            return outcome;
        }
        throw new IllegalArgumentException("unknown reverse assertion: " + id);
    }
}
