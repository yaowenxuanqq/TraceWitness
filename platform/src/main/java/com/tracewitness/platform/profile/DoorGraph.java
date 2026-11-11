package com.tracewitness.platform.profile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DoorGraph {
    public static final List<Node> NODES = Collections.unmodifiableList(Arrays.asList(
            new Node("lobby", "大堂"),
            new Node("unit-a", "一单元门"),
            new Node("unit-b", "二单元门"),
            new Node("stair", "楼梯间"),
            new Node("roof", "屋顶")));
    public static final List<Edge> EDGES = Collections.unmodifiableList(Arrays.asList(
            new Edge("lobby", "unit-a", 30),
            new Edge("unit-a", "unit-b", 40),
            new Edge("lobby", "stair", 20)));

    private DoorGraph() {
    }

    public static int shortestSeconds(String from, String to) {
        if (from == null || to == null) {
            return -1;
        }
        if (from.equals(to)) {
            return 0;
        }
        Map<String, Map<String, Integer>> adjacent = new LinkedHashMap<String, Map<String, Integer>>();
        for (Node node : NODES) {
            adjacent.put(node.code, new LinkedHashMap<String, Integer>());
        }
        for (Edge edge : EDGES) {
            adjacent.get(edge.from).put(edge.to, edge.seconds);
            adjacent.get(edge.to).put(edge.from, edge.seconds);
        }
        if (!adjacent.containsKey(from) || !adjacent.containsKey(to)) {
            return -1;
        }
        Map<String, Integer> distance = new HashMap<String, Integer>();
        for (String code : adjacent.keySet()) {
            distance.put(code, Integer.MAX_VALUE);
        }
        distance.put(from, 0);
        Map<String, Boolean> settled = new HashMap<String, Boolean>();
        while (true) {
            String current = null;
            int best = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> entry : distance.entrySet()) {
                if (Boolean.TRUE.equals(settled.get(entry.getKey())) || entry.getValue() >= best) {
                    continue;
                }
                best = entry.getValue();
                current = entry.getKey();
            }
            if (current == null || current.equals(to)) {
                break;
            }
            settled.put(current, Boolean.TRUE);
            for (Map.Entry<String, Integer> edge : adjacent.get(current).entrySet()) {
                if (distance.get(current) == Integer.MAX_VALUE) {
                    continue;
                }
                int next = distance.get(current) + edge.getValue();
                if (next < distance.get(edge.getKey())) {
                    distance.put(edge.getKey(), next);
                }
            }
        }
        int result = distance.get(to);
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    public static final class Node {
        public final String code;
        public final String name;

        public Node(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    public static final class Edge {
        public final String from;
        public final String to;
        public final int seconds;

        public Edge(String from, String to, int seconds) {
            this.from = from;
            this.to = to;
            this.seconds = seconds;
        }
    }
}
