package com.tracewitness.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracewitness")
public class AppProperties {
    private String mode = "MOCK";
    private String aiBaseUrl = "http://127.0.0.1:8000";
    private boolean autoLoop = true;
    private String mediaDir = "data/local/media";
    private String apiToken = "";
    private String serviceToken = "";
    private Eval eval = new Eval();
    private Overview overview = new Overview();
    private Media media = new Media();

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

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public Overview getOverview() {
        return overview;
    }

    public void setOverview(Overview overview) {
        this.overview = overview;
    }

    public Media getMedia() {
        return media;
    }

    public void setMedia(Media media) {
        this.media = media;
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

    public static class Overview {
        private static final int HARD_MAX = 500;
        private static final int CHILD_HARD_MAX = 100;
        private int events = 20;
        private int agentSteps = 50;
        private int toolCalls = 50;
        private int tracks = 200;
        private int observations = 200;
        private int decisions = 200;
        private int profiles = 100;
        private int tickets = 100;
        private int queries = 50;
        private int passages = 100;
        private int trajectories = 50;
        private int packages = 50;
        private int hardCases = 50;
        private int plugins = 50;
        private int doors = 100;
        private int audits = 100;
        private int companions = 100;
        private int compares = 50;
        private int childItems = 20;

        public int getEvents() { return clamp(events, HARD_MAX); }
        public void setEvents(int events) { this.events = events; }
        public int getAgentSteps() { return clamp(agentSteps, HARD_MAX); }
        public void setAgentSteps(int agentSteps) { this.agentSteps = agentSteps; }
        public int getToolCalls() { return clamp(toolCalls, HARD_MAX); }
        public void setToolCalls(int toolCalls) { this.toolCalls = toolCalls; }
        public int getTracks() { return clamp(tracks, HARD_MAX); }
        public void setTracks(int tracks) { this.tracks = tracks; }
        public int getObservations() { return clamp(observations, HARD_MAX); }
        public void setObservations(int observations) { this.observations = observations; }
        public int getDecisions() { return clamp(decisions, HARD_MAX); }
        public void setDecisions(int decisions) { this.decisions = decisions; }
        public int getProfiles() { return clamp(profiles, HARD_MAX); }
        public void setProfiles(int profiles) { this.profiles = profiles; }
        public int getTickets() { return clamp(tickets, HARD_MAX); }
        public void setTickets(int tickets) { this.tickets = tickets; }
        public int getQueries() { return clamp(queries, HARD_MAX); }
        public void setQueries(int queries) { this.queries = queries; }
        public int getPassages() { return clamp(passages, HARD_MAX); }
        public void setPassages(int passages) { this.passages = passages; }
        public int getTrajectories() { return clamp(trajectories, HARD_MAX); }
        public void setTrajectories(int trajectories) { this.trajectories = trajectories; }
        public int getPackages() { return clamp(packages, HARD_MAX); }
        public void setPackages(int packages) { this.packages = packages; }
        public int getHardCases() { return clamp(hardCases, HARD_MAX); }
        public void setHardCases(int hardCases) { this.hardCases = hardCases; }
        public int getPlugins() { return clamp(plugins, HARD_MAX); }
        public void setPlugins(int plugins) { this.plugins = plugins; }
        public int getDoors() { return clamp(doors, HARD_MAX); }
        public void setDoors(int doors) { this.doors = doors; }
        public int getAudits() { return clamp(audits, HARD_MAX); }
        public void setAudits(int audits) { this.audits = audits; }
        public int getCompanions() { return clamp(companions, HARD_MAX); }
        public void setCompanions(int companions) { this.companions = companions; }
        public int getCompares() { return clamp(compares, HARD_MAX); }
        public void setCompares(int compares) { this.compares = compares; }
        public int getChildItems() { return clamp(childItems, CHILD_HARD_MAX); }
        public void setChildItems(int childItems) { this.childItems = childItems; }

        private static int clamp(int value, int hardMax) {
            if (value < 1) {
                return 1;
            }
            return Math.min(value, hardMax);
        }
    }

    public static class Media {
        private static final long HARD_MAX_BYTES = 32L * 1024L * 1024L;
        private static final int HARD_MAX_GRACE_SECONDS = 24 * 60 * 60;
        private long maxUploadBytes = HARD_MAX_BYTES;
        private int graceSeconds = 900;
        private long sweepMs = 60000L;

        public long getMaxUploadBytes() {
            if (maxUploadBytes < 1) {
                return 1;
            }
            return Math.min(maxUploadBytes, HARD_MAX_BYTES);
        }

        public void setMaxUploadBytes(long maxUploadBytes) {
            this.maxUploadBytes = maxUploadBytes;
        }

        public int getGraceSeconds() {
            if (graceSeconds < 0) {
                return 0;
            }
            return Math.min(graceSeconds, HARD_MAX_GRACE_SECONDS);
        }

        public void setGraceSeconds(int graceSeconds) {
            this.graceSeconds = graceSeconds;
        }

        public long getSweepMs() {
            return sweepMs < 1000L ? 1000L : sweepMs;
        }

        public void setSweepMs(long sweepMs) {
            this.sweepMs = sweepMs;
        }
    }
}
