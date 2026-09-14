/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

/**
 * Shared OTel test infrastructure: {@link InMemorySpanExporter} + {@link Tracer}.
 *
 * <p>Creates a private {@link SdkTracerProvider} with an {@link InMemorySpanExporter}
 * for unit tests to inspect spans in-process.</p>
 *
 * <p>Does NOT call {@code GlobalOpenTelemetry.setTracerProvider()} — that API is
 * one-shot per process and conflicts with
 * {@code agent_teams.observability.init_observability()}. Instead the tracer is
 * obtained directly via {@code PROVIDER.getTracer()} which binds to this module's
 * processor chain, so all spans flow to {@code EXPORTER} without relying on
 * global provider state. This mirrors the design of {@link OtelTracerSetup#initOtelTracer}.</p>
 *
 * <p>Mirrors Python's {@code tests.conftest_otel}.</p>
 */
public final class ConftestOtel {

    /** Module-level singleton — one exporter per process. */
    public static final InMemorySpanExporter EXPORTER = InMemorySpanExporter.create();

    /** Module-level singleton — one provider per process. */
    private static final SdkTracerProvider PROVIDER = SdkTracerProvider.builder()
            .setResource(Resource.create(Attributes.of(
                    AttributeKey.stringKey("service.name"), "openjiuwen")))
            .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
            .build();

    /** Tracer bound to PROVIDER — spans flow to EXPORTER without global state. */
    public static final Tracer OTEL_TRACER = PROVIDER.get("openjiuwen.tracer.otel.test");

    private ConftestOtel() {
    }

    /** Clear all finished spans from the in-memory exporter. */
    public static void clearExporter() {
        EXPORTER.reset();
    }

    /**
     * Check if Jaeger is reachable at localhost:16686.
     *
     * @return {@code true} if Jaeger is available
     */
    public static boolean jaegerIsAvailable() {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("http://localhost:16686/api/services").openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
