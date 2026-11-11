package com.tracewitness.platform.route;

import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.safety.ReverseRules;
import com.tracewitness.platform.time.ClockMode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RoutePolicy {
    public static final String VERSION = "route-w06-1";
    public static final int MIN_HISTORY = 3;
    public static final BigDecimal SCORE_FLOOR = new BigDecimal("0.50");
    private static final BigDecimal FACE_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal BODY_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal CONTINUITY_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal THETA = new BigDecimal("0.35");
    private static final BigDecimal QUEUE_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal ONE = new BigDecimal("1");

    private final LocalReviewAdapter local = new LocalReviewAdapter();
    private final CloudReview cloud;

    public RoutePolicy() {
        this(new ScriptedCloudAdapter());
    }

    public RoutePolicy(CloudReview cloud) {
        this.cloud = cloud;
    }

    public static BigDecimal continuity(String mode) {
        if ("preferred".equals(mode)) {
            return new BigDecimal("1.00");
        }
        if ("gate_failure".equals(mode)) {
            return new BigDecimal("0.40");
        }
        if ("track_break".equals(mode) || "inference_error".equals(mode)) {
            return new BigDecimal("0.20");
        }
        throw new IllegalArgumentException("unknown observation mode: " + mode);
    }

    public RouteDecision decide(RouteFacts facts) {
        BigDecimal face = Quantities.persisted(facts.faceQuality);
        BigDecimal body = Quantities.persisted(facts.bodyQuality);
        BigDecimal continuity = Quantities.persisted(facts.continuity);
        BigDecimal covered = scale4(FACE_WEIGHT, face).add(scale4(BODY_WEIGHT, body)).add(scale4(CONTINUITY_WEIGHT, continuity));
        BigDecimal uncertainty = Quantities.persisted(ONE.subtract(covered));
        BigDecimal qObserved = Quantities.persisted(facts.qObserved);
        BigDecimal qScripted = Quantities.persisted(facts.qScripted);
        BigDecimal qInput = facts.clockMode == ClockMode.REAL ? qObserved : qScripted;
        BigDecimal theta = Quantities.persisted(THETA.add(scale4(QUEUE_WEIGHT, qInput)));
        boolean degraded = facts.historyCount < MIN_HISTORY;
        if (!ReverseRules.allowsPublicNetwork(facts.business)) {
            return new RouteDecision(VERSION, uncertainty, qInput, theta, degraded, "blocked", "unknown_business",
                    "refused", "unknown_business", false, null, null, null, null);
        }
        Review localReview = local.review(facts);
        if (degraded) {
            return finished(uncertainty, qInput, theta, true, "local", "history_short", "local",
                    localReview.label, false, localReview, null);
        }
        if (uncertainty.compareTo(theta) > 0) {
            Review cloudReview = cloud.review(facts);
            String fusion = fuse(localReview, cloudReview);
            String conclusion = conclusion(fusion, localReview, cloudReview);
            return finished(uncertainty, qInput, theta, false, "cloud", "uncertain", fusion,
                    conclusion, true, localReview, cloudReview);
        }
        return finished(uncertainty, qInput, theta, false, "local", "within_threshold", "local",
                localReview.label, false, localReview, null);
    }

    private static RouteDecision finished(BigDecimal uncertainty, BigDecimal qInput, BigDecimal theta, boolean degraded,
                                          String route, String reason, String fusion, String conclusion,
                                          boolean cloudCalled, Review localReview, Review cloudReview) {
        return new RouteDecision(VERSION, uncertainty, qInput, theta, degraded, route, reason, fusion, conclusion,
                cloudCalled, localReview.label, localReview.score,
                cloudReview == null ? null : cloudReview.label,
                cloudReview == null ? null : cloudReview.score);
    }

    private static String fuse(Review localReview, Review cloudReview) {
        boolean localHigh = localReview.score.compareTo(SCORE_FLOOR) >= 0;
        boolean cloudHigh = cloudReview.score.compareTo(SCORE_FLOOR) >= 0;
        if (localHigh && cloudHigh && localReview.label.equals(cloudReview.label)) {
            return "agree";
        }
        if (localHigh && cloudHigh) {
            return "conflict";
        }
        return "dual_low";
    }

    private static String conclusion(String fusion, Review localReview, Review cloudReview) {
        if ("agree".equals(fusion)) {
            return localReview.label;
        }
        if ("conflict".equals(fusion)) {
            return "conflict";
        }
        return "insufficient";
    }

    private static BigDecimal scale4(BigDecimal weight, BigDecimal value) {
        return weight.multiply(value).setScale(4, RoundingMode.HALF_UP);
    }
}
