package com.tracewitness.platform.route;

import com.tracewitness.platform.quant.Quantities;

public final class LocalReviewAdapter {
    public Review review(RouteFacts facts) {
        return new Review(facts.localLabel, Quantities.persisted(facts.bodyQuality));
    }
}
