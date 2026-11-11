package com.tracewitness.platform.route;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteWindows {
    public static final BigDecimal Q_OBSERVED = new BigDecimal("0.50");
    public static final BigDecimal Q_SCRIPTED = new BigDecimal("0.00");
    private static final Map<String, DeviceWindow> WINDOWS = windows();

    private RouteWindows() {
    }

    public static DeviceWindow get(String caseKey) {
        DeviceWindow window = WINDOWS.get(caseKey);
        if (window == null) {
            throw new IllegalArgumentException("no device window for " + caseKey);
        }
        return window;
    }

    private static Map<String, DeviceWindow> windows() {
        Map<String, DeviceWindow> rows = new LinkedHashMap<String, DeviceWindow>();
        rows.put("live", new DeviceWindow(decimals("0.90", "0.91", "0.88", "0.92", "0.90"), "light", "0.90"));
        rows.put("gate_failure", new DeviceWindow(decimals("0.22"), "light", "0.40"));
        rows.put("track_break", new DeviceWindow(decimals("0.70", "0.72", "0.68", "0.71"), "dark", "0.80"));
        rows.put("unknown_business", new DeviceWindow(decimals("0.50", "0.50", "0.50"), "dark", "0.90"));
        return Collections.unmodifiableMap(rows);
    }

    private static List<BigDecimal> decimals(String... values) {
        BigDecimal[] rows = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            rows[i] = new BigDecimal(values[i]);
        }
        return Collections.unmodifiableList(Arrays.asList(rows));
    }

    public static final class DeviceWindow {
        public final List<BigDecimal> samples;
        public final String cloudLabel;
        public final BigDecimal cloudScore;

        private DeviceWindow(List<BigDecimal> samples, String cloudLabel, String cloudScore) {
            this.samples = samples;
            this.cloudLabel = cloudLabel;
            this.cloudScore = new BigDecimal(cloudScore);
        }
    }
}
