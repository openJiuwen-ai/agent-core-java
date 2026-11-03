/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Host-facing facade of the trajectory observability runtime.
 *
 * <p>Mirrors Python Agent Core {@code extensions/observability/setup.py}:
 * {@link #init} builds an SDK tracer provider whose span processors include
 * the trajectory {@link SpanRecordProcessor}, and {@link #shutdown} releases
 * it in reverse creation order. The runtime deliberately does not install
 * {@code GlobalOpenTelemetry}; hosts own the provider lifecycle.</p>
 *
 * @since 0.1.18
 */
public final class TrajectoryObservability {
    private static final Logger LOG = LoggerFactory.getLogger(TrajectoryObservability.class);

    private static final AttributeKey<String> SERVICE_NAME_KEY =
            AttributeKey.stringKey(OtlpJsonCodec.SERVICE_NAME_KEY);

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile TrajectoryObservability active;

    private final SdkTracerProvider tracerProvider;
    private final SpanRecordProcessor spanRecordProcessor;
    private final Tracer tracer;

    private TrajectoryObservability(TrajectoryObservabilityConfig config) {
        this.spanRecordProcessor = new SpanRecordProcessor(config.serviceName());
        this.tracerProvider = SdkTracerProvider.builder()
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(SERVICE_NAME_KEY, config.serviceName()))))
                .setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(config.sampleRate())))
                .addSpanProcessor(spanRecordProcessor)
                .build();
        this.tracer = tracerProvider.get(OtlpJsonCodec.SCOPE_NAME, OtlpJsonCodec.SCOPE_VERSION);
    }

    /**
     * Initialize the process-wide trajectory runtime.
     *
     * <p>Repeated enabled calls are idempotent; calling again after
     * {@link #shutdown} builds a fresh runtime.</p>
     *
     * @param config runtime configuration
     * @return the active runtime, or empty when the configuration is disabled
     */
    public static synchronized Optional<TrajectoryObservability> init(
            TrajectoryObservabilityConfig config) {
        if (config == null || !config.isEnabled()) {
            LOG.debug("trajectory observability disabled; skipping provider init");
            shutdown();
            return Optional.empty();
        }
        if (active != null && INITIALIZED.get()) {
            return Optional.of(active);
        }
        TrajectoryObservability created = new TrajectoryObservability(config);
        active = created;
        INITIALIZED.set(true);
        LOG.info("trajectory observability initialized (service={})", config.serviceName());
        return Optional.of(created);
    }

    /**
     * The active runtime.
     *
     * @return the active runtime
     * @throws IllegalStateException when the runtime is not initialized or disabled
     */
    public static TrajectoryObservability active() {
        TrajectoryObservability current = active;
        if (current == null || !INITIALIZED.get()) {
            throw new IllegalStateException(
                    "TrajectoryObservability is not initialized; enable trajectory_ui or"
                            + " agent_observability first");
        }
        return current;
    }

    /**
     * Whether an enabled runtime is currently initialized.
     *
     * @return true when span production is active
     */
    public static boolean isAvailable() {
        return INITIALIZED.get();
    }

    /**
     * Tracer for trajectory spans.
     *
     * @return the tracer
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * The trajectory span record processor wired into the provider.
     *
     * @return the processor
     */
    public SpanRecordProcessor getSpanRecordProcessor() {
        return spanRecordProcessor;
    }

    /**
     * Shut down the runtime, flushing and releasing the provider.
     */
    public static synchronized void shutdown() {
        TrajectoryObservability current = active;
        active = null;
        INITIALIZED.set(false);
        if (current == null) {
            return;
        }
        current.tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
    }
}
