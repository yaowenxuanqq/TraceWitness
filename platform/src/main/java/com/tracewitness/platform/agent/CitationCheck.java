package com.tracewitness.platform.agent;

import java.util.List;
import java.util.Set;

public final class CitationCheck {
    private CitationCheck() {
    }

    public static boolean invalid(Set<String> allowed, List<String> cited, boolean insufficient) {
        if (cited == null || cited.isEmpty()) {
            return !insufficient;
        }
        for (String id : cited) {
            if (id == null || !allowed.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
