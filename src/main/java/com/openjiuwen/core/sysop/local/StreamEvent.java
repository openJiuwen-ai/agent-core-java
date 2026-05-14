/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data model for process stream events.
 * <p>
 * Mirrors Python's {@code StreamEvent} in {@code local/utils.py}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code type} — Event type (STDOUT/STDERR/EXIT/ERROR)</li>
 *   <li>{@code data} — Payload: text for stdout/stderr/error, exit code string for exit</li>
 *   <li>{@code timestamp} — UTC instant when the event was created</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {

    /** Type of the stream event. */
    private StreamEventType type;

    /**
     * Event payload data with type dependent on event type:
     * <ul>
     *   <li>stdout/stderr = text output {@code String}</li>
     *   <li>exit = integer exit code ({@code Integer})</li>
     *   <li>error = error message {@code String}</li>
     * </ul>
     * <p>
     * Mirrors Python's {@code data: Union[str, int]}.
     */
    private Object data;

    /** UTC timestamp when the event was created. */
    @Builder.Default
    private Instant timestamp = Instant.now();

    public static StreamEventBuilder builder() {
        return new StreamEventBuilder();
    }

    public StreamEventType getType() {
        return type;
    }

    public void setType(StreamEventType type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Get data as String.
     */
    public String getDataAsString() {
        return data != null ? data.toString() : null;
    }

    /**
     * Get data as Integer (for EXIT events).
     */
    public Integer getDataAsInt() {
        if (data instanceof Integer i) {
            return i;
        }
        if (data instanceof Number n) {
            return n.intValue();
        }
        if (data instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static final class StreamEventBuilder {
        private StreamEventType type;
        private Object data;
        private Instant timestamp = Instant.now();

        public StreamEventBuilder type(StreamEventType type) {
            this.type = type;
            return this;
        }

        public StreamEventBuilder data(Object data) {
            this.data = data;
            return this;
        }

        public StreamEventBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public StreamEvent build() {
            StreamEvent event = new StreamEvent();
            event.setType(type);
            event.setData(data);
            event.setTimestamp(timestamp);
            return event;
        }
    }
}
