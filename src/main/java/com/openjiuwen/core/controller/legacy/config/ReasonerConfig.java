/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Legacy reasoner configuration using sub-module configuration.
 *
 * <p>Mirrors Python's {@code ReasonerConfig} in
 * {@code openjiuwen/core/controller/legacy/config/reasoner_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonerConfig {

    @Builder.Default
    @JsonProperty("intent_detection")
    private IntentDetectionConfig intentDetection = new IntentDetectionConfig();

    @Builder.Default
    private PlannerConfig planner = new PlannerConfig();

    @Builder.Default
    @JsonProperty("proactive_identifier")
    private ProactiveIdentifierConfig proactiveIdentifier = new ProactiveIdentifierConfig();

    @Builder.Default
    private ReflectorConfig reflector = new ReflectorConfig();

    @Builder.Default
    @JsonProperty("enable_metrics")
    private boolean enableMetrics = true;

    @Builder.Default
    @JsonProperty("enable_logging")
    private boolean enableLogging = true;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public void setIntentDetection(IntentDetectionConfig intentDetection) {
        this.intentDetection = intentDetection == null ? new IntentDetectionConfig() : intentDetection;
    }

    public void setPlanner(PlannerConfig planner) {
        this.planner = planner == null ? new PlannerConfig() : planner;
    }

    public void setProactiveIdentifier(ProactiveIdentifierConfig proactiveIdentifier) {
        this.proactiveIdentifier = proactiveIdentifier == null
                ? new ProactiveIdentifierConfig()
                : proactiveIdentifier;
    }

    public void setReflector(ReflectorConfig reflector) {
        this.reflector = reflector == null ? new ReflectorConfig() : reflector;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
