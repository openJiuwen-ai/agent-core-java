/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OTel tracer provider initialization.
 *
 * <p>Creates a {@code SdkTracerProvider} from {@link OtelTracerConfig} and returns an
 * OTel {@link Tracer} instance. Independent from {@code observability} — the resulting
 * provider is used solely by {@code tracerotel} extension handlers.</p>
 *
 * <p>Does NOT call {@code GlobalOpenTelemetry.setTracerProvider()} so that it never
 * conflicts with {@code agent_teams.observability.init_observability()} (which manages
 * the global TracerProvider). The returned tracer is bound directly to this module's
 * TracerProvider, so {@link OtelAgentHandler} / {@link OtelWorkflowHandler} work
 * correctly without relying on global state.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.setup}.</p>
 *
 * @since 0.1.7
 */
public final class OtelTracerSetup {
    private OtelTracerSetup() {
    }

    /**
     * Initialize an OTel TracerProvider and return a {@link Tracer} instance.
     *
     * <p>Supported {@code exporterType} values:</p>
     * <ul>
     *   <li>{@code "console"}: {@link LoggingSpanExporter} (debugging)</li>
     *   <li>{@code "otlp"}: OTLP span exporter (requires endpoint)</li>
     * </ul>
     * <p>Supported {@code protocol} values (for {@code otlp} exporter):</p>
     * <ul>
     *   <li>{@code "grpc"}: {@link OtlpGrpcSpanExporter}</li>
     *   <li>{@code "http"}: {@link OtlpHttpSpanExporter}</li>
     * </ul>
     *
     * @param config immutable configuration instance
     * @return an OTel {@link Tracer} ready for use in handlers
     * @throws BaseError when {@code exporterType} or {@code protocol} is invalid
     */
    public static Tracer initOtelTracer(OtelTracerConfig config) {
        Resource resource = Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), config.getServiceName(),
                AttributeKey.stringKey("service.version"),
                config.getServiceVersion() != null ? config.getServiceVersion() : "unknown"));

        Sampler sampler = Sampler.parentBased(Sampler.traceIdRatioBased(config.getSampleRate()));
        var providerBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler);

        if ("console".equals(config.getExporterType())) {
            SpanExporter exporter = new LoggingSpanExporter();
            providerBuilder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
        } else if ("otlp".equals(config.getExporterType())) {
            SpanExporter exporter = createOtlpExporter(config);
            providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(config.getScheduleDelayMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setMaxExportBatchSize(config.getMaxExportBatchSize())
                    .setExporterTimeout(config.getExportTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build());
        } else {
            throw new BaseError(StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                    "unknown exporter_type '" + config.getExporterType() + "', supported: console, otlp",
                    null, null);
        }

        SdkTracerProvider provider = providerBuilder.build();

        // Intentionally NOT calling GlobalOpenTelemetry.setTracerProvider() here.
        // tracerotel handlers hold a direct tracer reference, so global provider state
        // is not needed. This avoids conflicts with agent_teams.observability.init_observability().
        return provider.get(config.getTracerName());
    }

    /**
     * Create OTLP span exporter based on protocol and headers config.
     *
     * <p>{@code protocol="http"} → {@link OtlpHttpSpanExporter} (endpoint must end with
     * {@code /v1/traces}; appended automatically if missing).
     * {@code protocol="grpc"} → {@link OtlpGrpcSpanExporter}.</p>
     *
     * @param config immutable configuration instance
     * @return the OTLP span exporter
     * @throws BaseError when {@code protocol} is invalid
     */
    public static SpanExporter createOtlpExporter(OtelTracerConfig config) {
        String endpoint = config.getExporterEndpoint();
        Map<String, String> headers = config.getHeaders() != null
                ? new LinkedHashMap<>(config.getHeaders())
                : new LinkedHashMap<>();

        if ("http".equals(config.getProtocol())) {
            String httpEndpoint = endpoint;
            // HTTP exporter expects endpoint to include path: /v1/traces
            if (httpEndpoint != null && !httpEndpoint.isEmpty() && !httpEndpoint.endsWith("/v1/traces")) {
                httpEndpoint = httpEndpoint + "/v1/traces";
            }
            var builder = OtlpHttpSpanExporter.builder();
            if (httpEndpoint != null && !httpEndpoint.isEmpty()) {
                builder.setEndpoint(httpEndpoint);
            }
            if (!headers.isEmpty()) {
                builder.setHeaders(() -> headers);
            }
            return builder.build();
        } else if ("grpc".equals(config.getProtocol())) {
            var builder = OtlpGrpcSpanExporter.builder();
            if (endpoint != null && !endpoint.isEmpty()) {
                builder.setEndpoint(endpoint);
            }
            if (!headers.isEmpty()) {
                builder.setHeaders(() -> headers);
            }
            return builder.build();
        } else {
            throw new BaseError(StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR,
                    "unknown otlp protocol '" + config.getProtocol() + "', supported: grpc, http",
                    null, null);
        }
    }
}
