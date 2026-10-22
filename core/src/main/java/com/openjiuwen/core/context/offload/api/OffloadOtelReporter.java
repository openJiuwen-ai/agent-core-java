/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenTelemetry bridge for {@link OffloadMetric} (架构文档 §7.8, T19/P2).
 *
 * <p>Off by default: {@code OffloadConfig.enableOtel=true} wires an instance
 * into {@link OffloadServiceImpl}. All otel calls are wrapped defensively —
 * observability must never break the offload path (zero-LLM/zero-failure
 * guarantee extends to the metric sink).</p>
 *
 * <p>Instruments (meter: {@code openjiuwen.context.offload}):
 * <ul>
 *   <li>{@code offload.operations} counter — attributes: event, algorithm,
 *       backend, success, failureReason;</li>
 *   <li>{@code offload.token.delta} counter — pre/post token bookkeeping
 *       summed per event;</li>
 *   <li>{@code offload.duration} counter (ms) — duration bookkeeping.</li>
 * </ul>
 * Histograms are avoided on purpose: the P0/P1 metric path relies on the
 * OpenTelemetry API only, and counter semantics keep the API dependency
 * minimal while remaining aggregatable on the collector side.</p>
 *
 * @since 2026-01-01
 */
public final class OffloadOtelReporter implements AutoCloseable {
    /** Instrumentation scope name for the offload metric family. */
    public static final String METER_NAME = "openjiuwen.context.offload";
    private static final Logger LOGGER = LoggerFactory.getLogger(OffloadOtelReporter.class);
    private static final AtomicBoolean GLOBAL = new AtomicBoolean(false);

    private final Meter meter;
    private final LongCounter operations;
    private final LongCounter tokenDelta;
    private final LongCounter durationMs;

    private OffloadOtelReporter(Meter meter) {
        this.meter = meter;
        this.operations = meter.counterBuilder("offload.operations")
                .setDescription("Context offload save/load/delete operations")
                .build();
        this.tokenDelta = meter.counterBuilder("offload.token.delta")
                .setDescription("Token counts observed by offload (pre + post tokens)")
                .build();
        this.durationMs = meter.counterBuilder("offload.duration")
                .setDescription("Offload operation duration in milliseconds")
                .setUnit("ms")
                .build();
    }

    /**
     * Creates a reporter when {@code isEnabled}; returns empty when
     * disabled or otel is unavailable so the caller can skip the reporter
     * entirely.
     *
     * @param isEnabled whether to create the reporter
     * @return a new reporter, or empty when disabled or otel is unavailable
     */
    public static Optional<OffloadOtelReporter> create(boolean isEnabled) {
        if (!isEnabled) {
            return Optional.empty();
        }
        try {
            OffloadOtelReporter reporter =
                    new OffloadOtelReporter(GlobalOpenTelemetry.get().getMeter(METER_NAME));
            GLOBAL.set(true);
            return Optional.of(reporter);
        } catch (RuntimeException ex) {
            // otel not initialized or broken: degrade to Slf4j-only silently.
            // Intentional: observability must never break the offload path.
            return Optional.empty();
        }
    }

    /**
     * Returns whether at least one reporter was created in this JVM.
     *
     * @return {@code true} when at least one reporter was created in this JVM
     */
    public static boolean isGlobalActive() {
        return GLOBAL.get();
    }

    /**
     * Records one metric observation.
     *
     * @param event  {@code save} / {@code load} / {@code delete}
     * @param metric structured observation from OffloadServiceImpl
     */
    public void record(String event, OffloadMetric metric) {
        if (event == null || metric == null) {
            return;
        }
        try {
            Attributes operationAttrs = Attributes.builder()
                    .put("event", event)
                    .put("algorithm", nullSafe(metric.algorithmId()))
                    .put("backend", metric.backend() == null ? "none" : metric.backend().name())
                    .put("success", String.valueOf(metric.isSuccess()))
                    .put("failure_reason", nullSafe(metric.failureReason()))
                    .build();
            operations.add(1L, operationAttrs);
            tokenDelta.add(Math.max(0L, metric.preTokenCount()), Attributes.builder()
                    .put("event", event).put("side", "pre").build());
            tokenDelta.add(Math.max(0L, metric.postTokenCount()), Attributes.builder()
                    .put("event", event).put("side", "post").build());
            durationMs.add(Math.max(0L, metric.durationMs()), Attributes.builder()
                    .put("event", event).build());
        } catch (RuntimeException ex) {
            // Intentional: log and swallow, metric sink failure must never affect the offload path.
            LOGGER.warn("offload otel metric record failed, event={}", event, ex);
        }
    }

    /**
     * Releases JVM-global state; safe to call multiple times.
     */
    @Override
    public void close() {
        GLOBAL.set(false);
    }

    /**
     * Exposes the underlying meter for tests.
     *
     * @return the meter instance
     */
    Meter meter() {
        return meter;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
