package com.tracewitness.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracewitness")
public class AppProperties {
    private String mode = "MOCK";
    private String aiBaseUrl = "http://127.0.0.1:8000";
    private boolean autoLoop = true;
    private String mediaDir = "data/local/media";
    private String apiToken = "";
    private Eval eval = new Eval();

    public boolean mock() {
        return "MOCK".equalsIgnoreCase(mode);
    }

    public String banner() {
        return mock() ? "MOCK_MODE" : "MODEL_API";
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    public void setAiBaseUrl(String aiBaseUrl) {
        this.aiBaseUrl = aiBaseUrl;
    }

    public boolean isAutoLoop() {
        return autoLoop;
    }

    public void setAutoLoop(boolean autoLoop) {
        this.autoLoop = autoLoop;
    }

    public String getMediaDir() {
        return mediaDir;
    }

    public void setMediaDir(String mediaDir) {
        this.mediaDir = mediaDir;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public Eval getEval() {
        return eval;
    }

    public void setEval(Eval eval) {
        this.eval = eval;
    }

    public static class Eval {
        private String scenarioId = "w02-mock-incident";
        private String epochStart = "2026-01-01T00:00:00.000Z";
        private long tickMs = 1000;
        private long masterSeed = 20261110L;
        private String task = "调查北门刚刚发生了什么";

        public String getScenarioId() {
            return scenarioId;
        }

        public void setScenarioId(String scenarioId) {
            this.scenarioId = scenarioId;
        }

        public String getEpochStart() {
            return epochStart;
        }

        public void setEpochStart(String epochStart) {
            this.epochStart = epochStart;
        }

        public long getTickMs() {
            return tickMs;
        }

        public void setTickMs(long tickMs) {
            this.tickMs = tickMs;
        }

        public long getMasterSeed() {
            return masterSeed;
        }

        public void setMasterSeed(long masterSeed) {
            this.masterSeed = masterSeed;
        }

        public String getTask() {
            return task;
        }

        public void setTask(String task) {
            this.task = task;
        }
    }
}
