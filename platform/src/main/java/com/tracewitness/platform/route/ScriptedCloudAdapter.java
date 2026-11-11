package com.tracewitness.platform.route;

import com.tracewitness.platform.quant.Quantities;

public final class ScriptedCloudAdapter implements CloudReview {
    @Override
    public Review review(RouteFacts facts) {
        if (facts.cloudLabel == null || facts.cloudScore == null) {
            throw new IllegalStateException("cloud result is not scripted");
        }
        return new Review(facts.cloudLabel, Quantities.persisted(facts.cloudScore));
    }
}
