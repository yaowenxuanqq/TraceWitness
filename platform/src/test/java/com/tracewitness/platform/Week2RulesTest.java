package com.tracewitness.platform;

import com.tracewitness.platform.id.EntityIds;
import com.tracewitness.platform.quant.Quantities;
import com.tracewitness.platform.seed.Seeds;
import com.tracewitness.platform.time.ClockMode;
import com.tracewitness.platform.time.TimeSource;
import com.tracewitness.platform.agent.CitationCheck;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Week2RulesTest {
    @Test
    void llmSeedMatchesDeterminismNote() {
        assertEquals(3169518213L, Seeds.subsystem(20261110L, "llm"));
    }

    @Test
    void quantitiesRoundHalfUpToTwoDecimals() {
        assertEquals(new BigDecimal("12.35"), Quantities.persisted(new BigDecimal("12.3456")));
        assertEquals(new BigDecimal("7.89"), Quantities.persisted(new BigDecimal("7.8912")));
        assertTrue(Quantities.withinTolerance(new BigDecimal("1.000"), new BigDecimal("1.004")));
        assertFalse(Quantities.withinTolerance(new BigDecimal("1.000"), new BigDecimal("1.006")));
    }

    @Test
    void evalClockUsesEpochAndTick() {
        TimeSource clock = new TimeSource();
        clock.load(ClockMode.EVAL, "2026-01-01T00:00:00.000Z", 1000, 3);
        assertEquals("2026-01-01T00:00:03.000Z", clock.formatNow());
    }

    @Test
    void realClockReadsWallTimeOncePerCall() {
        TimeSource clock = new TimeSource();
        clock.load(ClockMode.REAL, "2026-01-01T00:00:00.000Z", 1000, 0);
        Instant seen = clock.now();
        assertTrue(Math.abs(Duration.between(seen, Instant.now()).getSeconds()) < 5);
    }

    @Test
    void businessIdsAreStructured() {
        assertEquals(
                "tw.event.run-w02-mock-incident-20261110.000001",
                EntityIds.format("event", "run-w02-mock-incident-20261110", 1));
        assertThrows(IllegalArgumentException.class, () -> EntityIds.format("nope", "run-w02", 1));
    }

    @Test
    void citationRequiresIdsFromThisRun() {
        Set<String> allowed = Collections.singleton("tw.event.run-w02-mock-incident-20261110.000001");
        assertFalse(CitationCheck.invalid(allowed, Collections.singletonList("tw.event.run-w02-mock-incident-20261110.000001"), false));
        assertTrue(CitationCheck.invalid(allowed, Collections.singletonList("tw.event.other.000001"), false));
        assertTrue(CitationCheck.invalid(allowed, Collections.<String>emptyList(), false));
        assertFalse(CitationCheck.invalid(allowed, Collections.<String>emptyList(), true));
    }
}
