package com.tracewitness.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.agent.InvestigationTools;
import com.tracewitness.platform.profile.ProfileService;
import com.tracewitness.platform.store.ProfileStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileWeek8Test {
    private static final String RUN_ID = "run-w02-mock-incident-20261110";

    @Test
    void unreachableMergeIsForbiddenAndApprovalIsAudited() throws Exception {
        Path dir = Files.createTempDirectory("tw-w08");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        HttpServer server = stub();
        server.start();
        try {
            String[] properties = properties(database, dir.resolve("media"), server.getAddress().getPort());
            Map<String, Object> first = read(properties);
            Map<String, Object> second = read(properties);
            assertEquals(first.get("ticketIds"), second.get("ticketIds"));
            assertEquals(first.get("statuses"), second.get("statuses"));
            assertEquals("forbidden", first.get("roof"));
            assertEquals("unreachable", first.get("roofReason"));
            assertEquals(Integer.valueOf(-1), first.get("roofTravel"));
            assertEquals(Integer.valueOf(2), first.get("roofRejected"));
            assertEquals("forbidden", first.get("fast"));
            assertEquals("travel_conflict", first.get("fastReason"));
            assertEquals(Integer.valueOf(70), first.get("fastTravel"));
            assertEquals("0.99,0.01,0.00", first.get("centroid"));
            assertEquals("unit-a", first.get("address"));
            assertEquals("lobby", first.get("strangerAddress"));
            assertEquals(Integer.valueOf(3), first.get("strangerVisits"));
            assertEquals("open", first.get("frequency"));
            assertEquals("pending_human", first.get("ambiguous"));
            assertTrue((Boolean) first.get("audited"));
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
                ProfileService profiles = context.getBean(ProfileService.class);
                ProfileStore store = context.getBean(ProfileStore.class);
                InvestigationTools tools = context.getBean(InvestigationTools.class);
                Map<String, Object> named = tools.execute(RUN_ID, "search_person", query("居民"));
                assertEquals(Boolean.TRUE, named.get("person_created"));
                assertEquals(1, ((List<?>) named.get("people")).size());
                Map<String, Object> empty = tools.execute(RUN_ID, "search_person", query("衣着"));
                assertEquals(Boolean.FALSE, empty.get("person_created"));
                assertTrue(((List<?>) empty.get("source_ids")).isEmpty());
                Map<String, Object> approved = profiles.decideTicket(String.valueOf(first.get("ambiguousId")), "approve", "investigator");
                assertEquals(Boolean.TRUE, approved.get("applied"));
                Map<String, Object> lookalike = store.findProfileByCode(RUN_ID, "lookalike");
                Map<String, Object> resident = store.findProfileByCode(RUN_ID, "resident-chen");
                assertEquals("merged", lookalike.get("status"));
                assertEquals(resident.get("id"), lookalike.get("mergedInto"));
                assertEquals("0.99,0.07,0.00", resident.get("centroid"));
                boolean approvedAudit = false;
                for (Map<String, Object> audit : store.listAudits(RUN_ID)) {
                    if ("merge_approved".equals(audit.get("action")) && first.get("ambiguousId").equals(audit.get("targetId"))) {
                        approvedAudit = true;
                    }
                }
                assertTrue(approvedAudit);
                Map<String, Object> again = profiles.decideTicket(String.valueOf(first.get("ambiguousId")), "approve", "investigator");
                assertEquals(Boolean.FALSE, again.get("applied"));
            }
        } finally {
            server.stop(0);
        }
    }

    private static Map<String, Object> read(String[] properties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(properties)) {
            ProfileStore store = context.getBean(ProfileStore.class);
            Map<String, Map<String, Object>> tickets = new LinkedHashMap<String, Map<String, Object>>();
            List<String> ticketIds = new java.util.ArrayList<String>();
            List<String> statuses = new java.util.ArrayList<String>();
            for (Map<String, Object> ticket : store.listTickets(RUN_ID)) {
                tickets.put(String.valueOf(ticket.get("caseKey")), ticket);
                ticketIds.add(String.valueOf(ticket.get("id")));
                statuses.add(String.valueOf(ticket.get("status")));
            }
            java.util.Set<String> auditTargets = new java.util.HashSet<String>();
            for (Map<String, Object> audit : store.listAudits(RUN_ID)) {
                auditTargets.add(String.valueOf(audit.get("targetId")));
            }
            Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("ticketIds", ticketIds);
            snapshot.put("statuses", statuses);
            snapshot.put("roof", tickets.get("roof-jump").get("status"));
            snapshot.put("roofReason", tickets.get("roof-jump").get("reason"));
            snapshot.put("roofTravel", tickets.get("roof-jump").get("travelSeconds"));
            snapshot.put("roofRejected", Integer.valueOf(store.countRejected(RUN_ID, "roof-jump")));
            snapshot.put("fast", tickets.get("fast-unit-b").get("status"));
            snapshot.put("fastReason", tickets.get("fast-unit-b").get("reason"));
            snapshot.put("fastTravel", tickets.get("fast-unit-b").get("travelSeconds"));
            snapshot.put("centroid", store.findProfileByCode(RUN_ID, "resident-chen").get("centroid"));
            snapshot.put("address", store.findProfileByCode(RUN_ID, "resident-chen").get("addressLabel"));
            snapshot.put("strangerAddress", store.findProfileByCode(RUN_ID, "stranger-jia").get("addressLabel"));
            snapshot.put("strangerVisits", store.findProfileByCode(RUN_ID, "stranger-jia").get("visitCount"));
            snapshot.put("frequency", tickets.get("stranger-frequency").get("status"));
            snapshot.put("ambiguous", tickets.get("ambiguous-pair").get("status"));
            snapshot.put("ambiguousId", tickets.get("ambiguous-pair").get("id"));
            snapshot.put("audited", Boolean.valueOf(auditTargets.containsAll(ticketIds)));
            assertEquals(5, store.listDoors(RUN_ID).size());
            assertEquals(3, store.listEdges(RUN_ID).size());
            return snapshot;
        }
    }

    private static Map<String, Object> query(String text) {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("query", text);
        return args;
    }

    private static HttpServer stub() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/agent/select-tool", exchange -> {
            exchange.getRequestBody().close();
            ObjectNode response = mapper.createObjectNode();
            response.putArray("tool_calls");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/agent/report", exchange -> {
            exchange.getRequestBody().close();
            ObjectNode response = mapper.createObjectNode();
            response.put("conclusion", "证据不足");
            response.putArray("source_ids");
            response.put("confidence", 0);
            response.put("insufficient_evidence", true);
            response.putArray("requested_actions");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/track", exchange -> {
            exchange.getRequestBody().close();
            ObjectNode response = mapper.createObjectNode();
            ObjectNode track = response.putArray("tracks").addObject();
            track.put("local_id", "track-1");
            ArrayNode points = track.putArray("points");
            for (int i = 0; i < 4; i++) {
                ObjectNode point = points.addObject();
                point.put("frame_index", i);
                point.put("tick", i);
                point.put("x", 0.2 + i * 0.1);
                point.put("y", 0.5);
                point.put("w", 0.08);
                point.put("h", 0.2);
                point.put("confidence", 0.9);
            }
            response.putArray("faults");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/structure", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            String caseKey = body.path("case_key").asText("live");
            ObjectNode response = mapper.createObjectNode();
            response.put("case_key", caseKey);
            response.put("attribute_calls", 1);
            response.put("face_quality", 0.90);
            response.put("body_quality", 0.90);
            response.put("mode", "track_break".equals(caseKey) ? "track_break" : "gate_failure".equals(caseKey) ? "gate_failure" : "preferred");
            response.put("frame_kind", "gate_failure".equals(caseKey) ? "full_frame" : "crop");
            response.put("chosen_frame", 0);
            response.put("chosen_tick", 0);
            response.putObject("attributes").put("upper_color", "light").put("posture", "upright");
            response.putObject("masked").put("upper_color", "light").put("subject_token", "masked").put("face_region", "redacted");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        return server;
    }

    private static String[] properties(String database, Path media, int port) {
        return new String[]{
                "--spring.datasource.url=jdbc:h2:file:" + database,
                "--spring.datasource.username=sa",
                "--tracewitness.mode=MOCK",
                "--tracewitness.auto-loop=true",
                "--tracewitness.ai-base-url=http://127.0.0.1:" + port,
                "--tracewitness.media-dir=" + media.toAbsolutePath().toString().replace('\\', '/'),
                "--server.port=0"
        };
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
