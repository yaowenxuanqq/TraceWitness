package com.tracewitness.platform;

import com.tracewitness.platform.media.AorTimeline;
import com.tracewitness.platform.media.FrameImages;
import com.tracewitness.platform.media.WavFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AorTimelineTest {
    @Test
    void eventClipKeepsPreRollAndContinuousAudio() throws Exception {
        AorTimeline.Plan plan = AorTimeline.record(Arrays.asList(1L, 2L, 3L), 1000);
        assertTrue(plan.audioContinuous());
        assertEquals(3, plan.clips.size());

        AorTimeline.ClipSpan first = plan.clips.get(0);
        assertEquals(1L, first.eventTick);
        assertEquals(0L, first.startTick);
        assertEquals(2L, first.endTick);
        assertEquals("LOW", plan.segments.get(0).profile);
        assertEquals(AorTimeline.LOW_FPS, plan.segments.get(0).fps);
        assertEquals("HIGH", plan.segments.get(1).profile);
        assertEquals(AorTimeline.HIGH_FPS, plan.segments.get(1).fps);

        long cursor = 0;
        String previous = null;
        for (AorTimeline.Segment segment : plan.segments) {
            assertEquals(cursor, segment.audioStartSample);
            cursor += segment.audioSampleCount;
            if (previous != null && !previous.equals(segment.profile)) {
                assertEquals(previous.equals("LOW") ? "HIGH" : "LOW", segment.profile);
            }
            previous = segment.profile;
        }

        short[] slice = plan.slice(first);
        assertEquals(first.audioSampleCount, slice.length);
        for (int i = 0; i < slice.length; i++) {
            assertEquals(plan.samples[first.audioStartSample + i], slice[i]);
            assertTrue(slice[i] == 8000 || slice[i] == -8000);
        }
        assertTrue(WavFiles.playable(WavFiles.encode(AorTimeline.SAMPLE_RATE, slice), AorTimeline.SAMPLE_RATE, slice.length));

        Path frame = Files.createTempFile("tw-frame", ".png");
        FrameImages.write(0, "LOW", 0, frame);
        byte[] png = Files.readAllBytes(frame);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }
}
