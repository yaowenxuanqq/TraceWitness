package com.tracewitness.platform.time;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class TimeSource {
    public static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private ClockMode mode = ClockMode.EVAL;
    private Instant epochStart = Instant.parse("2026-01-01T00:00:00.000Z");
    private long tickMs = 1000;
    private long tick;

    public synchronized void load(ClockMode mode, String epochStart, long tickMs, long tick) {
        this.mode = mode;
        this.epochStart = Instant.parse(epochStart);
        this.tickMs = tickMs;
        this.tick = tick;
    }

    public synchronized void setTick(long tick) {
        this.tick = tick;
    }

    public synchronized long tick() {
        return tick;
    }

    public synchronized ClockMode mode() {
        return mode;
    }

    public synchronized Instant now() {
        if (mode == ClockMode.EVAL) {
            return epochStart.plusMillis(Math.multiplyExact(tick, tickMs));
        }
        return Instant.now();
    }

    public synchronized String formatNow() {
        return FORMAT.format(now());
    }
}
