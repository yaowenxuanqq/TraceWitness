package com.tracewitness.platform.retrieval;

import com.tracewitness.platform.safety.ReverseRules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PassagePolicy {
    public static final int CONFIRM_FRAMES = 3;

    private PassagePolicy() {
    }

    public static Map<String, Object> evaluate(Map<String, Object> input) {
        String scene = input.get("scene") == null ? "" : String.valueOf(input.get("scene"));
        int streak = 0;
        int best = 0;
        Object raw = input.get("frames");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (!(item instanceof Map)) {
                    streak = 0;
                    continue;
                }
                Map<?, ?> frame = (Map<?, ?>) item;
                boolean inbound = "bicycle".equals(String.valueOf(frame.get("kind")))
                        && Boolean.TRUE.equals(frame.get("crossed"))
                        && "in".equals(String.valueOf(frame.get("direction")));
                if (inbound) {
                    streak++;
                    best = Math.max(best, streak);
                } else {
                    streak = 0;
                }
            }
        }
        boolean confirmed = "entrance".equals(scene) && best >= CONFIRM_FRAMES;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("streak", Integer.valueOf(best));
        result.put("alert", Boolean.valueOf(confirmed));
        if (!confirmed) {
            result.put("level", "none");
            result.put("status", "none");
            result.put("action_status", "not_required");
            result.put("executed", Boolean.FALSE);
            return result;
        }
        Map<String, Object> rule = ReverseRules.evaluate("high_risk",
                Collections.<String, Object>singletonMap("action", "door_open"));
        result.put("level", "violation");
        result.put("status", rule.get("status"));
        result.put("action_status", rule.get("status"));
        result.put("executed", rule.get("executed"));
        return result;
    }
}
