/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Minimal tracer abstraction for translated observability setup.
 *
 * <p>Mirrors Python's injected tracer surface used by {@code OtelCallbackHandler} in
 * {@code openjiuwen/agent_teams/observability/callback_handler.py}.</p>
 */
@FunctionalInterface
public interface TelemetryTracer {

    TelemetrySpan startSpan(String name, TelemetrySpan.Kind kind);

    /**
     * In-memory tracer used by tests and by the no-external-dependency default provider.
     *
     * <p>Mirrors Python's in-memory exporter override behavior exercised through
     * {@code openjiuwen/agent_teams/observability/callback_handler.py}.</p>
     */
    final class InMemory implements TelemetryTracer {
        private final List<TelemetrySpan> spans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public TelemetrySpan startSpan(String name, TelemetrySpan.Kind kind) {
            TelemetrySpan span = new TelemetrySpan(name, kind);
            spans.add(span);
            return span;
        }

        public List<TelemetrySpan> getSpans() {
            synchronized (spans) {
                return new ArrayList<>(spans);
            }
        }
    }

    /**
     * Tracer that appends ended spans to a {@link TraceFileExporter}.
     *
     * <p>Mirrors Python's {@code BatchSpanProcessor}+{@code TraceFileExporter}
     * pairing in {@code openjiuwen/agent_teams/observability/setup.py}.</p>
     *
     * @since 0.1.14
     */
    final class FileBacked implements TelemetryTracer {
        private final TraceFileExporter exporter;
        private final InMemory memory = new InMemory();

        FileBacked(TraceFileExporter exporter) {
            this.exporter = Objects.requireNonNull(exporter, "exporter");
        }

        @Override
        public TelemetrySpan startSpan(String name, TelemetrySpan.Kind kind) {
            TelemetrySpan span = memory.startSpan(name, kind);
            return new ExportOnEndSpan(span, exporter);
        }

        public TraceFileExporter getExporter() {
            return exporter;
        }

        public List<TelemetrySpan> getSpans() {
            return memory.getSpans();
        }
    }
}
