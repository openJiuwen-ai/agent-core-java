/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Span wrapper that exports to a {@link TraceFileExporter} on {@link #end()}.
 *
 * @since 0.1.14
 */
final class ExportOnEndSpan extends TelemetrySpan {

    private final TelemetrySpan delegate;
    private final TraceFileExporter exporter;

    ExportOnEndSpan(TelemetrySpan delegate, TraceFileExporter exporter) {
        super(delegate.getName(), delegate.getKind(), delegate.getParent());
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public List<Event> getEvents() {
        return delegate.getEvents();
    }

    @Override
    public List<Throwable> getExceptions() {
        return delegate.getExceptions();
    }

    @Override
    public TelemetrySpan getParent() {
        return delegate.getParent();
    }

    @Override
    public StatusCode getStatusCode() {
        return delegate.getStatusCode();
    }

    @Override
    public String getStatusDescription() {
        return delegate.getStatusDescription();
    }

    @Override
    public boolean isEnded() {
        return delegate.isEnded();
    }

    @Override
    public void setAttribute(String key, Object value) {
        delegate.setAttribute(key, value);
    }

    @Override
    public void addEvent(String name, Map<String, Object> eventAttributes) {
        delegate.addEvent(name, eventAttributes);
    }

    @Override
    public void recordException(Throwable throwable) {
        delegate.recordException(throwable);
    }

    @Override
    public void setStatus(StatusCode statusCode) {
        delegate.setStatus(statusCode);
    }

    @Override
    public void setStatus(StatusCode statusCode, String description) {
        delegate.setStatus(statusCode, description);
    }

    @Override
    public void end() {
        if (delegate.isEnded()) {
            return;
        }
        delegate.end();
        exporter.export(List.of(delegate));
    }
}
