package com.tracewitness.platform;

import com.tracewitness.platform.route.CloudReview;
import com.tracewitness.platform.route.Review;
import com.tracewitness.platform.route.RouteDecision;
import com.tracewitness.platform.route.RouteFacts;
import com.tracewitness.platform.route.RoutePolicy;
import com.tracewitness.platform.route.RouteWindows;
import com.tracewitness.platform.time.ClockMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePolicyTest {
    @Test
    void fixedInputReplaysTheSameDecision() {
        RoutePolicy policy = new RoutePolicy();
        RouteFacts facts = facts(ClockMode.EVAL, "0.90", "0.90", "0.20", 4, "incident_review", "0.50", "0.00", "light", "dark", "0.80");
        RouteDecision first = policy.decide(facts);
        RouteDecision second = policy.decide(facts);
        assertTrue(first.same(second));
        assertEquals("cloud", first.route);
        assertEquals("conflict", first.fusion);
        assertEquals("conflict", first.conclusion);
        assertTrue(first.cloudCalled);
        assertEquals(0, new BigDecimal("0.00").compareTo(first.qInput));
        assertEquals(0, new BigDecimal("0.50").compareTo(facts.qObserved));
    }

    @Test
    void shortHistoryIsDegradedAndStaysLocal() {
        int[] calls = new int[1];
        RoutePolicy policy = new RoutePolicy(countingCloud(calls));
        RouteDecision decision = policy.decide(facts(ClockMode.EVAL, "0.20", "0.80", "0.40", 1, "incident_review", "0.50", "0.00", "light", "dark", "0.90"));
        assertTrue(decision.degraded);
        assertEquals("local", decision.route);
        assertEquals("history_short", decision.reason);
        assertFalse(decision.cloudCalled);
        assertEquals(0, calls[0]);
        assertEquals(1, RouteWindows.get("gate_failure").samples.size());
    }

    @Test
    void evalUsesScriptedQueueAndRealUsesObserved() {
        RouteFacts evalFacts = facts(ClockMode.EVAL, "0.50", "0.50", "0.50", 5, "incident_review", "1.00", "0.00", "light", "light", "0.80");
        RouteFacts realFacts = facts(ClockMode.REAL, "0.50", "0.50", "0.50", 5, "incident_review", "1.00", "0.00", "light", "light", "0.80");
        RoutePolicy policy = new RoutePolicy();
        RouteDecision evalDecision = policy.decide(evalFacts);
        RouteDecision realDecision = policy.decide(realFacts);
        assertEquals("cloud", evalDecision.route);
        assertEquals("agree", evalDecision.fusion);
        assertEquals(0, new BigDecimal("0.00").compareTo(evalDecision.qInput));
        assertEquals("local", realDecision.route);
        assertEquals(0, new BigDecimal("1.00").compareTo(realDecision.qInput));
        assertFalse(realDecision.cloudCalled);
    }

    @Test
    void dualLowFusionHasOneConclusion() {
        RouteDecision decision = new RoutePolicy().decide(facts(
                ClockMode.EVAL, "0.10", "0.20", "0.10", 5, "incident_review", "0.50", "0.00", "light", "dark", "0.10"));
        assertEquals("cloud", decision.route);
        assertEquals("dual_low", decision.fusion);
        assertEquals("insufficient", decision.conclusion);
    }

    @Test
    void unknownBusinessDoesNotCallCloud() {
        int[] calls = new int[1];
        RouteDecision decision = new RoutePolicy(countingCloud(calls)).decide(facts(
                ClockMode.EVAL, "0.10", "0.10", "0.20", 3, "visitor_chat", "0.50", "0.00", "light", "dark", "0.90"));
        assertEquals("blocked", decision.route);
        assertEquals("unknown_business", decision.conclusion);
        assertFalse(decision.cloudCalled);
        assertEquals(0, calls[0]);
    }

    private static CloudReview countingCloud(final int[] calls) {
        return new CloudReview() {
            @Override
            public Review review(RouteFacts facts) {
                calls[0] += 1;
                return new Review(facts.cloudLabel, new BigDecimal(facts.cloudScore.toString()));
            }
        };
    }

    private static RouteFacts facts(ClockMode clockMode, String face, String body, String continuity, int history,
                                    String business, String qObserved, String qScripted, String localLabel,
                                    String cloudLabel, String cloudScore) {
        return new RouteFacts("case", clockMode, new BigDecimal(face), new BigDecimal(body), new BigDecimal(continuity),
                history, business, new BigDecimal(qObserved), new BigDecimal(qScripted), localLabel, cloudLabel,
                new BigDecimal(cloudScore));
    }
}
