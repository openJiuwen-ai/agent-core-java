/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import io.opentelemetry.api.trace.Tracer;

/**
 * Exit-flush probe for {@link OtelTracerSetup}.
 *
 * <p>Initializes the {@code tracerotel} OTLP/HTTP exporter, ends one span and lets the main thread
 * return immediately, so the JVM exits while the span is still buffered in the
 * {@code BatchSpanProcessor}. {@code BatchSpanProcessor} exports from a daemon worker thread, so a
 * buffered span is lost on JVM exit unless the provider is flushed by a shutdown hook.</p>
 *
 * <p>Started as a child JVM by {@code OtelExitFlushSubprocessTest}; the periodic export interval is
 * set beyond the probe lifetime so that only exit-time flushing can deliver the span.</p>
 *
 * @since 0.1.17
 */
public final class OtelExitFlushProbe {
    /** Export interval, longer than the probe JVM lifetime so the periodic timer cannot fire. */
    private static final int SCHEDULE_DELAY_MILLIS = 60_000;

    private static final int EXPORT_TIMEOUT_MILLIS = 20_000;

    private OtelExitFlushProbe() {
    }

    /**
     * Entry point: buffer one span, then exit without flushing explicitly.
     *
     * @param args first argument is the OTLP/HTTP collector endpoint
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: OtelExitFlushProbe <otlp-http-endpoint>");
        }

        OtelTracerConfig config = OtelTracerConfig.builder()
                .exporterType("otlp")
                .exporterEndpoint(args[0])
                .protocol("http")
                .scheduleDelayMillis(SCHEDULE_DELAY_MILLIS)
                .exportTimeoutMs(EXPORT_TIMEOUT_MILLIS)
                .build();
        Tracer tracer = OtelTracerSetup.initOtelTracer(config);
        tracer.spanBuilder("exit_flush_probe_span").startSpan().end();
        // 该标记是给父测试 OtelExitFlushSubprocessTest 读子进程标准输出的通信约定，不走日志框架
        System.out.println("PROBE_SPAN_ENDED");
    }
}
