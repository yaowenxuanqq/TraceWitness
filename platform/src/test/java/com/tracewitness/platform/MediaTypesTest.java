package com.tracewitness.platform;

import com.tracewitness.platform.media.MediaTypes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaTypesTest {
    @Test
    void acceptsWaveAudio() {
        assertEquals("audio/wav", MediaTypes.validate("sample.wav", "audio/wav", wave()));
    }

    @Test
    void rejectsHtml() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTypes.validate("evil.html", "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsHtmlDisguisedAsVideo() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTypes.validate("evil.mp4", "video/mp4", "<html></html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsPayloadThatDoesNotMatchDeclaredType() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTypes.validate("sample.mp4", "video/mp4", wave()));
    }

    @Test
    void rejectsExtensionThatDoesNotMatchDeclaredType() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaTypes.validate("sample.mp4", "audio/wav", wave()));
    }

    @Test
    void acceptsMp4Container() {
        byte[] bytes = new byte[32];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        assertEquals("video/mp4", MediaTypes.validate("clip.mp4", "video/mp4", bytes));
    }

    private static byte[] wave() {
        byte[] bytes = new byte[44];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }
}
