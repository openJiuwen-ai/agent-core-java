/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory.GatewayTrajectoryRuntime;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.agentevolving.agent_rl.storage.LoRARepository;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transport-agnostic route binding for the online-RL gateway.
 *
 * <p>Mirrors Python's module-level gateway app assembly in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/server.py}.</p>
 */
public final class GatewayServer implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Logger LOGGER = Logger.getLogger("online_rl.gateway");
    private static final Set<String> FILTERED_PROXY_RESPONSE_HEADERS = Set.of(
        "content-length",
        "transfer-encoding",
        "connection",
        "content-encoding"
    );

    private final GatewayConfig config;
    private final Forwarder forwarder;
    private final UpstreamGatewayClient upstreamClient;
    private final TrajectoryGateway trajectoryGateway;
    private final AutoCloseable closeResources;
    private final LoRARepository loraRepository;
    private final AtomicInteger totalRequests = new AtomicInteger();

    public GatewayServer(
        GatewayConfig config,
        Forwarder forwarder,
        UpstreamGatewayClient upstreamClient,
        GatewayTrajectoryRuntime trajectoryRuntime,
        AutoCloseable closeResources,
        LoRARepository loraRepository
    ) {
        this(
            config,
            forwarder,
            upstreamClient,
            adaptTrajectoryGateway(trajectoryRuntime),
            closeResources,
            loraRepository
        );
    }

    GatewayServer(
        GatewayConfig config,
        Forwarder forwarder,
        UpstreamGatewayClient upstreamClient,
        TrajectoryGateway trajectoryGateway,
        AutoCloseable closeResources,
        LoRARepository loraRepository
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder");
        this.upstreamClient = Objects.requireNonNull(upstreamClient, "upstreamClient");
        this.trajectoryGateway = Objects.requireNonNull(trajectoryGateway, "trajectoryGateway");
        this.closeResources = closeResources;
        this.loraRepository = loraRepository;
    }

    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    public Map<String, Object> gatewayStats(String authorization) {
        GatewayHttpHelpers.ensureGatewayAuth(config.getGatewayApiKey(), authorization);
        return snapshotStats(totalRequests.get());
    }

    public Map<String, Object> createUploadBatch(Map<String, Object> payload, String authorization) {
        GatewayHttpHelpers.ensureGatewayAuth(config.getGatewayApiKey(), authorization);
        try {
            Map<String, Object> result = trajectoryGateway.ingestRailBatch(
                payload != null ? payload : new LinkedHashMap<>()
            );
            return Map.of("ok", true, "result", result);
        } catch (IllegalArgumentException exception) {
            throw new GatewayHttpException(400, exception.getMessage());
        }
    }

    public ChatCompletionResult chatCompletions(
        Map<String, String> requestHeaders,
        byte[] requestBody,
        String authorization
    ) {
        GatewayHttpHelpers.ensureGatewayAuth(config.getGatewayApiKey(), authorization);
        totalRequests.incrementAndGet();

        Map<String, Object> body = parseRequestBody(requestBody);
        String userId = GatewayRequestContext.requireUserId(requestHeaders, config);
        injectLatestLora(body, userId);
        boolean clientWantsStream = pythonTruthy(body.remove("stream"));

        Map<String, Object> responseJson = forwardChatCompletions(requestHeaders, body);
        if (clientWantsStream) {
            return ChatCompletionResult.stream(
                GatewayHttpHelpers.streamChatResponse(responseJson, config.getModelId()),
                "text/event-stream"
            );
        }
        return ChatCompletionResult.json(responseJson, "application/json");
    }

    public ProxyForwardResult proxyOther(
        String path,
        String method,
        Map<String, Object> queryParams,
        Map<String, String> requestHeaders,
        byte[] bodyBytes,
        String authorization
    ) {
        GatewayHttpHelpers.ensureGatewayAuth(config.getGatewayApiKey(), authorization);
        totalRequests.incrementAndGet();

        String targetUrl = config.getLlmUrl().replaceAll("/+$", "") + "/" + (path == null ? "" : path);
        Map<String, String> upstreamHeaders = GatewayHttpHelpers.buildUpstreamHeaders(
            requestHeaders,
            config.getLlmApiKey()
        );

        GatewayHttpResponse response;
        try {
            response = upstreamClient.request(
                method,
                targetUrl,
                queryParams != null ? queryParams : Map.of(),
                upstreamHeaders,
                bodyBytes != null ? bodyBytes : new byte[0]
            );
        } catch (Exception exception) {
            throw new GatewayHttpException(502, "proxy failed: " + exception.getMessage());
        }

        Map<String, String> filteredHeaders = new LinkedHashMap<>();
        response.headers().forEach((key, value) -> {
            if (key != null && !FILTERED_PROXY_RESPONSE_HEADERS.contains(key.toLowerCase())) {
                filteredHeaders.put(key, value);
            }
        });
        return new ProxyForwardResult(
            response.statusCode(),
            response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8),
            filteredHeaders,
            response.contentType()
        );
    }

    @Override
    public void close() throws Exception {
        if (closeResources != null) {
            closeResources.close();
        }
    }

    private void injectLatestLora(Map<String, Object> body, String userId) {
        if (loraRepository == null) {
            return;
        }
        if (loraRepository.latestOrNull(userId) == null) {
            return;
        }

        Object extraBody = body.get("extra_body");
        Map<String, Object> normalizedExtraBody;
        if (extraBody == null) {
            normalizedExtraBody = new LinkedHashMap<>();
            body.put("extra_body", normalizedExtraBody);
        } else if (extraBody instanceof Map<?, ?> rawMap) {
            normalizedExtraBody = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> normalizedExtraBody.put(String.valueOf(key), value));
            body.put("extra_body", normalizedExtraBody);
        } else {
            throw new IllegalStateException("extra_body must be a mapping when latest LoRA injection is enabled");
        }
        normalizedExtraBody.put("lora_name", userId);
    }

    private Map<String, Object> forwardChatCompletions(Map<String, String> requestHeaders, Map<String, Object> body) {
        long startedAt = System.nanoTime();
        String traceId = GatewayRequestContext.resolveTraceId(requestHeaders);
        List<?> messages = GatewayRequestContext.requireMessages(body);
        GatewayRequestContext.requireUserId(requestHeaders, config);
        Map<String, String> upstreamHeaders = GatewayHttpHelpers.buildUpstreamHeaders(
            requestHeaders,
            config.getLlmApiKey()
        );

        LOGGER.log(
            Level.FINE,
            "[Gateway {0}] proxy_only messages={1} stream={2}",
            new Object[]{traceId, messages.size(), pythonTruthy(body.get("stream"))}
        );

        Map<String, Object> responseJson = forwarder.forward(body, upstreamHeaders);
        LOGGER.log(
            Level.FINE,
            "[Gateway] chat_completions cost_ms={0}",
            (System.nanoTime() - startedAt) / 1_000_000.0d
        );
        return responseJson;
    }

    private Map<String, Object> snapshotStats(int requestCount) {
        Map<String, Object> trajectoryStats = trajectoryGateway.snapshotStats();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_requests", requestCount);
        stats.put("total_samples", trajectoryStats.get("total_samples"));
        stats.put("trajectory_store_total", trajectoryStats.get("trajectory_store_total"));
        stats.put("trajectory_store_pending", trajectoryStats.get("trajectory_store_pending"));
        return stats;
    }

    private Map<String, Object> parseRequestBody(byte[] requestBody) {
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(
                requestBody != null ? requestBody : new byte[0],
                MAP_TYPE
            );
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception exception) {
            throw new GatewayHttpException(400, "invalid json: " + exception.getMessage());
        }
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static TrajectoryGateway adaptTrajectoryGateway(GatewayTrajectoryRuntime runtime) {
        Objects.requireNonNull(runtime, "trajectoryRuntime");
        return new TrajectoryGateway() {
            @Override
            public Map<String, Object> snapshotStats() {
                return runtime.snapshotStats();
            }

            @Override
            public Map<String, Object> ingestRailBatch(Map<String, Object> payload) {
                return runtime.getRailIngestor().ingestRailBatch(payload);
            }
        };
    }

    interface TrajectoryGateway {
        Map<String, Object> snapshotStats();

        Map<String, Object> ingestRailBatch(Map<String, Object> payload);
    }

    public record ChatCompletionResult(
        boolean stream,
        Map<String, Object> jsonBody,
        List<String> eventStream,
        String mediaType
    ) {
        static ChatCompletionResult json(Map<String, Object> jsonBody, String mediaType) {
            return new ChatCompletionResult(false, jsonBody, List.of(), mediaType);
        }

        static ChatCompletionResult stream(List<String> eventStream, String mediaType) {
            return new ChatCompletionResult(true, Map.of(), eventStream, mediaType);
        }
    }

    public record ProxyForwardResult(
        int statusCode,
        byte[] content,
        Map<String, String> headers,
        String mediaType
    ) {
    }
}
