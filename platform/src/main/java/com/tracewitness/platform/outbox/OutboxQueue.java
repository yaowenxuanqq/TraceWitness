package com.tracewitness.platform.outbox;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Component
public class OutboxQueue {
    private final Deque<Long> ids = new ArrayDeque<>();

    public void addAll(List<Long> values) {
        ids.addAll(values);
    }

    public List<Long> drain() {
        List<Long> copy = new ArrayList<>(ids);
        ids.clear();
        return copy;
    }
}
