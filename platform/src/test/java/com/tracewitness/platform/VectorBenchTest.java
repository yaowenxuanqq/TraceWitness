package com.tracewitness.platform;

import com.tracewitness.platform.retrieval.RetrievalPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorBenchTest {
    @Test
    void tenThousandVectorSearchStaysUnderOneSecond() throws Exception {
        Map<String, Object> query = gallery(10000);
        RetrievalPolicy.evaluate(query);
        int repeats = 15;
        long[] samples = new long[repeats];
        for (int i = 0; i < repeats; i++) {
            long started = System.nanoTime();
            RetrievalPolicy.evaluate(query);
            samples[i] = (System.nanoTime() - started) / 1000000L;
        }
        Arrays.sort(samples);
        long p50 = samples[repeats / 2];
        long p95 = samples[(int) Math.ceil(repeats * 0.95) - 1];
        String line = "vectors=10000 dim=3 repeats=" + repeats
                + " p50_ms=" + p50 + " p95_ms=" + p95 + " target_p95_ms=1000";
        Path out = Paths.get("build", "vector-bench.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, (line + "\n").getBytes("UTF-8"));
        assertTrue(p95 < 1000L, line);
    }

    private static Map<String, Object> gallery(int size) {
        List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < size; i++) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", "v" + i);
            row.put("face", Arrays.asList(i % 2 == 0 ? "1.00" : "0.00", i % 2 == 0 ? "0.00" : "1.00", "0.00"));
            candidates.add(row);
        }
        Map<String, Object> image = new LinkedHashMap<String, Object>();
        image.put("face", Arrays.asList("1.00", "0.00", "0.00"));
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("candidates", candidates);
        input.put("images", Arrays.asList(image));
        return input;
    }
}
