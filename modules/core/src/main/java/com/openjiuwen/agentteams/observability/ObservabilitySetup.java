/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import com.openjiuwen.agentteams.agent.TeamAgent;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TracerProvider lifecycle and initialization for the agent_teams observability module.
 *
 * <p>Public entry points:</p>
 * <ul>
 *   <li>{@link #initObservability(ObservabilityConfig)} — stand up the
 *       {@code SdkTracerProvider}, build the configured exporter, and set
 *       the global {@link OpenTelemetry} instance.</li>
 *   <li>{@link #shutdownObservability()} — flush spans, shut down the
 *       provider, and reset module state. Tests rely on this to keep
 *       cases isolated.</li>
 *   <li>{@link #finalizeTeamTrace(String)} — close all spans for a
 *       specific team when the runner exits.</li>
 *   <li>{@link #getTracer(String)} — return a {@link Tracer} bound to
 *       the active provider.</li>
 *   <li>{@link #getConfig()} — return the active
 *       {@link ObservabilityConfig}, or empty if not initialized.</li>
 * </ul>
 *
 * <p>Like the {@code tracerotel} extension's {@code OtelTracerSetup},
 * this class holds a direct {@link SdkTracerProvider} reference rather
 * than setting the global {@code GlobalOpenTelemetry} provider. This
 * avoids conflicts if both modules are active simultaneously.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.setup}.</p>
 *
 * @since 0.1.7
 */
public final class ObservabilitySetup {
    private static final Logger LOG = LoggerFactory.getLogger(ObservabilitySetup.class);

    private static final String CALLBACK_TRACER_NAME = "openjiuwen.agent_teams.observability";
    private static final String MONITOR_TRACER_NAME = "openjiuwen.agent_teams.observability.monitor";

    private static SdkTracerProvider provider;
    private static ObservabilityConfig config;
    private static OtelTeamMonitorHandler monitorHandler;
    private static volatile boolean isInitialized;

    private ObservabilitySetup() {
    }

    /**
     * Initialize the TracerProvider and set the global OpenTelemetry instance.
     *
     * <p>When {@code config.enabled} is {@code false}, this is a no-op.
     * Calling this method more than once without an intervening
     * {@link #shutdownObservability()} logs a warning and returns.</p>
     *
     * @param observabilityConfig the effective configuration
     * @since 0.1.7
     */
    public static synchronized void initObservability(ObservabilityConfig observabilityConfig) {
        if (observabilityConfig == null || !observabilityConfig.isEnabled()) {
            LOG.info("observability disabled by config");
            return;
        }
        if (provider != null) {
            LOG.warn("observability already initialized; skipping re-init");
            return;
        }

        config = observabilityConfig;

        Resource resource = Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), config.getServiceName()));

        Sampler sampler = Sampler.parentBased(
                Sampler.traceIdRatioBased(config.getSampleRate()));

        var providerBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler);

        SpanExporter exporter = buildExporter(config);

        // Console exporter writes synchronously per-span, so keep it on the
        // Simple processor for immediate output. The file exporter appends
        // straight to disk — put it on BatchSpanProcessor so span-end does
        // not block the business thread. OTLP exporters also batch.
        if ("console".equals(config.getExporter())) {
            providerBuilder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
        } else {
            providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter)
                    .setExporterTimeout(config.getExportTimeoutMs(),
                            java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build());
        }

        provider = providerBuilder.build();

        monitorHandler = new OtelTeamMonitorHandler(config, provider.get(MONITOR_TRACER_NAME));

        isInitialized = true;
        LOG.info("otel: observability initialized, exporter={}, service={}",
                config.getExporter(), config.getServiceName());
    }

    /**
     * Close all spans for a specific team when the runner exits.
     *
     * @param teamName the team name; if blank, this is a no-op
     * @since 0.1.7
     */
    public static void finalizeTeamTrace(String teamName) {
        if (teamName == null || teamName.isEmpty()) {
            return;
        }

        LOG.info("otel: finalize_team_trace for team={}", teamName);
        OtelSpanContext.finalizeTrace(teamName);
        forceFlushProvider(config != null ? config.getExportTimeoutMs() : 5000);
    }

    /**
     * Force flush the TracerProvider to ensure spans are exported.
     *
     * @param timeoutMillis flush timeout in milliseconds
     * @since 0.1.7
     */
    public static void forceFlushProvider(int timeoutMillis) {
        if (provider != null) {
            try {
                provider.forceFlush();
            } catch (IllegalStateException | SecurityException e) {
                LOG.warn("otel: force_flush failed - {}", e.getMessage());
            }
        }
    }

    /**
     * Flush, shut down the provider, and reset module state.
     *
     * <p>After this call, {@link #isInitialized()} returns {@code false}
     * and {@link #initObservability(ObservabilityConfig)} can be called
     * again.</p>
     *
     * @since 0.1.7
     */
    public static synchronized void shutdownObservability() {
        if (provider != null) {
            try {
                provider.forceFlush();
            } catch (IllegalStateException | SecurityException e) {
                LOG.warn("otel: provider force_flush failed - {}", e.getMessage());
            }
            try {
                provider.shutdown();
            } catch (IllegalStateException | SecurityException e) {
                LOG.warn("otel: provider shutdown failed - {}", e.getMessage());
            }
            provider = null;
        }

        if (monitorHandler != null) {
            monitorHandler.closeAllSpans();
            monitorHandler = null;
        }

        config = null;
        isInitialized = false;
        OtelSpanContext.resetAll();
        LOG.info("otel: observability shut down");
    }

    /**
     * Return a {@link Tracer} bound to the active observability provider.
     *
     * <p>If the provider has not been initialized, falls back to the
     * global {@link GlobalOpenTelemetry#getTracer(String)}.</p>
     *
     * @param name the tracer instrumentation name
     * @return an {@link Optional} containing the {@link Tracer}, or empty if unavailable
     * @since 0.1.7
     */
    public static Optional<Tracer> getTracer(String name) {
        if (provider != null) {
            return Optional.of(provider.get(name));
        }
        try {
            return Optional.of(GlobalOpenTelemetry.getTracer(name));
        } catch (IllegalStateException e) {
            LOG.debug("otel: GlobalOpenTelemetry not initialized, returning empty");
            return Optional.empty();
        }
    }

    /**
     * Return the callback tracer for LLM/tool/agent spans.
     *
     * @return an {@link Optional} containing the callback tracer, or empty if unavailable
     * @since 0.1.7
     */
    public static Optional<Tracer> getCallbackTracer() {
        return getTracer(CALLBACK_TRACER_NAME);
    }

    /**
     * Return the monitor tracer for team event spans.
     *
     * @return an {@link Optional} containing the monitor tracer, or empty if unavailable
     * @since 0.1.7
     */
    public static Optional<Tracer> getMonitorTracer() {
        return getTracer(MONITOR_TRACER_NAME);
    }

    /**
     * Return the active {@link ObservabilityConfig}.
     *
     * @return an {@link Optional} containing the config, or empty if not initialized
     * @since 0.1.7
     */
    public static Optional<ObservabilityConfig> getConfig() {
        return Optional.ofNullable(config);
    }

    /**
     * Return {@code true} if {@link #initObservability} has been called and not yet shut down.
     *
     * @return {@code true} if initialized
     * @since 0.1.7
     */
    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Return the active monitor handler.
     *
     * @return an {@link Optional} containing the monitor handler, or empty if not initialized
     * @since 0.1.7
     */
    public static Optional<OtelTeamMonitorHandler> getMonitorHandler() {
        return Optional.ofNullable(monitorHandler);
    }

    /**
     * Register the monitor handler on a leader {@code TeamAgent}.
     *
     * <p>Idempotent — silently skips when the handler is already registered
     * or when observability has not been initialized.</p>
     *
     * @param teamAgent the team agent to attach to; must be a {@link TeamAgent}
     * @since 0.1.7
     */
    public static void attachToTeamAgent(Object teamAgent) {
        if (monitorHandler == null) {
            LOG.warn("attachToTeamAgent called before initObservability");
            return;
        }
        if (teamAgent instanceof TeamAgent team) {
            team.addEventListener(monitorHandler);
        } else {
            LOG.warn("attachToTeamAgent: teamAgent is not a TeamAgent (type={})",
                    teamAgent != null ? teamAgent.getClass().getName() : "null");
        }
    }

    /**
     * Create a team span and store it in the ThreadLocal context.
     *
     * <p>This should be called when a team trace begins (e.g. in the
     * runner's {@code run()} method before agent invocation).</p>
     *
     * @param teamName  the team name
     * @param sessionId the session ID (may be {@code null})
     * @since 0.1.7
     */
    public static void startTeamTrace(String teamName, String sessionId) {
        Optional<Tracer> tracerOpt = getTracer(CALLBACK_TRACER_NAME);
        if (tracerOpt.isEmpty()) {
            LOG.warn("otel: cannot start team trace for team={} - no tracer available", teamName);
            return;
        }
        Tracer tracer = tracerOpt.get();

        Span teamSpan = tracer.spanBuilder("team." + teamName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        teamSpan.setAttribute(ObservabilitySemConv.AT_TEAM_NAME, teamName);
        teamSpan.setAttribute(ObservabilitySemConv.AT_TEAM_ID, teamName);
        teamSpan.setAttribute(ObservabilitySemConv.LANGFUSE_TRACE_NAME, "team." + teamName);
        teamSpan.setAttribute(ObservabilitySemConv.LANGFUSE_OBSERVATION_TYPE, "team");

        if (sessionId != null && !sessionId.isEmpty()) {
            teamSpan.setAttribute(ObservabilitySemConv.LANGFUSE_SESSION_ID, sessionId);
            teamSpan.setAttribute(ObservabilitySemConv.AT_SESSION_ID, sessionId);
            OtelSpanContext.setSessionId(sessionId);
        }

        OtelSpanContext.setTeamSpan(teamSpan);
        OtelSpanContext.setTeamName(teamName);
        LOG.debug("otel: started team trace for team={}, sessionId={}", teamName, sessionId);
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    /**
     * Construct the exporter selected by the configuration.
     *
     * @param config the observability configuration
     * @return the span exporter
     * @throws IllegalArgumentException when {@code config.exporter} is unsupported
     * @since 0.1.7
     */
    private static SpanExporter buildExporter(ObservabilityConfig config) {
        String exporterType = config.getExporter();
        if ("console".equals(exporterType)) {
            return new LoggingSpanExporter();
        }
        if ("file".equals(exporterType)) {
            return new TraceFileExporter(config.getTracesDir(), config.getFileRetentionDays());
        }
        if ("otlp_grpc".equals(exporterType)) {
            Map<String, String> headers = buildAuthHeaders(config);
            var builder = OtlpGrpcSpanExporter.builder();
            if (config.getEndpoint() != null && !config.getEndpoint().isEmpty()) {
                builder.setEndpoint(config.getEndpoint());
            }
            if (!headers.isEmpty()) {
                builder.setHeaders(() -> headers);
            }
            return builder.build();
        }
        if ("otlp_http".equals(exporterType)) {
            Map<String, String> headers = buildAuthHeaders(config);
            String endpoint = config.getEndpoint();
            if (endpoint != null && !endpoint.isEmpty() && !endpoint.endsWith("/v1/traces")) {
                endpoint = endpoint + "/v1/traces";
            }
            var builder = OtlpHttpSpanExporter.builder();
            if (endpoint != null && !endpoint.isEmpty()) {
                builder.setEndpoint(endpoint);
            }
            if (!headers.isEmpty()) {
                builder.setHeaders(() -> headers);
            }
            return builder.build();
        }
        throw new IllegalArgumentException(
                "unsupported observability exporter: " + exporterType);
    }

    /**
     * Build authentication headers for OTLP export (Langfuse Basic auth).
     *
     * @param config the observability configuration
     * @return a map of headers, possibly empty
     * @since 0.1.7
     */
    private static Map<String, String> buildAuthHeaders(ObservabilityConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.getLangfusePublicKey() != null && !config.getLangfusePublicKey().isEmpty()
                && config.getLangfuseSecretKey() != null && !config.getLangfuseSecretKey().isEmpty()) {
            String credentials = config.getLangfusePublicKey() + ":" + config.getLangfuseSecretKey();
            String encoded = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
            headers.put("authorization", "Basic " + encoded);
        }
        return headers;
    }
}
