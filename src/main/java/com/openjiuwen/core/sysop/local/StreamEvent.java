/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
     * Event payload data:
     * stdout/stderr = text output string,
     * exit = integer exit code as string,
     * error = error message string.
     */
    private String data;

    /** UTC timestamp when the event was created. */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
