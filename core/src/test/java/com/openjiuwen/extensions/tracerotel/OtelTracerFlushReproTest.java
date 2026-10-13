/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduction for the "last OTLP span is lost" defect in {@link OtelTracerSetup}.
 *
 * <p>Uses a minimal in-process OTLP/HTTP collector ({@code POST /v1/traces}) and counts export
 * requests. The control case proves the collector/exporter pipeline works; the repro case shows
 * that with a long batch window and no flush handle, ended spans stay buffered and are lost when
 * the JVM exits.</p>
 */
@DisplayName("tracerotel OTLP flush reproduction")
class OtelTracerFlushReproTest {

    private HttpServer server;
    private final AtomicInteger exportRequests = new AtomicInteger();
    private String endpoint;

    @BeforeEach
    void setUp() throws IOException {
        exportRequests.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", this::handleExport);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        OtelTracerSetup.shutdownOtelTracer();
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleExport(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        exportRequests.incrementAndGet();
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private OtelTracerConfig config(int scheduleDelayMillis) {
        return OtelTracerConfig.builder()
                .exporterType("otlp")
                .protocol("http")
                .exporterEndpoint(endpoint)
                .scheduleDelayMillis(scheduleDelayMillis)
                .exportTimeoutMs(2000)
                .build();
    }

    private boolean awaitExports(int expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (exportRequests.get() >= expected) {
                return true;
            }
            Thread.sleep(50);
        }
        return exportRequests.get() >= expected;
    }

    @Test
    @DisplayName("control: periodic flush delivers spans (collector/exporter pipeline works)")
    void periodicFlushDeliversSpan() throws InterruptedException {
        Tracer tracer = OtelTracerSetup.initOtelTracer(config(200));
        Span span = tracer.spanBuilder("periodic-span").startSpan();
        span.end();

        assertThat(awaitExports(1, 5000))
                .as("span should be exported by the periodic batch flush")
                .isTrue();
    }

    @Test
    @DisplayName("repro: without a flush handle the buffered otlp span is never exported")
    void bufferedSpanNotExportedWithoutFlush() throws InterruptedException {
        Tracer tracer = OtelTracerSetup.initOtelTracer(config(60000));
        Span span = tracer.spanBuilder("final-answer").startSpan();
        span.end();

        Thread.sleep(1000);
        assertThat(exportRequests.get())
                .as("no flush/shutdown handle exists, so the last span stays buffered and is lost on JVM exit")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("fix: shutdownOtelTracer flushes the buffered otlp span before exit")
    void shutdownOtelTracerDeliversBufferedSpan() throws InterruptedException {
        Tracer tracer = OtelTracerSetup.initOtelTracer(config(60000));
        Span span = tracer.spanBuilder("final-answer").startSpan();
        span.end();

        OtelTracerSetup.shutdownOtelTracer();

        assertThat(awaitExports(1, 5000))
                .as("span buffered in BatchSpanProcessor must be delivered after shutdown")
                .isTrue();
    }
}
