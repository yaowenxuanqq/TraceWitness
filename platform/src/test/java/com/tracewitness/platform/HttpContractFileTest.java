package com.tracewitness.platform;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpContractFileTest {
    @Test
    void writePathsAreListed() throws Exception {
        Path file = Paths.get("..", "contracts", "http-api.yaml");
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(text.contains("/api/profiles/tickets/{id}/decision"));
        assertTrue(text.contains("/api/alerts/{id}/decision"));
        assertTrue(text.contains("/api/evidence/{id}/verify"));
        assertTrue(text.contains("/api/hard-cases/{id}/label"));
        assertTrue(text.contains("/api/plugins/{id}/fault"));
        assertTrue(text.contains("/api/plugins/rollback"));
        assertTrue(text.contains("additionalProperties: false"));
    }
}
