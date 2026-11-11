package com.tracewitness.platform.quant;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Quantities {
    public static final BigDecimal TOLERANCE = new BigDecimal("0.005");

    private Quantities() {
    }

    public static BigDecimal persisted(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean withinTolerance(BigDecimal actual, BigDecimal expected) {
        return actual.subtract(expected).abs().compareTo(TOLERANCE) <= 0;
    }
}
