/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression test for {@link ObservabilitySetup} flush and shutdown.
 *
 * <p>{@code SdkTracerProvider.forceFlush()} returns a {@link CompletableResultCode} that the batch
 * worker completes <em>after</em> it has exported the pending batch, and it does not block the
 * caller by itself. {@link ObservabilitySetup#forceFlushProvider(int)} and
 * {@link ObservabilitySetup#shutdownObservability()} must therefore await that result, so that by
 * the time they return the buffered spans have really been handed to the exporter.</p>
 *
 * <p>Each test uses a slow (but succeeding) exporter and asserts delivery at return time. When the
 * result is not awaited, the call returns while the span is still buffered and the assertions
 * fail — that is the reproduction.</p>
 *
 * @since 0.1.17
 */
@DisplayName("observability flush awaits export")
class ObservabilityFlushTimeoutTest {
    /** Exporter latency; long enough that a non-awaiting caller returns well before delivery. */
    private static final int EXPORT_DELAY_MILLIS = 800;

    /** Bound handed to the setup; comfortably above EXPORT_DELAY_MILLIS. */
    private static final int FLUSH_TIMEOUT_MILLIS = 10_000;

    @AfterEach
    void resetSetupState() {
        setStaticField("provider", null);
        setStaticField("config", null);
        setStaticField("isInitialized", Boolean.FALSE);
    }

    @Test
    @DisplayName("forceFlushProvider has delivered the buffered span by the time it returns")
    void testForceFlushProviderAwaitsExport() {
        SlowExporter exporter = new SlowExporter(EXPORT_DELAY_MILLIS);
        SdkTracerProvider provider = newSlowProvider(exporter);
        setStaticField("provider", provider);
        endOneSpan(provider);
        try {
            long startNanos = System.nanoTime();
            ObservabilitySetup.forceFlushProvider(FLUSH_TIMEOUT_MILLIS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertThat(exporter.exportedBatches())
                    .as("forceFlushProvider returned while the span was still buffered")
                    .isGreaterThanOrEqualTo(1);
            assertThat(elapsedMillis)
                    .as("forceFlushProvider must wait for the export, not return immediately")
                    .isGreaterThanOrEqualTo(EXPORT_DELAY_MILLIS);
        } finally {
            provider.shutdown().join(FLUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    @DisplayName("shutdownObservability has delivered the buffered span by the time it returns")
    void testShutdownObservabilityAwaitsExport() {
        SlowExporter exporter = new SlowExporter(EXPORT_DELAY_MILLIS);
        SdkTracerProvider provider = newSlowProvider(exporter);
        setStaticField("provider", provider);
        setStaticField("config", ObservabilityConfig.builder()
                .isEnabled(true)
                .serviceName("flush-timeout-test")
                .exportTimeoutMs(FLUSH_TIMEOUT_MILLIS)
                .build());
        endOneSpan(provider);
        try {
            long startNanos = System.nanoTime();
            ObservabilitySetup.shutdownObservability();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertThat(exporter.exportedBatches())
                    .as("shutdownObservability returned while the span was still buffered")
                    .isGreaterThanOrEqualTo(1);
            assertThat(elapsedMillis)
                    .as("shutdownObservability must wait for the export, not return immediately")
                    .isGreaterThanOrEqualTo(EXPORT_DELAY_MILLIS);
        } finally {
            provider.shutdown().join(FLUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static SdkTracerProvider newSlowProvider(SpanExporter exporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter)
                        .setExporterTimeout(FLUSH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        .build())
                .build();
    }

    private static void endOneSpan(SdkTracerProvider provider) {
        Tracer tracer = provider.get("observability-flush-timeout-test");
        tracer.spanBuilder("slow_flush_span").startSpan().end();
    }

    /**
     * Replace a private static field on {@link ObservabilitySetup} for one test.
     *
     * <p>Reflection failures are surfaced as an unchecked exception so that test methods do not have
     * to declare {@link ReflectiveOperationException}.</p>
     *
     * @param name  the field name
     * @param value the value to assign
     */
    private static void setStaticField(String name, Object value) {
        try {
            Field field = ObservabilitySetup.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set ObservabilitySetup." + name, e);
        }
    }

    /**
     * Exporter that takes a fixed time per batch, counting the batches it actually exported.
     *
     * <p>A batch is counted only after its export delay has elapsed, so "counted" is equivalent to
     * "delivered" for the assertions above.</p>
     */
    private static final class SlowExporter implements SpanExporter {
        private final long exportDelayMillis;
        private final AtomicInteger exportedBatches = new AtomicInteger();

        SlowExporter(long exportDelayMillis) {
            this.exportDelayMillis = exportDelayMillis;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            try {
                Thread.sleep(exportDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableResultCode.ofFailure();
            }
            exportedBatches.incrementAndGet();
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        int exportedBatches() {
            return exportedBatches.get();
        }
    }
}
