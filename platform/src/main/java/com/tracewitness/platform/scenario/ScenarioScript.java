package com.tracewitness.platform.scenario;

import java.math.BigDecimal;
import java.util.List;

public class ScenarioScript {
    public List<SiteSpec> sites;
    public List<DeviceSpec> devices;
    public List<EventSpec> events;

    public static class SiteSpec {
        public String code;
        public String name;
        public BigDecimal xM;
        public BigDecimal yM;
    }

    public static class DeviceSpec {
        public String code;
        public String siteCode;
        public String kind;
        public String name;
    }

    public static class EventSpec {
        public long tick;
        public String deviceCode;
        public String eventType;
        public String summary;
    }
}
