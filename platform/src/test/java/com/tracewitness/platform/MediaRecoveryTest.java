package com.tracewitness.platform;

import com.tracewitness.platform.config.AppProperties;
import com.tracewitness.platform.media.MediaService;
import com.tracewitness.platform.scenario.ScenarioService;
import com.tracewitness.platform.store.MediaStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaRecoveryTest {
    @Test
    void uploadsStayHiddenUntilReadyAndRecoveryIsIdempotent() throws Exception {
        Path dir = Files.createTempDirectory("tw-media");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        String media = dir.resolve("media").toAbsolutePath().toString().replace('\\', '/');
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(
                "--spring.datasource.url=jdbc:h2:file:" + database,
                "--spring.datasource.username=sa",
                "--tracewitness.mode=MOCK",
                "--tracewitness.auto-loop=false",
                "--tracewitness.media.grace-seconds=0",
                "--tracewitness.media.sweep-ms=3600000",
                "--tracewitness.media-dir=" + media,
                "--server.port=0")) {
            MediaService service = context.getBean(MediaService.class);
            MediaStore store = context.getBean(MediaStore.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String runId = context.getBean(ScenarioService.class).runId();
            byte[] wav = wav();

            Map<String, Object> uploaded = service.storeUpload(file("sample.wav", "audio/wav", wav));
            assertEquals("READY", status(jdbc, uploaded.get("id")));
            assertNotNull(service.findAsset(String.valueOf(uploaded.get("id"))));
            assertEquals(1, service.timeline().get("uploads") instanceof java.util.List
                    ? ((java.util.List<?>) service.timeline().get("uploads")).size() : 0);

            assertThrows(IllegalArgumentException.class,
                    () -> service.storeUpload(file("evil.html", "text/html", "<html></html>".getBytes(StandardCharsets.UTF_8))));
            assertEquals(1, countStatus(jdbc, "READY", "UPLOAD"));

            assertThrows(IllegalArgumentException.class,
                    () -> service.storeUpload(file("fake.wav", "audio/wav", "<html></html>".getBytes(StandardCharsets.UTF_8))));
            assertTrue(countStatus(jdbc, "FAILED", "UPLOAD") >= 1);
            assertEquals(1, countStatus(jdbc, "READY", "UPLOAD"));

            context.getBean(AppProperties.class).getMedia().setMaxUploadBytes(8);
            assertThrows(IllegalArgumentException.class, () -> service.storeUpload(file("big.wav", "audio/wav", wav)));
            assertEquals(1, countStatus(jdbc, "READY", "UPLOAD"));

            MockMultipartFile broken = new MockMultipartFile("file", "broken.wav", "audio/wav", wav) {
                @Override
                public java.io.InputStream getInputStream() throws java.io.IOException {
                    throw new java.io.IOException("disk");
                }
            };
            assertThrows(java.io.IOException.class, () -> service.storeUpload(broken));
            assertEquals(1, countStatus(jdbc, "READY", "UPLOAD"));

            jdbc.update("update media_asset set status = 'STAGED', created_at = '2000-01-01T00:00:00.000Z' where id = ?",
                    uploaded.get("id"));
            service.recover();
            assertEquals("READY", status(jdbc, uploaded.get("id")));
            service.recover();
            assertEquals("READY", status(jdbc, uploaded.get("id")));

            Map<String, Object> row = store.findAsset(String.valueOf(uploaded.get("id")));
            Files.delete(Paths.get(media).resolve(String.valueOf(row.get("storagePath"))));
            service.recover();
            assertEquals("DAMAGED", status(jdbc, uploaded.get("id")));
            assertNull(service.findAsset(String.valueOf(uploaded.get("id"))));
            assertTrue(((java.util.List<?>) service.timeline().get("uploads")).isEmpty());

            context.getBean(AppProperties.class).getMedia().setMaxUploadBytes(32L * 1024L * 1024L);
            Path orphan = Paths.get(media).resolve(runId).resolve("uploads").resolve("orphan.bin");
            Files.createDirectories(orphan.getParent());
            Files.write(orphan, new byte[]{1, 2, 3});
            Files.setLastModifiedTime(orphan, FileTime.from(Instant.parse("2000-01-01T00:00:00Z")));
            service.recover();
            assertTrue(Files.notExists(orphan));

            ExecutorService pool = Executors.newFixedThreadPool(4);
            Set<String> ids = new HashSet<String>();
            Future<?>[] tasks = new Future<?>[4];
            for (int i = 0; i < tasks.length; i++) {
                final int index = i;
                tasks[i] = pool.submit(() -> {
                    Map<String, Object> body = service.storeUpload(file("c" + index + ".wav", "audio/wav", wav()));
                    synchronized (ids) {
                        ids.add(String.valueOf(body.get("id")));
                    }
                    return null;
                });
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            pool.shutdown();
            assertEquals(4, ids.size());
            for (String id : ids) {
                assertEquals("READY", status(jdbc, id));
            }
        }
    }

    private static int countStatus(JdbcTemplate jdbc, String status, String kind) {
        Integer count = jdbc.queryForObject(
                "select count(*) from media_asset where status = ? and kind = ?", Integer.class, status, kind);
        return count == null ? 0 : count.intValue();
    }

    private static String status(JdbcTemplate jdbc, Object id) {
        return jdbc.queryForObject("select status from media_asset where id = ?", String.class, id);
    }

    private static MockMultipartFile file(String name, String type, byte[] body) {
        return new MockMultipartFile("file", name, type, body);
    }

    private static byte[] wav() {
        byte[] body = new byte[44];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, body, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, body, 8, 4);
        return body;
    }
}
