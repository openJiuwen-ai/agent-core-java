/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
