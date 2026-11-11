package com.tracewitness.platform.media;

import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.MediaStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaService {
    private final MediaStore mediaStore;
    private final PlatformStore platformStore;
    private final ScenarioService scenarios;
    private final AppProperties properties;
    private final TimeSource timeSource;

    public MediaService(MediaStore mediaStore, PlatformStore platformStore, ScenarioService scenarios,
                        AppProperties properties, TimeSource timeSource) {
        this.mediaStore = mediaStore;
        this.platformStore = platformStore;
        this.scenarios = scenarios;
        this.properties = properties;
        this.timeSource = timeSource;
    }

    @Transactional
    public void ensure() {
        String runId = scenarios.runId();
        if (mediaStore.countKind(runId, "CLIP") > 0) {
            return;
        }
        List<Map<String, Object>> events = platformStore.listEvents(runId, 100);
        Map<String, List<Long>> ticksByDevice = new LinkedHashMap<String, List<Long>>();
        Map<String, Map<Long, String>> eventsByDevice = new LinkedHashMap<String, Map<Long, String>>();
        for (Map<String, Object> event : events) {
            String deviceId = String.valueOf(event.get("deviceId"));
            List<Long> ticks = ticksByDevice.get(deviceId);
            if (ticks == null) {
                ticks = new ArrayList<Long>();
                ticksByDevice.put(deviceId, ticks);
                eventsByDevice.put(deviceId, new LinkedHashMap<Long, String>());
            }
            long tick = ((Number) event.get("tick")).longValue();
            ticks.add(tick);
            eventsByDevice.get(deviceId).put(tick, String.valueOf(event.get("id")));
        }
        for (Map.Entry<String, List<Long>> entry : ticksByDevice.entrySet()) {
            recordDevice(runId, entry.getKey(), entry.getValue(), eventsByDevice.get(entry.getKey()));
        }
    }

    @Transactional
    public Map<String, Object> storeUpload(String filename, String contentType, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("empty upload");
        }
        String runId = scenarios.runId();
        String id = platformStore.nextId(runId, "media");
        String type = contentType == null || contentType.trim().isEmpty() ? "application/octet-stream" : contentType.split(";")[0].trim();
        String relative = runId + "/uploads/" + id + "/file" + extension(filename);
        Path file = resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        mediaStore.insertAsset(id, runId, null, null, "UPLOAD", "FILE", type, relative,
                null, null, null, null, null, timeSource.formatNow());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", id);
        body.put("contentType", type);
        body.put("url", "/api/media/assets/" + id + "/file");
        return body;
    }

    public Map<String, Object> timeline() {
        String runId = scenarios.runId();
        List<Map<String, Object>> segments = mediaStore.listSegments(runId);
        boolean continuous = !segments.isEmpty();
        long cursor = 0;
        for (Map<String, Object> segment : segments) {
            long start = ((Number) segment.get("audioStartSample")).longValue();
            int count = ((Number) segment.get("audioSampleCount")).intValue();
            if (start != cursor) {
                continuous = false;
            }
            cursor += count;
        }
        int samplesPerTick = (int) (AorTimeline.SAMPLE_RATE * properties.getEval().getTickMs() / 1000L);
        List<Map<String, Object>> segmentViews = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> segment : segments) {
            segmentViews.add(segmentView(segment));
        }
        List<Map<String, Object>> clips = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> asset : mediaStore.listAssets(runId, "CLIP")) {
            clips.add(clipView(asset, segmentViews, samplesPerTick, continuous));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("lowFps", AorTimeline.LOW_FPS);
        body.put("highFps", AorTimeline.HIGH_FPS);
        body.put("sampleRate", AorTimeline.SAMPLE_RATE);
        body.put("tickMs", properties.getEval().getTickMs());
        body.put("audioContinuous", continuous);
        body.put("channels", channelViews(runId));
        body.put("segments", segmentViews);
        body.put("clips", clips);
        body.put("uploads", uploadViews(runId));
        return body;
    }

    public Map<String, Object> findAsset(String id) {
        return mediaStore.findAsset(id);
    }

    public byte[] readStored(Map<String, Object> asset) throws IOException {
        return Files.readAllBytes(resolve(String.valueOf(asset.get("storagePath"))));
    }

    public byte[] readFrame(String segmentId, int index) throws IOException {
        Map<String, Object> segment = mediaStore.findSegment(segmentId);
        if (segment == null || index < 0 || index >= ((Number) segment.get("frameCount")).intValue()) {
            return null;
        }
        Path file = resolve(String.valueOf(segment.get("frameDir"))).resolve(index + ".png");
        if (!file.startsWith(mediaRoot())) {
            throw new IllegalArgumentException("media path escapes root");
        }
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    private void recordDevice(String runId, String deviceId, List<Long> ticks, Map<Long, String> eventIds) {
        AorTimeline.Plan plan = AorTimeline.record(ticks, properties.getEval().getTickMs());
        if (!plan.audioContinuous()) {
            throw new IllegalStateException("audio timeline has a gap");
        }
        String createdAt = timeSource.formatNow();
        String channelId = platformStore.nextId(runId, "media");
        String channelRelative = runId + "/" + deviceId + "/channel.wav";
        writeWav(channelRelative, plan.samples);
        long origin = plan.segments.get(0).tick;
        long end = plan.segments.get(plan.segments.size() - 1).tick;
        mediaStore.insertAsset(channelId, runId, deviceId, null, "CHANNEL", "SYNTHETIC", "audio/wav",
                channelRelative, origin, end, AorTimeline.SAMPLE_RATE, (long) plan.samples.length, Boolean.TRUE, createdAt);

        for (AorTimeline.Segment segment : plan.segments) {
            String segmentId = platformStore.nextId(runId, "media");
            String frameDir = runId + "/" + deviceId + "/frames/" + segment.tick;
            try {
                Path dir = resolve(frameDir);
                Files.createDirectories(dir);
                for (int index = 0; index < segment.frameCount; index++) {
                    FrameImages.write(segment.tick, segment.profile, index, dir.resolve(index + ".png"));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("frame write failed", exception);
            }
            mediaStore.insertSegment(segmentId, runId, deviceId, segment.tick, segment.profile, segment.fps,
                    segment.frameCount, segment.audioStartSample, segment.audioSampleCount, frameDir);
        }

        for (AorTimeline.ClipSpan clip : plan.clips) {
            String eventId = eventIds.get(clip.eventTick);
            if (eventId == null) {
                throw new IllegalStateException("clip without event at tick " + clip.eventTick);
            }
            String clipId = platformStore.nextId(runId, "media");
            String relative = runId + "/clips/" + clipId + ".wav";
            writeWav(relative, plan.slice(clip));
            mediaStore.insertAsset(clipId, runId, deviceId, eventId, "CLIP", "SYNTHETIC", "audio/wav",
                    relative, clip.startTick, clip.endTick, AorTimeline.SAMPLE_RATE, (long) clip.audioSampleCount,
                    Boolean.TRUE, createdAt);
        }
    }

    private void writeWav(String relative, short[] samples) {
        try {
            Path file = resolve(relative);
            Files.createDirectories(file.getParent());
            Files.write(file, WavFiles.encode(AorTimeline.SAMPLE_RATE, samples));
        } catch (IOException exception) {
            throw new IllegalStateException("wav write failed", exception);
        }
    }

    private List<Map<String, Object>> channelViews(String runId) {
        List<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> asset : mediaStore.listAssets(runId, "CHANNEL")) {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("id", asset.get("id"));
            view.put("deviceId", asset.get("deviceId"));
            view.put("audioUrl", "/api/media/assets/" + asset.get("id") + "/file");
            view.put("sampleRate", asset.get("sampleRate"));
            view.put("sampleCount", asset.get("sampleCount"));
            view.put("startTick", asset.get("startTick"));
            view.put("endTick", asset.get("endTick"));
            views.add(view);
        }
        return views;
    }

    private List<Map<String, Object>> uploadViews(String runId) {
        List<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> asset : mediaStore.listUploads(runId)) {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("id", asset.get("id"));
            view.put("contentType", asset.get("contentType"));
            view.put("url", "/api/media/assets/" + asset.get("id") + "/file");
            views.add(view);
        }
        return views;
    }

    private Map<String, Object> segmentView(Map<String, Object> segment) {
        int frameCount = ((Number) segment.get("frameCount")).intValue();
        List<String> frames = new ArrayList<String>();
        for (int index = 0; index < frameCount; index++) {
            frames.add("/api/media/segments/" + segment.get("id") + "/frames/" + index);
        }
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", segment.get("id"));
        view.put("deviceId", segment.get("deviceId"));
        view.put("tick", segment.get("tick"));
        view.put("profile", segment.get("profile"));
        view.put("fps", segment.get("fps"));
        view.put("frameCount", frameCount);
        view.put("audioStartSample", segment.get("audioStartSample"));
        view.put("audioSampleCount", segment.get("audioSampleCount"));
        view.put("frames", frames);
        return view;
    }

    private Map<String, Object> clipView(Map<String, Object> asset, List<Map<String, Object>> segments,
                                          int samplesPerTick, boolean timelineContinuous) {
        long start = ((Number) asset.get("startTick")).longValue();
        long end = ((Number) asset.get("endTick")).longValue();
        long sampleCount = ((Number) asset.get("sampleCount")).longValue();
        boolean spanMatches = sampleCount == (end - start + 1) * samplesPerTick;
        List<Map<String, Object>> frames = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> segment : segments) {
            long tick = ((Number) segment.get("tick")).longValue();
            if (tick < start || tick > end || !asset.get("deviceId").equals(segment.get("deviceId"))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) segment.get("frames");
            int index = 0;
            for (String url : urls) {
                Map<String, Object> frame = new LinkedHashMap<String, Object>();
                frame.put("url", url);
                frame.put("tick", tick);
                frame.put("index", index);
                frame.put("fps", segment.get("fps"));
                frame.put("profile", segment.get("profile"));
                frames.add(frame);
                index++;
            }
        }
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", asset.get("id"));
        view.put("eventId", asset.get("eventId"));
        view.put("deviceId", asset.get("deviceId"));
        view.put("startTick", start);
        view.put("endTick", end);
        view.put("audioUrl", "/api/media/assets/" + asset.get("id") + "/file");
        view.put("sampleCount", sampleCount);
        view.put("audioContinuous", timelineContinuous && spanMatches);
        view.put("frames", frames);
        return view;
    }

    public String existingClipId(String runId, String eventId) {
        for (Map<String, Object> asset : mediaStore.listAssets(runId, "CLIP")) {
            if (!eventId.equals(String.valueOf(asset.get("eventId")))) {
                continue;
            }
            try {
                if (Files.exists(resolve(String.valueOf(asset.get("storagePath"))))) {
                    return String.valueOf(asset.get("id"));
                }
            } catch (IOException exception) {
                return null;
            }
        }
        return null;
    }

    private Path resolve(String relative) throws IOException {
        Path root = mediaRoot();
        Path path = root.resolve(relative).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IOException("media path escapes root");
        }
        return path;
    }

    private Path mediaRoot() throws IOException {
        Path root = Paths.get(properties.getMediaDir());
        if (!root.isAbsolute()) {
            root = Paths.get(System.getProperty("user.dir")).resolve(root);
        }
        root = root.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String base = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        String raw = base.substring(dot + 1).toLowerCase();
        if (!raw.matches("[a-z0-9]{1,8}")) {
            return "";
        }
        return "." + raw;
    }
}
