/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Supported observability exporter backends.
 * <p>
 * Mirrors Python's literal exporter values in
 * {@code openjiuwen/agent_teams/observability/config.py}.
 */
public enum ObservabilityExporter {
    OTLP_GRPC("otlp_grpc"),
    OTLP_HTTP("otlp_http"),
    CONSOLE("console");

    private final String value;

    ObservabilityExporter(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ObservabilityExporter fromValue(String value) {
        if (value == null) {
            return OTLP_GRPC;
        }
        for (ObservabilityExporter exporter : values()) {
            if (exporter.value.equalsIgnoreCase(value)) {
                return exporter;
            }
        }
        return OTLP_GRPC;
    }
}
