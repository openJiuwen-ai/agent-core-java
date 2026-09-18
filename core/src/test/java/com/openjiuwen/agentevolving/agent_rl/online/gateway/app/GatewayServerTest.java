package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.agentevolving.agent_rl.storage.LoRARepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void chatCompletionsStreamsAndInjectsLatestLora() throws Exception {
        GatewayConfig config = baseConfig();
        CapturingForwarder forwarder = new CapturingForwarder(Map.of(
            "id", "chatcmpl-1",
            "choices", List.of(Map.of("message", Map.of("content", "ok"), "finish_reason", "stop"))
        ));
        LoRARepository repository = new LoRARepository(tempDir.resolve("repo").toString());
        Path loraFile = tempDir.resolve("adapter.bin");
        Files.writeString(loraFile, "payload");
        repository.publish("user-1", loraFile.toString());

        GatewayServer server = new GatewayServer(
            config,
            forwarder,
            new NoOpUpstreamClient(),
            new FakeTrajectoryGateway(),
            null,
            repository
        );

        byte[] requestBody = OBJECT_MAPPER.writeValueAsBytes(Map.of(
            "messages", List.of(Map.of("role", "user", "content", "hi")),
            "stream", true
        ));

        GatewayServer.ChatCompletionResult result = server.chatCompletions(
            Map.of("x-user-id", "user-1", "authorization", "Bearer gateway-secret"),
            requestBody,
            "Bearer gateway-secret"
        );

        assertTrue(result.stream());
        assertEquals("text/event-stream", result.mediaType());
        assertTrue(result.eventStream().get(0).contains("chat.completion.chunk"));
        assertEquals("Bearer llm-secret", forwarder.lastHeaders.get("Authorization"));
        assertEquals(
            "user-1",
            ((Map<?, ?>) forwarder.lastBody.get("extra_body")).get("lora_name")
        );
        assertFalse(forwarder.lastBody.containsKey("stream"));
    }

    @Test
    void gatewayStatsIncludesRequestCounter() throws Exception {
        GatewayConfig config = baseConfig();
        CapturingForwarder forwarder = new CapturingForwarder(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", "ok"), "finish_reason", "stop"))
        ));
        FakeTrajectoryGateway trajectoryGateway = new FakeTrajectoryGateway();
        GatewayServer server = new GatewayServer(
            config,
            forwarder,
            new NoOpUpstreamClient(),
            trajectoryGateway,
            null,
            null
        );

        byte[] requestBody = OBJECT_MAPPER.writeValueAsBytes(Map.of(
            "messages", List.of(Map.of("role", "user", "content", "hi"))
        ));
        server.chatCompletions(
            Map.of("x-user-id", "user-1"),
            requestBody,
            "Bearer gateway-secret"
        );

        Map<String, Object> stats = server.gatewayStats("Bearer gateway-secret");

        assertEquals(1, stats.get("total_requests"));
        assertEquals(7, stats.get("total_samples"));
        assertEquals(5, stats.get("trajectory_store_total"));
        assertEquals(2, stats.get("trajectory_store_pending"));
    }

    @Test
    void createUploadBatchWrapsValidationErrorAsHttp400() {
        GatewayConfig config = baseConfig();
        FakeTrajectoryGateway trajectoryGateway = new FakeTrajectoryGateway();
        trajectoryGateway.error = new IllegalArgumentException("bad payload");
        GatewayServer server = new GatewayServer(
            config,
            new CapturingForwarder(Map.of()),
            new NoOpUpstreamClient(),
            trajectoryGateway,
            null,
            null
        );

        GatewayHttpException exception = assertThrows(
            GatewayHttpException.class,
            () -> server.createUploadBatch(Map.of("protocol_version", "bad"), "Bearer gateway-secret")
        );

        assertEquals(400, exception.getStatusCode());
        assertEquals("bad payload", exception.getDetail());
    }

    @Test
    void proxyOtherFiltersHopByHopHeaders() {
        GatewayConfig config = baseConfig();
        GatewayServer server = new GatewayServer(
            config,
            new CapturingForwarder(Map.of()),
            new FixedUpstreamClient(new GatewayHttpResponse(
                202,
                "proxied",
                Map.of(
                    "content-type", "text/plain",
                    "connection", "close",
                    "x-trace-id", "abc123"
                ),
                "text/plain"
            )),
            new FakeTrajectoryGateway(),
            null,
            null
        );

        GatewayServer.ProxyForwardResult response = server.proxyOther(
            "v1/models",
            "GET",
            Map.of("limit", 1),
            Map.of("x-user-id", "user-1"),
            new byte[0],
            "Bearer gateway-secret"
        );

        assertEquals(202, response.statusCode());
        assertEquals("text/plain", response.mediaType());
        assertEquals("abc123", response.headers().get("x-trace-id"));
        assertFalse(response.headers().containsKey("connection"));
        assertArrayEquals("proxied".getBytes(), response.content());
    }

    private static GatewayConfig baseConfig() {
        GatewayConfig config = new GatewayConfig();
        config.setGatewayApiKey("gateway-secret");
        config.setLlmApiKey("llm-secret");
        config.setModelId("demo-model");
        config.setLlmUrl("http://mock.llm/");
        config.setSingleUserDefault(true);
        return config;
    }

    private static final class CapturingForwarder extends Forwarder {
        private final Map<String, Object> response;
        private Map<String, Object> lastBody = Map.of();
        private Map<String, String> lastHeaders = Map.of();

        private CapturingForwarder(Map<String, Object> response) {
            super(new NoOpUpstreamClient(), "demo-model");
            this.response = response;
        }

        @Override
        public Map<String, Object> forward(Map<String, Object> body, Map<String, String> headers) {
            this.lastBody = new LinkedHashMap<>(body);
            this.lastHeaders = new LinkedHashMap<>(headers);
            return response;
        }
    }

    private static final class FakeTrajectoryGateway implements GatewayServer.TrajectoryGateway {
        private IllegalArgumentException error;

        @Override
        public Map<String, Object> snapshotStats() {
            return Map.of(
                "total_samples", 7,
                "trajectory_store_total", 5,
                "trajectory_store_pending", 2
            );
        }

        @Override
        public Map<String, Object> ingestRailBatch(Map<String, Object> payload) {
            if (error != null) {
                throw error;
            }
            return Map.of("accepted", 1, "rejected", 0);
        }
    }

    private static class NoOpUpstreamClient implements UpstreamGatewayClient {
        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            return new GatewayHttpResponse(200, "{}");
        }

        @Override
        public GatewayHttpResponse request(
            String method,
            String url,
            Map<String, Object> params,
            Map<String, String> headers,
            byte[] content
        ) {
            return new GatewayHttpResponse(200, "{}");
        }
    }

    private static final class FixedUpstreamClient extends NoOpUpstreamClient {
        private final GatewayHttpResponse response;

        private FixedUpstreamClient(GatewayHttpResponse response) {
            this.response = response;
        }

        @Override
        public GatewayHttpResponse request(
            String method,
            String url,
            Map<String, Object> params,
            Map<String, String> headers,
            byte[] content
        ) {
            return response;
        }
    }
}
