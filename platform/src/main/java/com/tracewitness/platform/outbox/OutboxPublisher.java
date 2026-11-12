package com.tracewitness.platform.outbox;

import com.tracewitness.platform.store.PlatformStore;
import com.tracewitness.platform.time.TimeSource;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublisher {
    private final PlatformStore store;
    private final OutboxQueue queue;
    private final TimeSource timeSource;

    public OutboxPublisher(PlatformStore store, OutboxQueue queue, TimeSource timeSource) {
        this.store = store;
        this.queue = queue;
        this.timeSource = timeSource;
    }

    public int publishAll(String runId) {
        String publishedAt = timeSource.formatNow();
        int published = store.publishIds(queue.drain(), publishedAt);
        return published + store.publishRemaining(runId, publishedAt);
    }
}
