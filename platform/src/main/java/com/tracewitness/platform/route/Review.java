package com.tracewitness.platform.route;

import java.math.BigDecimal;

public final class Review {
    public final String label;
    public final BigDecimal score;

    public Review(String label, BigDecimal score) {
        this.label = label;
        this.score = score;
    }
}
