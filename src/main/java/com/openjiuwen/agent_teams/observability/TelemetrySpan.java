/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal local span abstraction used by translated observability handlers.
 *
 * <p>Mirrors Python's span operations used by {@code OtelCallbackHandler} in
 * {@code openjiuwen/agent_teams/observability/callback_handler.py}.</p>
 */
public class TelemetrySpan {

    public enum Kind {
        INTERNAL,
        CLIENT
    }

    public enum StatusCode {
        UNSET,
        OK,
        ERROR
    }

    public record Event(String name, Map<String, Object> attributes) {
        public Event {
            attributes = attributes == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
    }

    private final String name;
    private final Kind kind;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Throwable> exceptions = new ArrayList<>();
    private TelemetrySpan parent;
    private StatusCode statusCode = StatusCode.UNSET;
    private String statusDescription = "";
    private boolean ended;

    public TelemetrySpan(String name, Kind kind) {
        this(name, kind, null);
    }

    public TelemetrySpan(String name, Kind kind, TelemetrySpan parent) {
        this.name = name;
        this.kind = kind == null ? Kind.INTERNAL : kind;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public List<Event> getEvents() {
        return List.copyOf(events);
    }

    public List<Throwable> getExceptions() {
        return List.copyOf(exceptions);
    }

    public TelemetrySpan getParent() {
        return parent;
    }

    void setParent(TelemetrySpan parent) {
        this.parent = parent;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public boolean isEnded() {
        return ended;
    }

    public void setAttribute(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
    }

    public void addEvent(String name, Map<String, Object> eventAttributes) {
        events.add(new Event(name, eventAttributes));
    }

    public void recordException(Throwable throwable) {
        if (throwable != null) {
            exceptions.add(throwable);
        }
    }

    public void setStatus(StatusCode statusCode) {
        setStatus(statusCode, "");
    }

    public void setStatus(StatusCode statusCode, String description) {
        this.statusCode = statusCode == null ? StatusCode.UNSET : statusCode;
        this.statusDescription = description == null ? "" : description;
    }

    public void end() {
        ended = true;
    }
}
