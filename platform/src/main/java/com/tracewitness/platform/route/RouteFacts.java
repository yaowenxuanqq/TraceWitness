package com.tracewitness.platform.route;

import com.tracewitness.platform.time.ClockMode;

import java.math.BigDecimal;

public final class RouteFacts {
    public final String caseKey;
    public final ClockMode clockMode;
    public final BigDecimal faceQuality;
    public final BigDecimal bodyQuality;
    public final BigDecimal continuity;
    public final int historyCount;
    public final String business;
    public final BigDecimal qObserved;
    public final BigDecimal qScripted;
    public final String localLabel;
    public final String cloudLabel;
    public final BigDecimal cloudScore;

    public RouteFacts(String caseKey, ClockMode clockMode, BigDecimal faceQuality, BigDecimal bodyQuality,
                      BigDecimal continuity, int historyCount, String business, BigDecimal qObserved,
                      BigDecimal qScripted, String localLabel, String cloudLabel, BigDecimal cloudScore) {
        this.caseKey = caseKey;
        this.clockMode = clockMode;
        this.faceQuality = faceQuality;
        this.bodyQuality = bodyQuality;
        this.continuity = continuity;
        this.historyCount = historyCount;
        this.business = business;
        this.qObserved = qObserved;
        this.qScripted = qScripted;
        this.localLabel = localLabel;
        this.cloudLabel = cloudLabel;
        this.cloudScore = cloudScore;
    }
}
