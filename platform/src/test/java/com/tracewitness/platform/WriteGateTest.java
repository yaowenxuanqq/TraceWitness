package com.tracewitness.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WriteGateTest {
    @Test
    void writesRejectBadInputAndStayInsideTheCurrentRun() throws Exception {
        Path dir = Files.createTempDirectory("tw-write");
        String database = dir.resolve("tracewitness").toAbsolutePath().toString().replace('\\', '/');
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = stub(mapper);
        server.start();
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TraceWitnessApplication.class).run(
                "--spring.datasource.url=jdbc:h2:file:" + database,
                "--spring.datasource.username=sa",
                "--tracewitness.mode=MOCK",
                "--tracewitness.auto-loop=true",
                "--tracewitness.api-token=test-token",
                "--tracewitness.ai-base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                "--tracewitness.media-dir=" + dir.resolve("media").toAbsolutePath().toString().replace('\\', '/'),
                "--server.port=0")) {
            String port = context.getEnvironment().getProperty("local.server.port");
            String root = "http://127.0.0.1:" + port;
            RestTemplate http = quietClient();
            assertEquals("test-token", http.getForObject(root + "/api/session", JsonNode.class).get("token").asText());
            assertEquals(401, post(http, root + "/api/plugins/rollback", "{\"actor\":\"investigator\"}", null).getStatusCodeValue());

            JsonNode overview = http.getForObject(root + "/api/overview", JsonNode.class);
            String ticket = first(overview.get("tickets"), "status", "pending_human", "ticketKind", "merge");
            String passage = firstAlert(overview.get("passages"));
            String hardCase = first(overview.get("hardCases"), "status", "open", null, null);
            String active = first(overview.get("plugins"), "slot", "active", null, null);
            assertNotNull(ticket);
            assertNotNull(passage);
            assertNotNull(hardCase);
            assertNotNull(active);

            assertEquals(400, post(http, root + "/api/profiles/tickets/" + ticket + "/decision",
                    "{\"decision\":\"approve\",\"actor\":\"investigator\",\"extra\":1}", "test-token").getStatusCodeValue());
            assertEquals(400, post(http, root + "/api/profiles/tickets/" + ticket + "/decision",
                    "{\"decision\":\"approve\"}", "test-token").getStatusCodeValue());
            assertEquals(400, post(http, root + "/api/profiles/tickets/" + ticket + "/decision",
                    "{\"decision\":1,\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            assertEquals(400, post(http, root + "/api/profiles/tickets/" + ticket + "/decision",
                    "{", "test-token").getStatusCodeValue());
            assertEquals(404, post(http, root + "/api/profiles/tickets/tw.ticket.missing.000001/decision",
                    "{\"decision\":\"approve\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            String longActor = new String(new char[65]).replace('\0', 'a');
            assertEquals(400, post(http, root + "/api/profiles/tickets/" + ticket + "/decision",
                    "{\"decision\":\"approve\",\"actor\":\"" + longActor + "\"}", "test-token").getStatusCodeValue());

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("insert into merge_ticket(id, run_id, case_key, ticket_kind, status, reason, detail, created_at) "
                            + "values ('tw.ticket.run-other.000001', 'run-other', 'foreign', 'merge', 'pending_human', 'ambiguous', 'other', '2026-01-01T00:00:00.000Z')");
            jdbc.update("insert into agent_run(id, run_id, task, status, mock_mode, conclusion, insufficient_evidence, "
                            + "confidence, source_ids, failure_type, requested_actions, created_at) "
                            + "values ('tw.agent.run-other.000001', 'run-other', 'foreign', 'COMPLETED', true, "
                            + "'foreign', false, 1.00, '[]', null, '[]', '2026-01-01T00:00:00.000Z')");
            assertEquals(404, post(http, root + "/api/profiles/tickets/tw.ticket.run-other.000001/decision",
                    "{\"decision\":\"approve\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            assertEquals("pending_human", jdbc.queryForObject(
                    "select status from merge_ticket where id = 'tw.ticket.run-other.000001'", String.class));
            JsonNode runs = http.getForObject(root + "/api/agent/runs", JsonNode.class);
            assertNotNull(runs);
            assertEquals(1, runs.size());
            for (JsonNode run : runs) {
                assertEquals("run-w02-mock-incident-20261110", run.path("runId").asText());
            }

            HttpHeaders cookie = new HttpHeaders();
            ResponseEntity<String> health = http.exchange(root + "/api/health", HttpMethod.GET, new HttpEntity<String>(cookie), String.class);
            String setCookie = health.getHeaders().getFirst("Set-Cookie");
            assertNotNull(setCookie);
            HttpHeaders cookieOnly = new HttpHeaders();
            cookieOnly.add("Cookie", setCookie.split(";", 2)[0]);
            ResponseEntity<String> byCookie = http.exchange(
                    root + "/api/profiles/tickets/" + ticket + "/decision",
                    HttpMethod.POST,
                    new HttpEntity<String>("{\"decision\":\"approve\",\"actor\":\"investigator\"}", jsonHeaders(cookieOnly)),
                    String.class);
            assertEquals(200, byCookie.getStatusCodeValue());
            assertEquals(409, post(http, root + "/api/profiles/tickets/" + ticket + "/decision",
                    "{\"decision\":\"approve\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());

            assertEquals(200, post(http, root + "/api/alerts/" + passage + "/decision",
                    "{\"decision\":\"approve\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            assertEquals(409, post(http, root + "/api/alerts/" + passage + "/decision",
                    "{\"decision\":\"approve\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            assertEquals(200, post(http, root + "/api/hard-cases/" + hardCase + "/label",
                    "{\"label\":\"true_negative\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            assertEquals(409, post(http, root + "/api/hard-cases/" + hardCase + "/label",
                    "{\"label\":\"true_negative\",\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            assertEquals(200, post(http, root + "/api/plugins/rollback",
                    "{\"actor\":\"investigator\"}", "test-token").getStatusCodeValue());
            JsonNode afterRollback = http.getForObject(root + "/api/overview", JsonNode.class);
            String nowActive = first(afterRollback.get("plugins"), "slot", "active", null, null);
            assertEquals(200, post(http, root + "/api/plugins/" + nowActive + "/fault", "", "test-token").getStatusCodeValue());
            assertEquals(409, post(http, root + "/api/plugins/" + nowActive + "/fault", "", "test-token").getStatusCodeValue());
        } finally {
            server.stop(0);
        }
    }

    private static ResponseEntity<String> post(RestTemplate http, String url, String body, String token) {
        HttpHeaders headers = jsonHeaders(new HttpHeaders());
        if (token != null) {
            headers.set("X-Trace-Token", token);
        }
        return http.exchange(url, HttpMethod.POST, new HttpEntity<String>(body, headers), String.class);
    }

    private static HttpHeaders jsonHeaders(HttpHeaders headers) {
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private static RestTemplate quietClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setOutputStreaming(false);
        RestTemplate http = new RestTemplate(factory);
        http.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
        http.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return http;
    }

    private static String first(JsonNode rows, String field, String value, String field2, String value2) {
        if (rows == null) {
            return null;
        }
        for (JsonNode row : rows) {
            if (value.equals(row.path(field).asText()) && (field2 == null || value2.equals(row.path(field2).asText()))) {
                return row.path("id").asText();
            }
        }
        return null;
    }

    private static String firstAlert(JsonNode rows) {
        if (rows == null) {
            return null;
        }
        for (JsonNode row : rows) {
            if (row.path("alerted").asBoolean() && "pending_human".equals(row.path("status").asText())) {
                return row.path("id").asText();
            }
        }
        return null;
    }

    private static HttpServer stub(ObjectMapper mapper) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/agent/select-tool", exchange -> {
            ObjectNode response = mapper.createObjectNode();
            response.put("tool_name", "search_events");
            response.putObject("arguments").put("limit", 5);
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/agent/report", exchange -> {
            ObjectNode body = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            ObjectNode response = mapper.createObjectNode();
            response.put("conclusion", "结论仅依据已返回的证据。");
            ArrayNode ids = response.putArray("source_ids");
            body.path("tool_results").forEach(result -> result.path("source_ids").forEach(ids::add));
            response.put("confidence", 0.8);
            response.put("insufficient_evidence", false);
            response.putArray("requested_actions");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        server.createContext("/v1/vision/track", exchange -> {
            mapper.readTree(exchange.getRequestBody());
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
            ObjectNode response = mapper.createObjectNode();
            response.put("attribute_calls", 1);
            response.put("face_quality", 0.90);
            response.put("body_quality", 0.90);
            response.put("mode", "gate_failure".equals(body.path("case_key").asText()) ? "gate_failure" : "preferred");
            response.put("frame_kind", "crop");
            response.put("chosen_frame", 0);
            response.put("chosen_tick", 0);
            response.putObject("attributes").put("upper_color", "light").put("posture", "upright");
            response.putObject("masked").put("face_region", "redacted");
            respond(exchange, mapper.writeValueAsBytes(response));
        });
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
