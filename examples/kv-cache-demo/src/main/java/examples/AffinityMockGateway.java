/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded mock gateway mimicking an OpenAI-compatible Ascend-affinity
 * endpoint.
 *
 * <p>The gateway answers every {@code POST /v1/chat/completions} with a
 * minimal valid chat completion and records the {@code extra_body.agent_hint}
 * payload of each request, so {@link AscendAffinityKvCacheDemo} can assert
 * which hint the client actually sent and run fully offline.</p>
 *
 * @since 0.1.16
 */
public final class AffinityMockGateway {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String COMPLETION_BODY = "{\"id\":\"mock-1\",\"object\":\"chat.completion\","
            + "\"created\":0,\"model\":\"mock-model\",\"choices\":[{\"index\":0,\"message\":"
            + "{\"role\":\"assistant\",\"content\":\"mock-ok\"},\"finish_reason\":\"stop\"}],"
            + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";

    private final List<String> agentHints = new ArrayList<>();
    private HttpServer server;

    /**
     * Start the gateway on an ephemeral loopback port.
     *
     * @throws IOException when the embedded server cannot be created or started
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64), new MockGatewayThreadFactory()));
        server.start();
    }

    /**
     * Base URL of the running gateway.
     *
     * @return loopback base URL without a trailing slash
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Snapshot of the agent_hint payloads received so far, in arrival order.
     *
     * @return unmodifiable list of raw agent_hint JSON strings
     */
    public List<String> recordedAgentHints() {
        synchronized (agentHints) {
            return List.copyOf(agentHints);
        }
    }

    /**
     * Clear all recorded hints so a new demo phase starts from a blank slate.
     */
    public void reset() {
        synchronized (agentHints) {
            agentHints.clear();
        }
    }

    /**
     * Stop the gateway and release the port.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("===requestBody===" + requestBody);
        recordAgentHint(requestBody);
        byte[] payload = COMPLETION_BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void recordAgentHint(String requestBody) {
        try {
            JsonNode root = MAPPER.readTree(requestBody);
            JsonNode hint = root.path("agent_hint");
            if (hint.isMissingNode()) {
                hint = root.path("extra_body").path("agent_hint");
            }
            if (!hint.isMissingNode()) {
                synchronized (agentHints) {
                    agentHints.add(MAPPER.writeValueAsString(hint));
                }
            }
        } catch (IOException exception) {
            System.out.println("[mock-gateway] ignored unparsable request: " + exception.getMessage());
        }
    }

    private static final class MockGatewayThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "affinity-mock-gateway-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
