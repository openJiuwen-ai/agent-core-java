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
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <p>Retains the built provider so buffered spans can still be exported when the process exits:
 * {@link #forceFlushOtelTracer()} and {@link #shutdownOtelTracer()} wait (bounded) for the export
 * to complete, and {@link #initOtelTracer(OtelTracerConfig)} registers a one-time JVM shutdown
 * hook that flushes and shuts the provider down. Without such a flush handle, spans still
 * buffered in a {@code BatchSpanProcessor} are lost at JVM exit.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.setup}.</p>
 *
 * @since 0.1.7
 */
public final class OtelTracerSetup {
    /** Upper bound (seconds) for one flush/shutdown wait, so JVM exit cannot hang forever. */
    private static final long FLUSH_TIMEOUT_SECONDS = 30L;

    private static final Logger LOG = LoggerFactory.getLogger(OtelTracerSetup.class);

    /** Guards the one-time registration of the JVM shutdown hook. */
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    /** Provider built by the latest {@link #initOtelTracer(OtelTracerConfig)} call; cleared on shutdown. */
    private static volatile SdkTracerProvider provider;

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

        SdkTracerProvider builtProvider = providerBuilder.build();
        provider = builtProvider;
        registerShutdownHook();

        // Intentionally NOT calling GlobalOpenTelemetry.setTracerProvider() here.
        // tracerotel handlers hold a direct tracer reference, so global provider state
        // is not needed. This avoids conflicts with agent_teams.observability.init_observability().
        return builtProvider.get(config.getTracerName());
    }

    /**
     * Flush spans still buffered by the provider built in {@link #initOtelTracer(OtelTracerConfig)}.
     *
     * <p>Waits until the flush finishes or {@link #FLUSH_TIMEOUT_SECONDS} elapses, so a caller
     * (including the JVM shutdown hook) cannot block forever when the collector is unreachable.</p>
     *
     * <p>{@code BatchSpanProcessor} completes its flush result once the buffer is drained and does
     * not report per-export failures, so {@code false} means "did not finish in time"; export
     * failures are logged by the exporter itself.</p>
     *
     * @return {@code true} when the flush finished, or when no provider exists
     * @since 0.1.17
     */
    public static boolean forceFlushOtelTracer() {
        SdkTracerProvider current = provider;
        if (current == null) {
            return true;
        }
        try {
            return awaitExport(current.forceFlush(), "force flush");
        } catch (IllegalStateException | SecurityException e) {
            LOG.warn("otel: tracerotel force flush failed - {}", e.getMessage());
            return false;
        }
    }

    /**
     * Flush and shut down the provider built in {@link #initOtelTracer(OtelTracerConfig)}.
     *
     * <p>{@code SdkTracerProvider.shutdown()} already flushes every {@code SpanProcessor}, so no
     * separate {@code forceFlush()} is issued. The provider reference is cleared before waiting, so
     * concurrent and repeated calls return immediately instead of shutting down twice.</p>
     *
     * <p>{@code BatchSpanProcessor} reports success once its worker stops, so {@code false} means
     * "did not finish in time"; per-export failures are logged by the exporter itself.</p>
     *
     * @return {@code true} when the provider shutdown finished, or when no provider exists
     * @since 0.1.17
     */
    public static synchronized boolean shutdownOtelTracer() {
        SdkTracerProvider current = provider;
        if (current == null) {
            return true;
        }
        provider = null;
        try {
            return awaitExport(current.shutdown(), "shutdown");
        } catch (IllegalStateException | SecurityException e) {
            LOG.warn("otel: tracerotel shutdown failed - {}", e.getMessage());
            return false;
        }
    }

    /**
     * Register the one-time JVM shutdown hook that flushes and shuts the provider down.
     *
     * @since 0.1.17
     */
    private static void registerShutdownHook() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(OtelTracerSetup::shutdownOtelTracer, "otel-tracer-shutdown"));
        }
    }

    /**
     * Wait for an export result with the bounded flush timeout.
     *
     * @param resultCode export result returned by the provider
     * @param operation operation name used in the warning message
     * @return {@code true} when the export finished successfully
     * @since 0.1.17
     */
    private static boolean awaitExport(CompletableResultCode resultCode, String operation) {
        if (resultCode.join(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS).isSuccess()) {
            return true;
        }
        LOG.warn("otel: tracerotel {} did not finish within {}s (collector unreachable?), failure={}",
                operation, FLUSH_TIMEOUT_SECONDS, describeFailure(resultCode));
        return false;
    }

    /**
     * Describe an export failure for logging.
     *
     * @param resultCode export result returned by the provider
     * @return the failure message, or {@code "unknown"} when the export timed out without a cause
     * @since 0.1.17
     */
    private static String describeFailure(CompletableResultCode resultCode) {
        Throwable failure = resultCode.getFailureThrowable();
        return failure == null ? "unknown" : failure.getMessage();
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
