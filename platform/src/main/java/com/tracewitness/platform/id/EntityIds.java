package com.tracewitness.platform.id;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class EntityIds {
    public static final Set<String> ENTITIES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "event", "evidence", "device", "site", "track", "step", "tool",
            "agent", "profile", "trajectory", "artifact", "audit", "media", "point", "fault", "observation", "decision",
            "door", "edge", "sample", "access", "binding", "ticket",
            "query", "hit", "exclusion", "passage",
            "companion", "package", "hardcase", "compare",
            "plugin", "health", "rollback")));
    private static final Pattern RUN_ID = Pattern.compile("[a-z0-9-]+");

    private EntityIds() {
    }

    public static String format(String entity, String runId, long seq) {
        if (!ENTITIES.contains(entity)) {
            throw new IllegalArgumentException("unknown entity: " + entity);
        }
        if (runId == null || !RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("run_id must be lowercase letters, digits, and hyphens");
        }
        if (seq < 1 || seq > 999999) {
            throw new IllegalArgumentException("seq out of range: " + seq);
        }
        return "tw." + entity + "." + runId + "." + String.format("%06d", seq);
    }
}
