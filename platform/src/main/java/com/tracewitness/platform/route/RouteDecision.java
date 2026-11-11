package com.tracewitness.platform.route;

import java.math.BigDecimal;

public final class RouteDecision {
    public final String policyVersion;
    public final BigDecimal uncertainty;
    public final BigDecimal qInput;
    public final BigDecimal thetaUEff;
    public final boolean degraded;
    public final String route;
    public final String reason;
    public final String fusion;
    public final String conclusion;
    public final boolean cloudCalled;
    public final String localLabel;
    public final BigDecimal localScore;
    public final String cloudLabel;
    public final BigDecimal cloudScore;

    public RouteDecision(String policyVersion, BigDecimal uncertainty, BigDecimal qInput, BigDecimal thetaUEff,
                         boolean degraded, String route, String reason, String fusion, String conclusion,
                         boolean cloudCalled, String localLabel, BigDecimal localScore, String cloudLabel,
                         BigDecimal cloudScore) {
        this.policyVersion = policyVersion;
        this.uncertainty = uncertainty;
        this.qInput = qInput;
        this.thetaUEff = thetaUEff;
        this.degraded = degraded;
        this.route = route;
        this.reason = reason;
        this.fusion = fusion;
        this.conclusion = conclusion;
        this.cloudCalled = cloudCalled;
        this.localLabel = localLabel;
        this.localScore = localScore;
        this.cloudLabel = cloudLabel;
        this.cloudScore = cloudScore;
    }

    public boolean same(RouteDecision other) {
        return policyVersion.equals(other.policyVersion)
                && uncertainty.compareTo(other.uncertainty) == 0
                && qInput.compareTo(other.qInput) == 0
                && thetaUEff.compareTo(other.thetaUEff) == 0
                && degraded == other.degraded
                && route.equals(other.route)
                && reason.equals(other.reason)
                && fusion.equals(other.fusion)
                && conclusion.equals(other.conclusion)
                && cloudCalled == other.cloudCalled
                && equalsText(localLabel, other.localLabel)
                && equalsDecimal(localScore, other.localScore)
                && equalsText(cloudLabel, other.cloudLabel)
                && equalsDecimal(cloudScore, other.cloudScore);
    }

    private static boolean equalsText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean equalsDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }
}
