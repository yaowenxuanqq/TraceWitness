package com.tracewitness.platform.media;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Software AOR timeline. Idle ticks stay at a low frame rate. An event tick
 * switches to the high frame rate on the tick boundary, which is the smooth
 * encoding change. Audio is one PCM stream: each tick appends the next
 * samples, and a clip copies a slice of that stream. The ring buffer must
 * already hold the pre-roll when the event tick is recorded.
 */
public final class AorTimeline {
    public static final int LOW_FPS = 1;
    public static final int HIGH_FPS = 4;
    public static final int SAMPLE_RATE = 8000;
    public static final int PRE_TICKS = 1;
    public static final int POST_TICKS = 1;
    public static final int RING_TICKS = 2;

    private AorTimeline() {
    }

    public static Plan record(List<Long> eventTicks, long tickMs) {
        if (eventTicks == null || eventTicks.isEmpty()) {
            throw new IllegalArgumentException("no events");
        }
        if (tickMs <= 0 || (SAMPLE_RATE * tickMs) % 1000L != 0) {
            throw new IllegalArgumentException("tick does not divide the audio sample rate");
        }
        int samplesPerTick = (int) (SAMPLE_RATE * tickMs / 1000L);
        long origin = eventTicks.get(0);
        long last = eventTicks.get(0);
        for (Long tick : eventTicks) {
            origin = Math.min(origin, tick.longValue());
            last = Math.max(last, tick.longValue());
        }
        origin -= PRE_TICKS;
        last += POST_TICKS;
        if (last < origin || (last - origin) > 100000) {
            throw new IllegalArgumentException("timeline length out of range");
        }

        Set<Long> events = new HashSet<Long>(eventTicks);
        int tickCount = (int) (last - origin + 1);
        short[] samples = new short[samplesPerTick * tickCount];
        List<Segment> segments = new ArrayList<Segment>();
        ArrayDeque<Segment> ring = new ArrayDeque<Segment>();
        List<long[]> pending = new ArrayList<long[]>();
        List<ClipSpan> clips = new ArrayList<ClipSpan>();
        int cursor = 0;

        for (long tick = origin; tick <= last; tick++) {
            boolean high = events.contains(tick);
            int fps = high ? HIGH_FPS : LOW_FPS;
            int period = high ? 20 : 40;
            for (int i = 0; i < samplesPerTick; i++) {
                samples[cursor + i] = (short) ((i % period) < (period / 2) ? 8000 : -8000);
            }
            Segment segment = new Segment(tick, high ? "HIGH" : "LOW", fps, fps, cursor, samplesPerTick);
            segments.add(segment);
            ring.addLast(segment);
            while (ring.size() > RING_TICKS) {
                ring.removeFirst();
            }
            if (high) {
                long start = tick - PRE_TICKS;
                if (!ringContains(ring, start)) {
                    throw new IllegalStateException("ring buffer missing pre-roll at tick " + start);
                }
                pending.add(new long[]{tick, start});
            }
            cursor += samplesPerTick;
            pending = closeReadyClips(pending, tick, segments, clips);
        }
        if (!pending.isEmpty()) {
            throw new IllegalStateException("clip post-roll was not recorded");
        }
        return new Plan(segments, samples, clips, samplesPerTick);
    }

    private static boolean ringContains(ArrayDeque<Segment> ring, long tick) {
        for (Segment segment : ring) {
            if (segment.tick == tick) {
                return true;
            }
        }
        return false;
    }

    private static List<long[]> closeReadyClips(List<long[]> pending, long tick, List<Segment> segments, List<ClipSpan> clips) {
        List<long[]> still = new ArrayList<long[]>();
        for (long[] item : pending) {
            if (tick != item[0] + POST_TICKS) {
                still.add(item);
                continue;
            }
            Segment start = segmentAt(segments, item[1]);
            Segment end = segmentAt(segments, tick);
            int sampleCount = (int) (end.audioStartSample + end.audioSampleCount - start.audioStartSample);
            clips.add(new ClipSpan(item[0], start.tick, end.tick, (int) start.audioStartSample, sampleCount));
        }
        return still;
    }

    private static Segment segmentAt(List<Segment> segments, long tick) {
        for (Segment segment : segments) {
            if (segment.tick == tick) {
                return segment;
            }
        }
        throw new IllegalStateException("missing segment " + tick);
    }

    public static final class Segment {
        public final long tick;
        public final String profile;
        public final int fps;
        public final int frameCount;
        public final long audioStartSample;
        public final int audioSampleCount;

        public Segment(long tick, String profile, int fps, int frameCount, long audioStartSample, int audioSampleCount) {
            this.tick = tick;
            this.profile = profile;
            this.fps = fps;
            this.frameCount = frameCount;
            this.audioStartSample = audioStartSample;
            this.audioSampleCount = audioSampleCount;
        }
    }

    public static final class ClipSpan {
        public final long eventTick;
        public final long startTick;
        public final long endTick;
        public final int audioStartSample;
        public final int audioSampleCount;

        public ClipSpan(long eventTick, long startTick, long endTick, int audioStartSample, int audioSampleCount) {
            this.eventTick = eventTick;
            this.startTick = startTick;
            this.endTick = endTick;
            this.audioStartSample = audioStartSample;
            this.audioSampleCount = audioSampleCount;
        }
    }

    public static final class Plan {
        public final List<Segment> segments;
        public final short[] samples;
        public final List<ClipSpan> clips;
        public final int samplesPerTick;

        public Plan(List<Segment> segments, short[] samples, List<ClipSpan> clips, int samplesPerTick) {
            this.segments = segments;
            this.samples = samples;
            this.clips = clips;
            this.samplesPerTick = samplesPerTick;
        }

        public boolean audioContinuous() {
            long cursor = 0;
            for (Segment segment : segments) {
                if (segment.audioStartSample != cursor || segment.audioSampleCount != samplesPerTick) {
                    return false;
                }
                if ("LOW".equals(segment.profile) && segment.fps != LOW_FPS) {
                    return false;
                }
                if ("HIGH".equals(segment.profile) && segment.fps != HIGH_FPS) {
                    return false;
                }
                cursor += segment.audioSampleCount;
            }
            if (cursor != samples.length || clips.isEmpty()) {
                return false;
            }
            for (ClipSpan clip : clips) {
                long span = clip.endTick - clip.startTick + 1;
                if (clip.startTick != clip.eventTick - PRE_TICKS || clip.endTick != clip.eventTick + POST_TICKS) {
                    return false;
                }
                if (clip.audioSampleCount != span * samplesPerTick) {
                    return false;
                }
            }
            return true;
        }

        public short[] slice(ClipSpan clip) {
            short[] out = new short[clip.audioSampleCount];
            System.arraycopy(samples, clip.audioStartSample, out, 0, clip.audioSampleCount);
            return out;
        }
    }
}
