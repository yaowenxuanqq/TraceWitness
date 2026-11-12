package com.tracewitness.platform.media;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Upload media type policy. Only a small set of audio and video containers is
 * accepted. The declared MIME type, the filename extension and the leading
 * bytes of the payload must agree.
 */
public final class MediaTypes {
    private static final Set<String> AUDIO_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg", "audio/mp3", "audio/ogg",
            "audio/mp4", "audio/aac", "audio/flac", "audio/x-flac", "audio/webm")));
    private static final Set<String> VIDEO_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo", "video/x-matroska",
            "video/mpeg", "video/x-flv", "video/x-m4v")));
    private static final Set<String> AUDIO_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "wav", "wave", "mp3", "mp2", "ogg", "oga", "m4a", "aac", "flac", "webm")));
    private static final Set<String> VIDEO_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "mp4", "m4v", "mov", "webm", "avi", "mkv", "mpg", "mpeg", "flv")));

    private MediaTypes() {
    }

    public static String validate(String filename, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("empty upload");
        }
        String type = normalize(contentType);
        boolean audio = AUDIO_TYPES.contains(type);
        boolean video = VIDEO_TYPES.contains(type);
        if (!audio && !video) {
            throw new IllegalArgumentException("unsupported media type");
        }
        String extension = extension(filename);
        if (extension.isEmpty()) {
            throw new IllegalArgumentException("media filename needs an extension");
        }
        if (audio && !AUDIO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("extension does not match audio type");
        }
        if (video && !VIDEO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("extension does not match video type");
        }
        String family = sniff(bytes);
        if (family == null) {
            throw new IllegalArgumentException("unsupported media format");
        }
        if (!"either".equals(family)
                && ((audio && !"audio".equals(family)) || (video && !"video".equals(family)))) {
            throw new IllegalArgumentException("payload does not match declared media type");
        }
        return type;
    }

    public static String normalize(String contentType) {
        String raw = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return raw.isEmpty() ? "application/octet-stream" : raw;
    }

    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String base = slash >= 0 ? filename.substring(slash + 1) : filename;
        int dot = base.lastIndexOf('.');
        if (dot < 0 || dot == base.length() - 1) {
            return "";
        }
        String raw = base.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!raw.matches("[a-z0-9]{1,8}")) {
            return "";
        }
        return raw;
    }

    private static String sniff(byte[] bytes) {
        if (starts(bytes, 0, "RIFF") && starts(bytes, 8, "WAVE")) {
            return "audio";
        }
        if (starts(bytes, 0, "RF64") && starts(bytes, 8, "WAVE")) {
            return "audio";
        }
        if (starts(bytes, 0, "RIFF") && starts(bytes, 8, "AVI ")) {
            return "video";
        }
        if (starts(bytes, 4, "ftyp")) {
            return "either";
        }
        if (starts(bytes, 0, "OggS") || starts(bytes, 0, "fLaC")) {
            return "audio";
        }
        if (starts(bytes, 0, "ID3") || isMp3Frame(bytes) || isAacFrame(bytes)) {
            return "audio";
        }
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0x1a && (bytes[1] & 0xff) == 0x45
                && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3) {
            return "either";
        }
        if (starts(bytes, 0, "FLV")) {
            return "video";
        }
        if (bytes.length >= 4 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 1
                && ((bytes[3] & 0xff) == 0xba || (bytes[3] & 0xff) == 0xb3)) {
            return "video";
        }
        return null;
    }

    private static boolean isMp3Frame(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0;
    }

    private static boolean isAacFrame(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xf6) == 0xf0;
    }

    private static boolean starts(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ((bytes[offset + i] & 0xff) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
