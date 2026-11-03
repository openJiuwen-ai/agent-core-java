/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for the agent_teams observability module.
 *
 * <p>Controls OTel tracer provider initialization, span export, and
 * redaction behavior. When {@code enabled} is {@code false},
 * {@link ObservabilitySetup#initObservability} is a no-op.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.config.ObservabilityConfig}.</p>
 *
 * @since 0.1.7
 */
@Data
@Builder
public class ObservabilityConfig {
    /** Master switch; when false, init is a no-op. Default: {@code true}. */
    @Builder.Default
    private boolean isEnabled = true;

    /** OTel resource {@code service.name}. Default: {@code "openjiuwen-agent-teams"}. */
    @Builder.Default
    private String serviceName = "openjiuwen-agent-teams";

    /**
     * Exporter type. Supported values: {@code "otlp_grpc"}, {@code "otlp_http"},
     * {@code "console"}, {@code "file"}. Default: {@code "otlp_grpc"}.
     */
    @Builder.Default
    private String exporter = "otlp_grpc";

    /** OTLP endpoint URL; ignored when exporter is {@code "file"}. Default: {@code "http://localhost:4317"}. */
    @Builder.Default
    private String endpoint = "http://localhost:4317";

    /** ParentBased ratio sampling rate (0.0–1.0). Default: {@code 1.0}. */
    @Builder.Default
    private double sampleRate = 1.0;

    /** When true, hash/truncate prompt attributes. Default: {@code false}. */
    @Builder.Default
    private boolean shouldRedactPrompts = false;

    /** When true, hash/truncate completion attributes. Default: {@code false}. */
    @Builder.Default
    private boolean shouldRedactCompletions = false;

    /** Maximum string attribute length in characters. Default: {@code 8192}. */
    @Builder.Default
    private int attributeValueMaxLength = 8192;

    /** Span exporter shutdown timeout in milliseconds. Default: {@code 5000}. */
    @Builder.Default
    private int exportTimeoutMs = 5000;

    /** Langfuse OTLP authentication public key. Default: empty string. */
    @Builder.Default
    private String langfusePublicKey = "";

    /** Langfuse OTLP authentication secret key. Default: empty string. */
    @Builder.Default
    private String langfuseSecretKey = "";

    /** Root directory for file exporter. Default: {@code "./traces"}. */
    @Builder.Default
    private String tracesDir = "./traces";

    /** File retention in days for file exporter. Default: {@code 7}. */
    @Builder.Default
    private int fileRetentionDays = 7;
}
