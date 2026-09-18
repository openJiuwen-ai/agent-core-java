/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runtime knobs for the observability subsystem.
 * <p>
 * Mirrors Python's {@code ObservabilityConfig} in
 * {@code openjiuwen/agent_teams/observability/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ObservabilityConfig {

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    @JsonProperty("service_name")
    private String serviceName = "openjiuwen-agent-teams";

    @Builder.Default
    private ObservabilityExporter exporter = ObservabilityExporter.OTLP_GRPC;

    @Builder.Default
    private String endpoint = "http://localhost:4317";

    @Builder.Default
    @JsonProperty("sample_rate")
    private double sampleRate = 1.0;

    @Builder.Default
    @JsonProperty("redact_prompts")
    private boolean redactPrompts = false;

    @Builder.Default
    @JsonProperty("redact_completions")
    private boolean redactCompletions = false;

    @Builder.Default
    @JsonProperty("attribute_value_max_length")
    private int attributeValueMaxLength = 8192;

    @Builder.Default
    @JsonProperty("export_timeout_ms")
    private int exportTimeoutMs = 5000;

    @Builder.Default
    @JsonProperty("traces_dir")
    private String tracesDir = "./traces";

    @Builder.Default
    @JsonProperty("file_retention_days")
    private int fileRetentionDays = 7;

    public void setSampleRate(double sampleRate) {
        if (sampleRate < 0.0 || sampleRate > 1.0) {
            throw new IllegalArgumentException("sampleRate must be between 0.0 and 1.0");
        }
        this.sampleRate = sampleRate;
    }
}
