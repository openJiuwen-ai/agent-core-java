/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Acceptance test for the export path that {@code ObservabilitySetup} controls.
 *
 * <p>Black box on purpose: it drives only the public API
 * ({@code initObservability} / {@code startTeamTrace} / {@code finalizeTeamTrace}) against a real
 * OTLP/HTTP receiver, and never inspects private static state or the provider directly. The
 * acceptance criterion is the user-visible outcome — once {@code finalizeTeamTrace} returns, the
 * team span must already be in the receiver's hands.</p>
 *
 * @since 0.1.17
 */
@DisplayName("acceptance: observability export delivery")
class ObservabilityFlushAcceptanceTest {
    private static final String TEAM_NAME = "acceptance_team";

    private static final String SESSION_ID = "acceptance-session";

    private static final int RECEIVER_BACKLOG = 8;

    /** Export timeout used when a real receiver is present; also the bound handed to the setup. */
    private static final int DELIVERY_BOUND_MILLIS = 5_000;

    /** Export timeout used when the collector stalls; the setup must not exceed it by much. */
    private static final int STALL_BOUND_MILLIS = 2_000;

    /** Upper bound for a bounded call whose export timeout is STALL_BOUND_MILLIS. */
    private static final long STALLED_CALL_LIMIT_MILLIS = 5_000L;

    @AfterEach
    void shutdownAfterEach() {
        ObservabilitySetup.shutdownObservability();
    }

    @Test
    @DisplayName("finalizeTeamTrace has delivered the team span by the time it returns")
    void testFinalizeTeamTraceDeliversSpan() throws IOException {
        List<byte[]> receivedBodies = new CopyOnWriteArrayList<>();
        HttpServer receiver = startReceiver(receivedBodies);
        try {
            initAgainst("http://127.0.0.1:" + receiver.getAddress().getPort(), DELIVERY_BOUND_MILLIS);
            ObservabilitySetup.startTeamTrace(TEAM_NAME, SESSION_ID);

            ObservabilitySetup.finalizeTeamTrace(TEAM_NAME);

            assertThat(receivedTeamSpan(receivedBodies, TEAM_NAME))
                    .as("receiver must already hold the team span when finalizeTeamTrace returns")
                    .isTrue();
        } finally {
            receiver.stop(0);
        }
    }

    @Test
    @DisplayName("finalizeTeamTrace returns within its bound when the collector never responds")
    void testFinalizeTeamTraceIsBoundedWhenCollectorStalls() throws IOException {
        ServerSocket stalledCollector = new ServerSocket(0);
        try {
            initAgainst("http://127.0.0.1:" + stalledCollector.getLocalPort(), STALL_BOUND_MILLIS);
            ObservabilitySetup.startTeamTrace(TEAM_NAME, SESSION_ID);

            long startNanos = System.nanoTime();
            ObservabilitySetup.finalizeTeamTrace(TEAM_NAME);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertThat(elapsedMillis)
                    .as("finalizeTeamTrace must not block past its bound when the collector stalls")
                    .isLessThan(STALLED_CALL_LIMIT_MILLIS);
        } finally {
            stalledCollector.close();
        }
    }

    private static void initAgainst(String endpoint, int exportTimeoutMs) {
        ObservabilitySetup.initObservability(ObservabilityConfig.builder()
                .isEnabled(true)
                .exporter("otlp_http")
                .endpoint(endpoint)
                .serviceName("otel-acceptance")
                .sampleRate(1.0)
                .exportTimeoutMs(exportTimeoutMs)
                .build());
    }

    private static HttpServer startReceiver(List<byte[]> receivedBodies) throws IOException {
        HttpServer receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), RECEIVER_BACKLOG);
        receiver.createContext("/v1/traces", exchange -> {
            receivedBodies.add(exchange.getRequestBody().readAllBytes());
            // 返回合法 OTLP/HTTP 成功响应，避免导出器重试退避
            exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        receiver.start();
        return receiver;
    }

    /**
     * Check whether any received OTLP payload names the team span.
     *
     * <p>Protobuf encodes strings as length-prefixed UTF-8, so the span name is searched for in the
     * decoded payload rather than parsed out of it.</p>
     *
     * @param receivedBodies raw request bodies captured by the receiver
     * @param teamName the team name used for the span name {@code team.<name>}
     * @return {@code true} when the team span name was seen
     * @throws IOException if a gzip encoded body cannot be decoded
     */
    private static boolean receivedTeamSpan(List<byte[]> receivedBodies, String teamName) throws IOException {
        String spanName = "team." + teamName;
        for (byte[] body : receivedBodies) {
            if (new String(decodeIfGzipped(body), StandardCharsets.ISO_8859_1).contains(spanName)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] decodeIfGzipped(byte[] body) throws IOException {
        boolean isGzipped = body.length > 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B;
        if (!isGzipped) {
            return body;
        }
        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return gzip.readAllBytes();
        }
    }
}
