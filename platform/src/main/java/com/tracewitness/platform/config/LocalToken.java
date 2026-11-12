package com.tracewitness.platform.config;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class LocalToken {
    private final String value;

    public LocalToken(AppProperties properties) {
        String configured = properties.getApiToken();
        if (configured == null || configured.trim().isEmpty()) {
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hex.append(String.format("%02x", item));
            }
            this.value = hex.toString();
        } else {
            this.value = configured.trim();
        }
    }

    public String value() {
        return value;
    }

    public boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return constantEquals(value, presented.trim());
    }

    private static boolean constantEquals(String left, String right) {
        byte[] a = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
