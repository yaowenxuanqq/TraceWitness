package com.tracewitness.platform;

import com.tracewitness.platform.store.PlatformStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreIsolationTest {
    @Test
    void concurrentIdsAreUniqueAndOutboxStaysInsideRun() throws Exception {
        Path dir = Files.createTempDirectory("tw-store");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class)
                .run(
                        "--spring.datasource.url=jdbc:h2:file:" + database + ";LOCK_TIMEOUT=5000",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.hikari.maximum-pool-size=16",
                        "--tracewitness.mode=MOCK",
                        "--tracewitness.auto-loop=false",
                        "--server.port=0")) {
            PlatformStore store = context.getBean(PlatformStore.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String runA = "run-store-a";
            String runB = "run-store-b";
            int threads = 8;
            int each = 125;
            Set<String> ids = ConcurrentHashMap.newKeySet();
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < each; n++) {
                            ids.add(store.nextId(runA, "event"));
                        }
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(threads * each, ids.size());
            assertEquals(new HashSet<String>(ids).size(), ids.size());

            jdbc.update("insert into outbox(run_id, event_id, payload, status, created_at) values (?, ?, '{}', 'PENDING', '2026-01-01T00:00:00.000Z')",
                    runA, "tw.event.run-store-a.000001");
            jdbc.update("insert into outbox(run_id, event_id, payload, status, created_at) values (?, ?, '{}', 'PENDING', '2026-01-01T00:00:00.000Z')",
                    runB, "tw.event.run-store-b.000001");
            assertEquals(1, store.publishRemaining(runA, "2026-01-01T00:00:01.000Z"));
            assertEquals(0, store.outboxCounts(runA).get("pending"));
            assertEquals(1, store.outboxCounts(runA).get("published"));
            assertEquals(1, store.outboxCounts(runB).get("pending"));
            assertEquals(0, store.outboxCounts(runB).get("published"));
        }
    }
}
