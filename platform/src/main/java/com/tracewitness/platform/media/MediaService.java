package com.tracewitness.platform.media;

import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.MediaStore;
import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.time.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaService.class);

    private final MediaStore mediaStore;
    private final PlatformStore platformStore;
    private final ScenarioService scenarios;
    private final AppProperties properties;
    private final TimeSource timeSource;
    private final TransactionTemplate transactions;

    public MediaService(MediaStore mediaStore, PlatformStore platformStore, ScenarioService scenarios,
                        AppProperties properties, TimeSource timeSource, TransactionTemplate transactions) {
        this.mediaStore = mediaStore;
        this.platformStore = platformStore;
        this.scenarios = scenarios;
        this.properties = properties;
        this.timeSource = timeSource;
        this.transactions = transactions;
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

    public Map<String, Object> storeUpload(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String type = MediaTypes.validateDeclared(filename, file.getContentType());
        String runId = scenarios.runId();
        String id = platformStore.nextId(runId, "media");
        String relative = runId + "/uploads/" + id + "/file." + MediaTypes.extension(filename);
        String createdAt = timeSource.formatNow();
        transactions.executeWithoutResult(status -> mediaStore.insertUploading(id, runId, type, relative, createdAt));
        Path staging = resolve(".staging/" + id + ".part");
        try {
            Files.createDirectories(staging.getParent());
            Staged staged = writeStaging(file, staging);
            MediaTypes.assertPayload(type, staged.header);
            transactions.executeWithoutResult(status -> mediaStore.updateState(id, "STAGED", staged.sha256, Long.valueOf(staged.size)));
            Path target = resolve(relative);
            if (Files.exists(target)) {
                throw new IOException("upload target already exists");
            }
            Files.createDirectories(target.getParent());
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            transactions.executeWithoutResult(status -> mediaStore.updateState(id, "READY", staged.sha256, Long.valueOf(staged.size)));
        } catch (RuntimeException | IOException exception) {
            boolean moved = Files.isRegularFile(resolve(relative));
            if (!moved) {
                Files.deleteIfExists(staging);
                try {
                    transactions.executeWithoutResult(status -> mediaStore.updateState(id, "FAILED", null, null));
                } catch (RuntimeException ignored) {
                    LOG.warn("media upload id={} code=state_update_failed", id);
                }
            }
            throw exception;
        }
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
            if (!readable(asset)) {
                continue;
            }
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
        Map<String, Object> row = mediaStore.findAsset(id);
        if (!currentRun(row) || !"READY".equals(String.valueOf(row.get("status")))) {
            return null;
        }
        return readable(row) ? row : null;
    }

    public byte[] readStored(Map<String, Object> asset) throws IOException {
        return Files.readAllBytes(resolve(String.valueOf(asset.get("storagePath"))));
    }

    public byte[] readFrame(String segmentId, int index) throws IOException {
        Map<String, Object> segment = mediaStore.findSegment(segmentId);
        if (segment == null || !currentRun(segment)
                || index < 0 || index >= ((Number) segment.get("frameCount")).intValue()) {
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
            if (!readable(asset)) {
                continue;
            }
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
            if (!readable(asset)) {
                continue;
            }
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

    public void recover() {
        Instant now = Instant.now();
        int grace = properties.getMedia().getGraceSeconds();
        for (Map<String, Object> row : mediaStore.listRecoverable()) {
            String id = String.valueOf(row.get("id"));
            String status = String.valueOf(row.get("status"));
            try {
                Path target = resolve(String.valueOf(row.get("storagePath")));
                if ("READY".equals(status)) {
                    if (!Files.isRegularFile(target)) {
                        transactions.executeWithoutResult(tx -> mediaStore.updateState(id, "DAMAGED", null, null));
                        LOG.info("media recover id={} from={} result=damaged code=ready_missing", id, status);
                    }
                    continue;
                }
                if (!expired(String.valueOf(row.get("createdAt")), grace, now)) {
                    continue;
                }
                Path staging = resolve(".staging/" + id + ".part");
                if ("STAGED".equals(status) && Files.isRegularFile(target) && sameHash(target, row.get("sha256"))) {
                    long size = Files.size(target);
                    transactions.executeWithoutResult(tx -> mediaStore.updateState(id, "READY", String.valueOf(row.get("sha256")), Long.valueOf(size)));
                    Files.deleteIfExists(staging);
                    LOG.info("media recover id={} from={} result=ready code=staged_promoted", id, status);
                    continue;
                }
                Files.deleteIfExists(staging);
                if (Files.isRegularFile(target)) {
                    quarantine(target);
                }
                transactions.executeWithoutResult(tx -> mediaStore.updateState(id, "FAILED", null, null));
                LOG.info("media recover id={} from={} result=failed code=expired_{}", id, status, status.toLowerCase(java.util.Locale.ROOT));
            } catch (IOException exception) {
                LOG.warn("media recover id={} result=skipped code=io", id);
            }
        }
        sweepOrphans();
    }

    private boolean readable(Map<String, Object> asset) {
        try {
            if (Files.isRegularFile(resolve(String.valueOf(asset.get("storagePath"))))) {
                return true;
            }
            transactions.executeWithoutResult(status -> mediaStore.updateState(String.valueOf(asset.get("id")), "DAMAGED", null, null));
            LOG.info("media read id={} result=damaged code=ready_missing", asset.get("id"));
        } catch (IOException exception) {
            return false;
        }
        return false;
    }

    private Staged writeStaging(MultipartFile file, Path staging) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        byte[] buffer = new byte[8192];
        byte[] header = new byte[64];
        int headerLength = 0;
        long size = 0;
        long max = properties.getMedia().getMaxUploadBytes();
        try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(staging)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                size += read;
                if (size > max) {
                    throw new IllegalArgumentException("upload too large");
                }
                if (headerLength < header.length) {
                    int copy = (int) Math.min(read, header.length - headerLength);
                    System.arraycopy(buffer, 0, header, headerLength, copy);
                    headerLength += copy;
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (size == 0) {
            throw new IllegalArgumentException("empty upload");
        }
        Staged staged = new Staged();
        staged.header = Arrays.copyOf(header, headerLength);
        staged.size = size;
        staged.sha256 = hex(digest.digest());
        return staged;
    }

    private void sweepOrphans() {
        try {
            Path root = mediaRoot();
            Instant cutoff = Instant.now().minusSeconds(properties.getMedia().getGraceSeconds());
            java.util.Set<String> kept = new java.util.HashSet<String>();
            for (Map<String, Object> row : mediaStore.listRecoverable()) {
                String status = String.valueOf(row.get("status"));
                if ("READY".equals(status) || "STAGED".equals(status)) {
                    kept.add(String.valueOf(row.get("storagePath")).replace('\\', '/'));
                }
            }
            Path staging = root.resolve(".staging");
            if (Files.isDirectory(staging)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(staging)) {
                    for (Path file : files) {
                        if (Files.isRegularFile(file) && olderThan(file, cutoff)) {
                            quarantine(file);
                            LOG.info("media recover id={} result=removed code=orphan_staging", file.getFileName());
                        }
                    }
                }
            }
            try (DirectoryStream<Path> runs = Files.newDirectoryStream(root)) {
                for (Path run : runs) {
                    String name = run.getFileName().toString();
                    if (name.startsWith(".") || !Files.isDirectory(run)) {
                        continue;
                    }
                    Path uploads = run.resolve("uploads");
                    if (!Files.isDirectory(uploads)) {
                        continue;
                    }
                    java.util.List<Path> files = new ArrayList<Path>();
                    try (java.util.stream.Stream<Path> walk = Files.walk(uploads, 3)) {
                        walk.forEach(files::add);
                    }
                    for (Path file : files) {
                        if (!Files.isRegularFile(file) || !olderThan(file, cutoff)) {
                            continue;
                        }
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        if (kept.contains(relative)) {
                            continue;
                        }
                        quarantine(file);
                        LOG.info("media recover id={} result=removed code=orphan_upload", file.getFileName());
                    }
                }
            }
        } catch (IOException exception) {
            LOG.warn("media recover result=skipped code=orphan_io");
        }
    }

    private void quarantine(Path file) throws IOException {
        Path root = mediaRoot();
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("media path escapes root");
        }
        Path quarantine = root.resolve(".quarantine");
        Files.createDirectories(quarantine);
        Path dest = quarantine.resolve(normalized.getFileName().toString() + "-" + System.nanoTime());
        Files.move(normalized, dest, StandardCopyOption.ATOMIC_MOVE);
        Files.deleteIfExists(dest);
    }

    private static boolean olderThan(Path file, Instant cutoff) throws IOException {
        FileTime time = Files.getLastModifiedTime(file);
        return !time.toInstant().isAfter(cutoff);
    }

    private static boolean expired(String createdAt, int graceSeconds, Instant now) {
        try {
            return !Instant.parse(createdAt).plusSeconds(graceSeconds).isAfter(now);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static boolean sameHash(Path file, Object expected) throws IOException {
        if (expected == null) {
            return false;
        }
        return hex(sha256(file)).equals(String.valueOf(expected));
    }

    private static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    private static final class Staged {
        private byte[] header;
        private long size;
        private String sha256;
    }

    private boolean currentRun(Map<String, Object> row) {
        return row != null && scenarios.runId().equals(row.get("runId"));
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

}
