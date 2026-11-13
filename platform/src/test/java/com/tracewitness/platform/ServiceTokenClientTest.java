package com.tracewitness.platform;

import com.sun.net.httpserver.HttpServer;
import com.tracewitness.platform.agent.PythonAgentClient;
import com.tracewitness.platform.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTokenClientTest {
    @Test
    void clientSendsBearerTokenAndClassifiesAuthenticationFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<String>();
        server.createContext("/v1/agent/select-tool", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            InputStream body = exchange.getRequestBody();
            byte[] buffer = new byte[1024];
            while (body.read(buffer) >= 0) {
                // Drain the request so the client can finish.
            }
            byte[] response = authorization.get() != null && authorization.get().equals("Bearer secret")
                    ? "{\"tool_calls\":[]}".getBytes(StandardCharsets.UTF_8)
                    : "unauthorized".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(authorization.get() != null && authorization.get().equals("Bearer secret") ? 200 : 401, response.length);
            OutputStream output = exchange.getResponseBody();
            output.write(response);
            output.close();
        });
        server.start();
        try {
            AppProperties properties = new AppProperties();
            properties.setAiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setServiceToken("secret");
            PythonAgentClient client = new PythonAgentClient(properties);
            assertTrue(client.selectTools("run", "EVAL", "2026-01-01T00:00:00.000Z", 1L, "task",
                    Collections.singletonList("search_events"), Collections.<java.util.Map<String, Object>>emptyList()).isEmpty());
            assertEquals("Bearer secret", authorization.get());

            properties.setServiceToken("wrong");
            PythonAgentClient rejected = new PythonAgentClient(properties);
            PythonAgentClient.AgentCallException failure = assertThrows(PythonAgentClient.AgentCallException.class,
                    () -> rejected.selectTools("run", "EVAL", "2026-01-01T00:00:00.000Z", 1L, "task",
                            Collections.singletonList("search_events"), Collections.<java.util.Map<String, Object>>emptyList()));
            assertEquals("service_auth_failed", failure.failureType());
            assertEquals("服务认证失败", failure.publicDetail());
            assertTrue(!failure.getMessage().contains("secret"));
            assertTrue(!failure.getMessage().contains("wrong"));
        } finally {
            server.stop(0);
        }
    }
}
